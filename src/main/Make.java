import static java.lang.ModuleLayer.defineModulesWithOneLoader;
import static java.lang.System.Logger.Level.DEBUG;
import static java.lang.System.Logger.Level.ERROR;
import static java.lang.System.Logger.Level.INFO;
import static java.lang.System.Logger.Level.WARNING;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.lang.module.ModuleFinder;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;
import java.util.spi.ToolProvider;
import java.util.stream.Collectors;

/** Modular project model and maker. */
class Make implements ToolProvider {

  /** Version is either {@code master} or {@link Runtime.Version#parse(String)}-compatible. */
  static final String VERSION = "master";

  /** Convenient short-cut to {@code "user.dir"} as a path. */
  static final Path USER_PATH = Path.of(System.getProperty("user.dir"));

  /** Main entry-point. */
  public static void main(String... args) {
    var make = Make.of(USER_PATH);
    var code = make.run(System.out, System.err, args);
    if (code != 0) {
      System.err.println(make.name() + " failed with error code: " + code);
      System.exit(code);
    }
  }

  /** Create instance using the given path as the home of project. */
  static Make of(Path home) {
    var configuration = Configuration.of(home);
    return new Make(configuration);
  }

  /** Immutable configuration. */
  final Configuration configuration;
  /** Main realm. */
  final Realm main;
  /** Test realm. */
  final Realm test;

  Make(Configuration configuration) {
    this.configuration = configuration;
    this.main = new Realm(configuration, "main");
    this.test = new Realm(configuration, "test", main);
  }

  @Override
  public String name() {
    return "Make.java";
  }

  @Override
  public int run(PrintWriter out, PrintWriter err, String... args) {
    var run = new Run(configuration.threshold, out, err);
    return run(run, List.of(args));
  }

  int run(Run run, List<String> args) {
    run.log(DEBUG, "__BEGIN__");
    run.log(INFO, "Making %s...", configuration.project);
    run.log(DEBUG, "%s %s", name(), VERSION);
    run.log(DEBUG, "  args = %s", args);
    run.log(DEBUG, "  user.path = %s", USER_PATH);
    run.log(DEBUG, "  configuration.home = %s", configuration.home);
    run.log(DEBUG, "  configuration.work = %s", configuration.work);
    run.log(DEBUG, "  configuration.threshold = %s", configuration.threshold);
    run.log(DEBUG, "  run.type = %s", run.getClass().getTypeName());
    if (configuration.dryRun) {
      run.log(INFO, "Dry-run ends here.");
      return 0;
    }
    try {
      assemble(run);
      build(run);
      summary(run, main);
      run.log(DEBUG, "__END.__");
      run.log(INFO, "Build successful after %d ms.", run.toDurationMillis());
      return 0;
    } catch (Throwable throwable) {
      run.log(ERROR, "Build failed: %s", throwable.getMessage());
      throwable.printStackTrace(run.err);
      return 1;
    }
  }

  private void assemble(Run run) throws Exception {
    run.log(DEBUG, "__ASSEMBLE__");
    assemble(run, main);
    assemble(run, test);
  }

  /** Assemble assets and check preconditions. */
  private void assemble(Run run, Realm realm) throws Exception {
    run.log(DEBUG, "Assembling assets for %s realm...", realm.name);
    var candidates =
        List.of(
            configuration.libraries.resolve(realm.name),
            configuration.libraries.resolve(realm.name + "-compile-only"),
            configuration.libraries.resolve(realm.name + "-runtime-only"),
            configuration.libraries.resolve(realm.name + "-runtime-platform"));
    var downloaded = new ArrayList<Path>();
    for (var candidate : candidates) {
      if (!Files.isDirectory(candidate)) {
        continue;
      }
      var path = candidate.resolve("module-uri.properties");
      if (Files.notExists(path)) {
        continue;
      }
      var directory = path.getParent();
      var downloader = new Downloader(directory, Boolean.getBoolean("offline"));
      var properties = new Properties();
      try (var stream = Files.newInputStream(path)) {
        properties.load(stream);
        run.log(DEBUG, "Resolving %d modules in %s", properties.size(), directory.toUri());
        for (var value : properties.values()) {
          var string = value.toString();
          var uri = URI.create(string);
          uri = uri.isAbsolute() ? uri : configuration.home.resolve(string).toUri();
          run.log(DEBUG, " o %s", uri);
          downloaded.add(downloader.transfer(uri));
        }
      }
    }
    run.log(DEBUG, "Downloaded %d modules.", downloaded.size());
    run.log(DEBUG, "Assembled assets for %s realm.", realm.name);
  }

  private void build(Run run) throws Exception {
    run.log(DEBUG, "__BUILD__");
    if (main.modules.size() + test.modules.size() == 0) {
      run.log(INFO, "No modules found. Trying to build using `--class-path`...");
      new ClassicalBuilder(run).build();
      return;
    }
    if (!main.modules.isEmpty()) {
      build(run, main, false);
      document(run, main);
    }
    if (!test.modules.isEmpty()) {
      build(run, test, true);
      if (configuration.doLaunchJUnitPlatform) {
        junit(run, test);
      }
    }
  }

  /** Build given modular realm. */
  private void build(Run run, Realm realm, boolean compileOnly) {
    run.log(DEBUG, "Modules in '%s' realm: %s", realm.name, realm.modules);
    var pendingModules = new ArrayList<>(realm.modules);
    var builders = List.of(new MultiReleaseBuilder(run, realm), new JigsawBuilder(run, realm));
    for (var builder : builders) {
      var completedModules = builder.build(pendingModules, compileOnly);
      pendingModules.removeAll(completedModules);
      if (pendingModules.isEmpty()) {
        return;
      }
    }
    throw new IllegalStateException("Pending module list is not empty! " + pendingModules);
  }

  /** Launch JUnit Platform for given modular realm. */
  @SuppressWarnings("UnusedAssignment")
  private void junit(Run run, Realm realm) {
    var junit =
        new Args()
            .add("--fail-if-no-tests")
            .add("--reports-dir", realm.target.resolve("junit-reports"))
            .add("--scan-modules");

    var modulePath = new ArrayList<Path>();
    modulePath.add(realm.compiledModules); // grab "exploded" test modules
    modulePath.addAll(realm.modulePath("runtime"));
    run.log(DEBUG, "Module path:");
    for (var element : modulePath) {
      run.log(DEBUG, "  -> %s", element);
    }
    var finder = ModuleFinder.of(modulePath.toArray(Path[]::new));
    run.log(DEBUG, "Finder finds module(s):");
    for (var reference : finder.findAll()) {
      run.log(DEBUG, "  -> %s", reference);
    }
    var rootModules = new ArrayList<>(realm.modules);
    rootModules.add("org.junit.platform.console");
    run.log(DEBUG, "Root module(s):");
    for (var module : rootModules) {
      run.log(DEBUG, "  -> %s", module);
    }
    var boot = ModuleLayer.boot();
    var configuration = boot.configuration().resolveAndBind(finder, ModuleFinder.of(), rootModules);
    var parentLoader = ClassLoader.getPlatformClassLoader();
    var controller = defineModulesWithOneLoader(configuration, List.of(boot), parentLoader);
    var junitConsoleLayer = controller.layer();
    controller.addExports( // "Make.java" resides in an unnamed module...
        junitConsoleLayer.findModule("org.junit.platform.console").orElseThrow(),
        "org.junit.platform.console",
        Make.class.getModule());
    var junitConsoleLoader = junitConsoleLayer.findLoader("org.junit.platform.console");
    launchJUnitPlatformConsole(run, junitConsoleLoader, junit);
    if (System.getProperty("os.name", "?").toLowerCase(Locale.ENGLISH).contains("win")) {
      try {
        controller = null;
        junitConsoleLayer = null;
        junitConsoleLoader = null;
        System.gc();
        Thread.sleep(1234);
      } catch (InterruptedException e) {
        // ignore
      }
    }
  }

  private void launchJUnitPlatformConsole(Run run, ClassLoader loader, Args junit) {
    run.log(DEBUG, "__CHECK__");
    run.log(DEBUG, "Launching JUnit Platform Console: %s", junit.list);
    run.log(DEBUG, "Using class loader: %s - %s", loader.getName(), loader);
    var currentThread = Thread.currentThread();
    var currentContextLoader = currentThread.getContextClassLoader();
    currentThread.setContextClassLoader(loader);
    try {
      var launcher = loader.loadClass("org.junit.platform.console.ConsoleLauncher");
      var params = new Class<?>[] {PrintStream.class, PrintStream.class, String[].class};
      var execute = launcher.getMethod("execute", params);
      var out = new ByteArrayOutputStream();
      var err = new ByteArrayOutputStream();
      var args = junit.toStringArray();
      var result = execute.invoke(null, new PrintStream(out), new PrintStream(err), args);
      run.out.write(out.toString());
      run.err.write(err.toString());
      var code = (int) result.getClass().getMethod("getExitCode").invoke(result);
      if (code != 0) {
        throw new AssertionError("JUnit run exited with code " + code);
      }
    } catch (Throwable t) {
      throw new Error("ConsoleLauncher.execute(...) failed: " + t, t);
    } finally {
      currentThread.setContextClassLoader(currentContextLoader);
    }
  }

  /** Generate documentation for given modular realm. */
  private void document(Run run, Realm realm) throws Exception {
    // javadoc: error - Destination directory not writable: ${work}/main/compiled/javadoc
    // var destination = Files.createDirectories(realm.compiledJavadoc);
    // https://github.com/sormuras/make-java/issues/8
    var moduleSourcePath = realm.source.toString();
    var javaSources = new ArrayList<String>();
    javaSources.add(moduleSourcePath);
    for (var release = 7; release <= Runtime.version().feature(); release++) {
      var separator = File.separator;
      javaSources.add(moduleSourcePath + separator + "*" + separator + "java-" + release);
    }
    var javadoc =
        new Args()
            .add(false, "-verbose")
            .add("-encoding", "UTF-8")
            .add("-quiet")
            .add("-windowtitle", configuration.project)
            .add("-d", realm.compiledJavadoc)
            .add("--module-source-path", String.join(File.pathSeparator, javaSources))
            .add("--module", String.join(",", realm.modules));
    var modulePath = realm.modulePath("compile");
    if (!modulePath.isEmpty()) {
      javadoc.add("--module-path", modulePath);
    }
    run.tool("javadoc", javadoc.toStringArray());
    Files.createDirectories(realm.packagedJavadoc);
    var javadocJar =
        realm.packagedJavadoc.resolve(configuration.project.jarBaseName + "-javadoc.jar");
    var jar =
        new Args()
            .add(configuration.debug, "--verbose")
            .add("--create")
            .add("--file", javadocJar)
            .add("-C", realm.compiledJavadoc)
            .add(".");
    run.tool("jar", jar.toStringArray());
  }

  /** Log summary for given realm. */
  private void summary(Run run, Realm realm) {
    run.log(INFO, "__SUMMARY__");
    var jars = Util.find(realm.target, "*.jar");
    jars.forEach(jar -> run.log(INFO, "  -> %,9d %s", Util.size(jar), jar));
    if (configuration.debug) {
      var jdeps = new Make.Args().add("-summary");
      if (realm.modules.isEmpty()) {
        // var classPath = new ArrayList<Path>(); // realm "runtime"
        jdeps.add(realm.classicalJar);
      } else {
        var modulePath = new ArrayList<Path>();
        modulePath.add(realm.packagedModules);
        modulePath.addAll(realm.modulePath("runtime"));
        jdeps
            .add("--module-path", modulePath)
            .add("--add-modules", String.join(",", realm.modules))
            .add("--multi-release", "base");
      }
      run.tool("jdeps", jdeps.toStringArray());
    }
  }

  /** Global project constants. */
  static class Project {
    final String name;
    final String version;
    final String jarBaseName;

    Project(String name, String version) {
      this.name = name;
      this.version = version;
      this.jarBaseName = name.toLowerCase().replace('.', '-') + '-' + version;
    }

    @Override
    public String toString() {
      return name + ' ' + version;
    }
  }

  /** Immutable configuration. */
  static class Configuration {

    /** Named property value getter. */
    static class Configurator {
      final Properties properties;

      Configurator(Properties properties) {
        this.properties = properties;
      }

      String get(String key, String defaultValue) {
        return System.getProperty(key, properties.getProperty(key, defaultValue));
      }

      boolean is(String key, String defaultValue) {
        var value = System.getProperty(key.substring(1), properties.getProperty(key, defaultValue));
        return "".equals(value) || "true".equals(value);
      }

      private System.Logger.Level threshold() {
        if (is("debug", "false")) {
          return System.Logger.Level.ALL;
        }
        var level = get("threshold", "INFO").toUpperCase();
        return System.Logger.Level.valueOf(level);
      }
    }

    static Configuration of(Path home) {
      return new Configuration(home, newProperties(home));
    }

    static Properties newProperties(Path home) {
      var properties = new Properties();
      var path = home.resolve(home.getFileName() + ".properties");
      if (Files.exists(path)) {
        try (var stream = Files.newBufferedReader(path)) {
          properties.load(stream);
        } catch (Exception e) {
          throw new Error("Loading properties failed: " + path, e);
        }
      }
      return properties;
    }

    final boolean debug;
    final boolean dryRun;
    final boolean doLaunchJUnitPlatform;
    final Path home;
    final Path work;
    final Path libraries;
    final Project project;
    final System.Logger.Level threshold;

    Configuration(Path home, Properties properties) {
      this.home = USER_PATH.getRoot().equals(home.getRoot()) ? USER_PATH.relativize(home) : home;
      this.libraries = this.home.resolve("lib");

      var configurator = new Configurator(properties);
      this.debug = configurator.is("debug", "false");
      this.dryRun = configurator.is("dry-run", "false");
      this.doLaunchJUnitPlatform = configurator.is("do-launch-junit-platform", "true");
      var work = Path.of(configurator.get("work", "target"));
      this.work = work.isAbsolute() ? work : this.home.resolve(work);
      this.project =
          new Project(
              configurator.get("name", home.getFileName().toString()),
              configurator.get("version", "1.0.0-SNAPSHOT"));
      this.threshold = configurator.threshold();
    }
  }

  /** Runtime context information. */
  static class Run {
    /** Current logging level threshold. */
    final System.Logger.Level threshold;
    /** Stream to which normal and expected output should be written. */
    final PrintWriter out;
    /** Stream to which any error messages should be written. */
    final PrintWriter err;
    /** Time instant recorded on creation of this instance. */
    final Instant start;

    Run(System.Logger.Level threshold, PrintWriter out, PrintWriter err) {
      this.start = Instant.now();
      this.threshold = threshold;
      this.out = out;
      this.err = err;
    }

    /** Log message unless threshold suppresses it. */
    void log(System.Logger.Level level, String format, Object... args) {
      if (level.getSeverity() < threshold.getSeverity()) {
        return;
      }
      var consumer = level.getSeverity() < WARNING.getSeverity() ? out : err;
      var message = String.format(format, args);
      consumer.println(message);
      consumer.flush();
    }

    /** Run provided tool. */
    void tool(String name, String... args) {
      log(DEBUG, "Running tool '%s' with: %s", name, List.of(args));
      var tool = ToolProvider.findFirst(name).orElseThrow();
      if (Boolean.getBoolean("dump-tool-command".substring(1))) {
        Util.listCommand(name, args).forEach(line -> log(INFO, "%s", line));
        log(INFO, "");
      }
      var code = tool.run(out, err, args);
      if (code == 0) {
        log(DEBUG, "Tool '%s' successfully run.", name);
        return;
      }
      throw new Error("Tool '" + name + "' run failed with error code: " + code);
    }

    long toDurationMillis() {
      return TimeUnit.MILLISECONDS.convert(Duration.between(start, Instant.now()));
    }
  }

  /** Command-line program argument list builder. */
  static class Args {

    final ArrayList<String> list = new ArrayList<>();

    /** Add single argument by invoking {@link Object#toString()} on the given argument. */
    Args add(Object argument) {
      list.add(argument.toString());
      return this;
    }

    /** Add a single argument iff the conditions is {@code true}. */
    Args add(boolean condition, Object argument) {
      return condition ? add(argument) : this;
    }

    /** Add two arguments by invoking {@link #add(Object)} for the key and value elements. */
    Args add(Object key, Object value) {
      return add(key).add(value);
    }

    /** Add two arguments, i.e. the key and the paths joined by system's path separator. */
    Args add(Object key, Collection<Path> paths) {
      return add(key, paths, UnaryOperator.identity());
    }

    /** Add two arguments, i.e. the key and the paths joined by system's path separator. */
    Args add(Object key, Collection<Path> paths, UnaryOperator<String> operator) {
      var stream = paths.stream() /*.filter(Files::isDirectory)*/.map(Object::toString);
      return add(key, operator.apply(stream.collect(Collectors.joining(File.pathSeparator))));
    }

    /** Add all arguments by invoking {@link #add(Object)} for each element. */
    Args addEach(Iterable<?> arguments) {
      arguments.forEach(this::add);
      return this;
    }

    /** Returns an array of {@link String} containing all of the collected arguments. */
    String[] toStringArray() {
      return list.toArray(String[]::new);
    }
  }

  /** Modular source set. */
  static class Realm {
    final Configuration configuration;
    final String name;
    final List<Realm> requiredRealms;

    final Path source;
    final Path target;
    final List<String> modules;

    final Path classicalClasses;
    final Path classicalJar;
    final Path classicalJarSources;
    final Path classicalReports;

    final Path compiledMulti;
    final Path compiledModules;
    final Path compiledJavadoc;
    final Path packagedModules;
    final Path packagedSources;
    final Path packagedJavadoc;

    Realm(Configuration configuration, String name, Realm... requiredRealms) {
      this.configuration = configuration;
      this.name = name;
      this.requiredRealms = List.of(requiredRealms);

      this.source = configuration.home.resolve("src").resolve(name);
      this.target = configuration.work.resolve(name);
      this.modules =
          Util.find(source, "module-info.java").size() > 0
              ? Util.listDirectoryNames(source)
              : List.of();

      this.classicalClasses = target.resolve("classes");
      this.classicalJar = target.resolve(configuration.project.jarBaseName + ".jar");
      this.classicalJarSources = target.resolve(configuration.project.jarBaseName + "-sources.jar");
      this.classicalReports = target.resolve("reports");

      var compiledBase = target.resolve("compiled");
      this.compiledJavadoc = compiledBase.resolve("javadoc");
      this.compiledModules = compiledBase.resolve("modules");
      this.compiledMulti = compiledBase.resolve("multi-release");
      this.packagedJavadoc = target.resolve("javadoc");
      this.packagedModules = target.resolve("modules");
      this.packagedSources = target.resolve("sources");
    }

    /** Create module path. */
    List<Path> modulePath(String phase) {
      var result = new ArrayList<Path>();
      var candidates = List.of(name, name + "-" + phase + "-only");
      for (var candidate : candidates) {
        result.add(configuration.libraries.resolve(candidate));
      }
      for (var required : requiredRealms) {
        result.add(required.packagedModules);
        result.addAll(required.modulePath(phase));
      }
      result.removeIf(Files::notExists);
      return result;
    }
  }

  /** Common builder base. */
  abstract class Builder {
    final Run run;

    Builder(Run run) {
      this.run = run;
    }

    /** Build given modules and return list of modules actually built. */
    List<String> build(List<String> modules, boolean compileOnly) {
      return List.of();
    }

    Args newJavacArgs(Path destination) {
      return new Args()
          .add(false, "-verbose") // that's really(!) verbose...
          .add("-encoding", "UTF-8")
          .add("-Xlint")
          .add("-d", destination);
    }

    Args newJarArgs(Path file) {
      return new Args().add(configuration.debug, "--verbose").add("--create").add("--file", file);
    }
  }

  /** Classpath-based builder. */
  class ClassicalBuilder extends Builder {
    ClassicalBuilder(Run run) {
      super(run);
    }

    void build() {
      if (Files.exists(main.source)) {
        compile(main);
        jarClasses(main);
        jarSources(main);
        // document(main); // https://github.com/sormuras/make-java/issues/8
      }
      if (Files.exists(test.source)) {
        compile(test);
        if (configuration.doLaunchJUnitPlatform) {
          junit();
        }
      }
    }

    private void compile(Realm realm) {
      var units = Util.find(realm.source, "*.java");
      if (units.isEmpty()) {
        throw new IllegalStateException("No source files found in: " + realm.source);
      }
      var classPath = new ArrayList<Path>();
      if (realm.name.equals("test")) {
        classPath.add(main.classicalJar);
      }
      var libraries = configuration.libraries;
      classPath.addAll(Util.find(libraries.resolve(realm.name), "*.jar"));
      classPath.addAll(Util.find(libraries.resolve(realm.name + "-compile-only"), "*.jar"));
      var javac = newJavacArgs(realm.classicalClasses);
      if (!classPath.isEmpty()) {
        javac.add("--class-path", classPath);
      }
      run.tool("javac", javac.addEach(units).toStringArray());
    }

    private void jarClasses(Realm realm) {
      var jar = newJarArgs(realm.classicalJar).add("-C", realm.classicalClasses).add(".");
      run.tool("jar", jar.toStringArray());
    }

    private void jarSources(Realm realm) {
      var jar = newJarArgs(realm.classicalJarSources).add("-C", realm.source).add(".");
      run.tool("jar", jar.toStringArray());
    }

    private void junit() {
      var testClasses = Util.find(test.classicalClasses, "*.class");
      if (testClasses.isEmpty()) {
        throw new AssertionError("No test class found in: " + test.classicalClasses);
      }
      var junit = new Args().add("--fail-if-no-tests").add("--reports-dir", test.classicalReports);
      testClasses.forEach(
          path -> {
            var string = test.classicalClasses.relativize(path).toString();
            var name = string.replace(File.separatorChar, '.').substring(0, string.length() - 6);
            junit.add("--select-class", name);
          });
      var loader = newJUnitPlatformClassLoader();
      try {
        launchJUnitPlatformConsole(run, loader, junit);
      } finally {
        while (loader instanceof URLClassLoader) {
          try {
            ((URLClassLoader) loader).close();
          } catch (Exception e) {
            // ignore
          }
          loader = loader.getParent();
        }
      }
    }

    private ClassLoader newJUnitPlatformClassLoader() {
      var libraries = configuration.libraries;

      var mainPaths = new ArrayList<Path>();
      mainPaths.add(main.classicalJar);
      mainPaths.addAll(Util.find(libraries.resolve("main"), "*.jar"));
      mainPaths.addAll(Util.find(libraries.resolve("main-runtime-only"), "*.jar"));
      mainPaths.removeIf(path -> Files.notExists(path));
      run.log(DEBUG, "mainPaths: %s", mainPaths);

      var testPaths = new ArrayList<Path>();
      testPaths.add(test.classicalClasses);
      testPaths.addAll(Util.find(libraries.resolve("test"), "*.jar"));
      testPaths.addAll(Util.find(libraries.resolve("test-runtime-only"), "*.jar"));
      testPaths.removeIf(path -> Files.notExists(path));
      run.log(DEBUG, "testPaths: %s", testPaths);

      var platformPaths = Util.find(libraries.resolve("test-runtime-platform"), "*.jar");
      run.log(DEBUG, "platformPaths: %s", platformPaths);

      var parent = ClassLoader.getPlatformClassLoader();
      var mainLoader = new URLClassLoader("junit-main", Util.urls(mainPaths), parent);
      var testLoader = new URLClassLoader("junit-test", Util.urls(testPaths), mainLoader);
      return new URLClassLoader("junit-platform", Util.urls(platformPaths), testLoader);
    }
  }

  /** Build modules using default jigsaw directory layout. */
  class JigsawBuilder extends Builder {
    final Realm realm;

    JigsawBuilder(Run run, Realm realm) {
      super(run);
      this.realm = realm;
    }

    @Override
    List<String> build(List<String> modules, boolean compileOnly) {
      run.log(DEBUG, "Building %d Jigsaw module(s): %s", modules.size(), modules);
      compile(modules);
      if (compileOnly) {
        return modules;
      }
      try {
        for (var module : modules) {
          jarModule(module);
          jarSources(module);
          // Create javadoc and "-javadoc.jar" for this module
          // https://github.com/sormuras/make-java/issues/8
        }
      } catch (Exception e) {
        throw new Error("Building modules failed!", e);
      }
      return modules;
    }

    private void compile(List<String> modules) {
      var javac =
          newJavacArgs(realm.compiledModules)
              .add("--module-version", configuration.project.version)
              .add("--module-source-path", realm.source)
              .add("--module", String.join(",", modules));

      var modulePath = new ArrayList<Path>();
      if (realm.name.equals("test")) {
        modulePath.add(main.packagedModules);
        modulePath.add(configuration.libraries.resolve("main"));
      }
      modulePath.add(realm.packagedModules); // modules from previous builders
      modulePath.add(configuration.libraries.resolve(realm.name));
      modulePath.add(configuration.libraries.resolve(realm.name + "-compile-only"));
      modulePath.removeIf(path -> Files.notExists(path));
      if (!modulePath.isEmpty()) {
        javac.add("--module-path", modulePath);
      }
      if (realm.name.equals("test")) {
        var map = Util.findPatchMap(Set.of(realm.source), Set.of(main.source));
        for (var entry : map.entrySet()) {
          var module = entry.getKey();
          var patches = entry.getValue();
          javac.add("--patch-module", patches, paths -> module + "=" + paths);
        }
      }

      run.tool("javac", javac.toStringArray());
    }

    private void jarModule(String module) throws Exception {
      var compiledModules = realm.target.resolve("compiled/modules");
      var modularJar =
          realm.packagedModules.resolve(module + '-' + configuration.project.version + ".jar");
      var jar = newJarArgs(modularJar).add("-C", compiledModules.resolve(module)).add(".");
      Files.createDirectories(realm.packagedModules);
      run.tool("jar", jar.toStringArray());
    }

    private void jarSources(String module) throws Exception {
      var sourcesJar =
          realm.packagedSources.resolve(
              module + '-' + configuration.project.version + "-sources.jar");
      var jar = newJarArgs(sourcesJar).add("-C", realm.source.resolve(module)).add(".");
      Files.createDirectories(realm.packagedSources);
      run.tool("jar", jar.toStringArray());
    }
  }

  /** Build multi-release modules. */
  class MultiReleaseBuilder extends Builder {

    final Realm realm;
    final Pattern javaReleasePattern = Pattern.compile("java-\\d+");

    MultiReleaseBuilder(Run run, Realm realm) {
      super(run);
      this.realm = realm;
    }

    @Override
    List<String> build(List<String> modules, boolean compileOnly) {
      var result = new ArrayList<String>();
      for (var module : modules) {
        if (build(module, compileOnly)) {
          result.add(module);
        }
      }
      return result;
    }

    private boolean build(String module, boolean compileOnly) {
      var names = Util.listDirectoryNames(realm.source.resolve(module));
      if (names.isEmpty()) {
        return false; // empty source path or just a sole "module-info.java" file...
      }
      if (!names.stream().allMatch(javaReleasePattern.asMatchPredicate())) {
        return false;
      }
      run.log(DEBUG, "Building multi-release module: %s", module);
      int base = Util.findBaseJavaFeatureNumber(names);
      run.log(DEBUG, "Base feature number is: %d", base);
      for (var release = base; release <= Runtime.version().feature(); release++) {
        compile(module, base, release);
      }
      if (compileOnly) {
        return true;
      }
      try {
        jarModule(module, base);
        jarSources(module, base);
        // Create "-javadoc.jar" for this multi-release module
        // https://github.com/sormuras/make-java/issues/8
      } catch (Exception e) {
        throw new Error("Building module " + module + " failed!", e);
      }
      return true;
    }

    private void compile(String module, int base, int release) {
      var javaR = "java-" + release;
      var source = realm.source.resolve(module).resolve(javaR);
      if (Files.notExists(source)) {
        run.log(DEBUG, "Skipping %s, no source path exists: %s", javaR, source);
        return;
      }
      var destination = realm.compiledMulti.resolve(javaR);
      var javac =
          new Args()
              .add(false, "-verbose")
              .add("-encoding", "UTF-8")
              .add("-Xlint")
              .add("--release", release);
      if (release < 9) {
        javac.add("-d", destination.resolve(module));
        javac.add("--class-path", Util.find(configuration.libraries.resolve(realm.name), "*.jar"));
        javac.addEach(Util.find(source, "*.java"));
      } else {
        javac.add("-d", destination);
        javac.add("--module-version", configuration.project.version);
        javac.add("--module-path", realm.modulePath("compile"));
        var pathR = realm.source + File.separator + "*" + File.separator + javaR;
        var sources = List.of(pathR, "" + realm.source);
        javac.add("--module-source-path", String.join(File.pathSeparator, sources));
        javac.add(
            "--patch-module",
            module + '=' + realm.compiledMulti.resolve("java-" + base).resolve(module));
        javac.add("--module", module);
      }
      run.tool("javac", javac.toStringArray());
    }

    private void jarModule(String module, int base) throws Exception {
      Files.createDirectories(realm.packagedModules);
      var file =
          realm.packagedModules.resolve(module + '-' + configuration.project.version + ".jar");
      var source = realm.compiledMulti;
      var javaBase = source.resolve("java-" + base).resolve(module);
      var jar =
          new Args()
              .add(configuration.debug, "--verbose")
              .add("--create")
              .add("--file", file)
              // "base" classes
              .add("-C", javaBase)
              .add(".");
      // "base" + 1 .. N files
      for (var release = base + 1; release <= Runtime.version().feature(); release++) {
        var javaRelease = source.resolve("java-" + release).resolve(module);
        if (Files.notExists(javaRelease)) {
          continue;
        }
        jar.add("--release", release);
        jar.add("-C", javaRelease);
        jar.add(".");
      }
      run.tool("jar", jar.toStringArray());
    }

    private void jarSources(String module, int base) throws Exception {
      Files.createDirectories(realm.packagedSources);
      var file =
          realm.packagedSources.resolve(
              module + '-' + configuration.project.version + "-sources.jar");
      var source = realm.source.resolve(module);
      var javaBase = source.resolve("java-" + base);
      var jar =
          new Args()
              .add(configuration.debug, "--verbose")
              .add("--create")
              .add("--file", file)
              // "base" classes
              .add("-C", javaBase)
              .add(".");
      // "base" + 1 .. N files
      for (var release = base + 1; release <= Runtime.version().feature(); release++) {
        var javaRelease = source.resolve("java-" + release);
        if (Files.notExists(javaRelease)) {
          continue;
        }
        jar.add("--release", release);
        jar.add("-C", javaRelease);
        jar.add(".");
      }
      run.tool("jar", jar.toStringArray());
    }
  }

  /** Downloader. */
  static class Downloader {

    /** Extract last path element from the supplied uri. */
    static String extractFileName(URI uri) {
      var path = uri.getPath(); // strip query and fragment elements
      return path.substring(path.lastIndexOf('/') + 1);
    }

    final Path folder;
    final boolean offline;

    Downloader(Path folder, boolean offline) {
      this.folder = folder;
      this.offline = offline;
    }

    Path transfer(URI uri) throws Exception {
      var fileName = extractFileName(uri);
      var target = Files.createDirectories(folder).resolve(fileName);
      var url = uri.toURL(); // fails for non-absolute uri
      if (offline) {
        if (Files.exists(target)) {
          return target;
        }
        throw new IllegalStateException("Target is missing and being offline: " + target);
      }
      var connection = url.openConnection();
      try (var sourceStream = connection.getInputStream()) {
        var millis = connection.getLastModified();
        var lastModified = FileTime.fromMillis(millis == 0 ? System.currentTimeMillis() : millis);
        if (Files.exists(target)) {
          var fileModified = Files.getLastModifiedTime(target);
          if (fileModified.equals(lastModified)) {
            return target;
          }
        }
        try (var targetStream = Files.newOutputStream(target)) {
          sourceStream.transferTo(targetStream);
        }
        var contentDisposition = connection.getHeaderField("Content-Disposition");
        if (contentDisposition != null && contentDisposition.indexOf('=') > 0) {
          var contentName = contentDisposition.split("=")[1].replaceAll("\"", "");
          var newTarget = target.resolveSibling(contentName);
          Files.move(target, newTarget);
          target = newTarget;
        }
        Files.setLastModifiedTime(target, lastModified);
      }
      return target;
    }
  }

  /** Static helpers. */
  static class Util {
    /** No instance permitted. */
    private Util() {
      throw new Error();
    }

    static List<String> listCommand(String command, String... args) {
      if (args.length == 0) {
        return List.of(command);
      }
      if (args.length == 1) {
        return List.of(command + ' ' + args[0]);
      }
      var list = new ArrayList<String>();
      list.add(String.format("%s with %d arguments", command, args.length));
      boolean simple = true;
      for (var arg : args) {
        var indent = simple ? 8 : arg.startsWith("-") ? 8 : 10;
        simple = !arg.startsWith("-");
        if (arg.length() > 50) {
          if (arg.contains(File.pathSeparator)) {
            for (String path : arg.split(File.pathSeparator)) {
              list.add(String.format("%-10s%s", "", path));
            }
            continue;
          }
          arg = arg.substring(0, 46) + "[...]";
        }
        list.add(String.format("%-" + indent + "s%s", "", arg));
      }
      return list;
    }

    /** Return list of child directories directly present in {@code root} path. */
    static List<Path> listDirectories(Path root) {
      return listDirectories(root, 1, false);
    }

    /** Return list of directories starting with {@code root} path. */
    static List<Path> listDirectories(Path root, int maxDepth, boolean includeRoot) {
      try (var paths = Files.find(root, maxDepth, (path, attr) -> Files.isDirectory(path))) {
        var stream = includeRoot ? paths : paths.filter(path -> !root.equals(path));
        return stream.sorted().collect(Collectors.toList());
      } catch (Exception e) {
        throw new Error("listDirectories failed for root: " + root, e);
      }
    }

    /** Return sorted list of child directory names directly present in {@code root} path. */
    static List<String> listDirectoryNames(Path root) {
      return listDirectories(root).stream()
          .map(root::relativize)
          .map(Path::toString)
          .sorted()
          .collect(Collectors.toList());
    }

    /** Find paths specified by a glob pattern. */
    static Set<Path> find(Path root, String glob) {
      if (Files.notExists(root)) {
        return Set.of();
      }
      var paths = new TreeSet<Path>();
      for (var directory : listDirectories(root, Integer.MAX_VALUE, true)) {
        try (var stream = Files.newDirectoryStream(directory, glob)) {
          stream.forEach(paths::add);
        } catch (Exception e) {
          throw new Error("find failed for directory: " + directory, e);
        }
      }
      return paths;
    }

    /** Find lowest Java feature number. */
    static int findBaseJavaFeatureNumber(List<String> strings) {
      int base = Integer.MAX_VALUE;
      for (var string : strings) {
        var candidate = Integer.valueOf(string.substring("java-".length()));
        if (candidate < base) {
          base = candidate;
        }
      }
      if (base == Integer.MAX_VALUE) {
        throw new IllegalArgumentException("No base Java feature number found: " + strings);
      }
      return base;
    }

    /** Return patch map using two collections of paths. */
    static Map<String, Set<Path>> findPatchMap(Collection<Path> bases, Collection<Path> patches) {
      var map = new TreeMap<String, Set<Path>>();
      for (var base : bases) {
        for (var name : listDirectoryNames(base)) {
          for (var patch : patches) {
            var candidate = patch.resolve(name);
            if (Files.isDirectory(candidate)) {
              map.computeIfAbsent(name, __ -> new TreeSet<>()).add(candidate);
            }
          }
        }
      }
      return map;
    }

    /** Get file size. */
    static long size(Path path) {
      try {
        return Files.size(path);
      } catch (Exception e) {
        throw new Error("size failed for path: " + path, e);
      }
    }

    /** Convert a collection of paths to an array of urls. */
    static URL[] urls(Collection<Path> paths) {
      var urls = new URL[paths.size()];
      var index = 0;
      try {
        for (var path : paths) {
          urls[index++] = path.toUri().toURL();
        }
      } catch (Exception e) {
        throw new Error("urls failed for paths: " + paths, e);
      }
      return urls;
    }
  }
}
