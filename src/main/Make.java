import static java.lang.System.Logger.Level.DEBUG;
import static java.lang.System.Logger.Level.ERROR;
import static java.lang.System.Logger.Level.INFO;
import static java.lang.System.Logger.Level.WARNING;

import java.io.File;
import java.io.PrintWriter;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
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
    var code = Make.of(USER_PATH).run(System.out, System.err, args);
    if (code != 0) {
      throw new Error("Make.java failed with error code: " + code);
    }
  }

  /** Create instance using the given path as the home of project. */
  static Make of(Path home) {
    var debug = Boolean.getBoolean("ebug");
    var dryRun = Boolean.getBoolean("ry-run");
    var project = System.getProperty("project.name", home.getFileName().toString());
    var version = System.getProperty("project.version", "1.0.0-SNAPSHOT");
    var main = Realm.of("main", home, home.resolve("work"));
    return new Make(debug, dryRun, project, version, home, List.of(main));
  }

  /** Debug flag. */
  final boolean debug;
  /** Dry-run flag. */
  final boolean dryRun;
  /** Name of the project. */
  final String project;
  /** Version of the project. */
  final String version;
  /** Root path of this project. */
  final Path home;
  /** Realms of this project. */
  final List<Realm> realms;

  Make(
      boolean debug,
      boolean dryRun,
      String project,
      String version,
      Path home,
      List<Realm> realms) {
    this.debug = debug;
    this.dryRun = dryRun;
    this.project = project;
    this.version = version;
    this.home = home;
    this.realms = realms;
  }

  @Override
  public String name() {
    return "Make.java";
  }

  @Override
  public int run(PrintWriter out, PrintWriter err, String... args) {
    var run = new Run(debug ? System.Logger.Level.ALL : System.Logger.Level.INFO, out, err);
    return run(run, args);
  }

  int run(Run run, String... args) {
    run.log(INFO, "%s - %s", name(), VERSION);
    run.log(DEBUG, "  args = %s", List.of(args));
    run.log(DEBUG, "  java = %s", Runtime.version());
    run.log(INFO, "Building project '%s', version %s...", project, version);
    run.log(DEBUG, "  home = %s", home.toUri());
    for (int i = 0; i < realms.size(); i++) {
      run.log(DEBUG, "  realms[%d] = %s", i, realms.get(i));
    }
    if (dryRun) {
      run.log(INFO, "Dry-run ends here.");
      return 0;
    }
    try {
      for (var realm : realms) {
        build(run, realm);
      }
      var main = realms.get(0);
      var jars = Util.listFiles(List.of(main.packagedModules), Util::isJarFile);
      jars.forEach(jar -> run.log(INFO, "  -> " + jar.getFileName()));
      if (debug) {
        var modulePath = new ArrayList<Path>();
        modulePath.add(main.packagedModules);
        modulePath.addAll(main.modulePath);
        var addModules = String.join(",", Util.listDirectoryNames(home.resolve(main.source)));
        var jdeps =
            new Make.Args()
                .with("--module-path", modulePath)
                .with("--add-modules", addModules)
                .with("--multi-release", "base")
                .with("-summary");
        run.tool("jdeps", jdeps.toStringArray());
      }
      // Launch JUnit Platform
      for (var realm : realms) {
        if (realm.containsTests()) {
          junit(run, realm);
        }
      }
      run.log(INFO, "Build successful after %d ms.", run.toDurationMillis());
      return 0;
    } catch (Throwable t) {
      run.log(ERROR, "Build failed: %s", t.getMessage());
      t.printStackTrace(run.err);
      return 1;
    }
  }

  private void build(Run run, Realm realm) {
    var moduleSourcePath = home.resolve(realm.source);
    if (Files.notExists(moduleSourcePath)) {
      run.log(WARNING, "Source path of %s realm not found: %s", realm.name, moduleSourcePath);
      return;
    }
    var modules = Util.listDirectoryNames(moduleSourcePath);
    if (modules.isEmpty()) {
      throw new Error("No module directories found in source path: " + moduleSourcePath);
    }
    // multi-release modules
    var regularModules = new ArrayList<>(modules);
    var regularModulesIterator = regularModules.listIterator();
    while (regularModulesIterator.hasNext()) {
      var module = regularModulesIterator.next();
      if (Files.notExists(moduleSourcePath.resolve(module).resolve("module-info.java"))) {
        run.log(DEBUG, "Building multi-release module: %s", module);
        var builder = new MultiReleaseBuilder(run, realm);
        builder.build(module);
        regularModulesIterator.remove();
      }
    }
    var moduleBuilder = new ModuleBuilder(run, realm);
    moduleBuilder.build(regularModules);
  }

  private void junit(Run run, Realm realm) throws Exception {
    if (!realm.containsTests()) {
      run.log(WARNING, "Realm %s is not configured to contain tests...", realm.name);
      return;
    }
    var modulePath = new ArrayList<Path>();
    modulePath.add(realm.compiledModules);
    modulePath.addAll(realm.modulePath);
    modulePath.add(Path.of("lib", realm.name + "-runtime-only"));
    var addModules = String.join(",", Util.listDirectoryNames(home.resolve(realm.source)));
    var java =
        new Args()
            .with("--show-version")
            .with("--module-path", modulePath)
            .with("--add-modules", addModules);
    var args =
        new Args()
            .with("--fail-if-no-tests")
            .with("--reports-dir", realm.target.resolve("junit-reports"))
            .with("--scan-modules");
    run.junit(java, args.toStringArray());
  }

  /** Command-line program argument list builder. */
  static class Args extends ArrayList<String> {
    /** Add single argument by invoking {@link Object#toString()} on the given argument. */
    Args with(Object argument) {
      add(argument.toString());
      return this;
    }

    Args with(boolean condition, Object argument) {
      return condition ? with(argument) : this;
    }

    /** Add two arguments by invoking {@link #with(Object)} for the key and value elements. */
    Args with(Object key, Object value) {
      return with(key).with(value);
    }

    /** Add two arguments, i.e. the key and the paths joined by system's path separator. */
    Args with(Object key, List<Path> paths) {
      var value =
          paths.stream()
              // .filter(Files::isDirectory)
              .map(Object::toString)
              .collect(Collectors.joining(File.pathSeparator));
      return with(key, value);
    }

    /** Add all arguments by invoking {@link #with(Object)} for each element. */
    Args withEach(Iterable<?> arguments) {
      arguments.forEach(this::with);
      return this;
    }

    String[] toStringArray() {
      return toArray(String[]::new);
    }
  }

  /** Runtime context information. */
  static class Run {
    /** Current logging level threshold. */
    final System.Logger.Level threshold;
    /** Stream to which "expected" output should be written. */
    final PrintWriter out;
    /** Stream to which any error messages should be written. */
    final PrintWriter err;
    /** Time instant recorded on creation of this instance. */
    final Instant start;

    Run(System.Logger.Level threshold, PrintWriter out, PrintWriter err) {
      this.threshold = threshold;
      this.out = out;
      this.err = err;
      this.start = Instant.now();
    }

    /** Log message unless threshold suppresses it. */
    void log(System.Logger.Level level, String format, Object... args) {
      if (level.getSeverity() < threshold.getSeverity()) {
        return;
      }
      var consumer = level.getSeverity() < WARNING.getSeverity() ? out : err;
      var message = String.format(format, args);
      consumer.println(message);
    }

    /** Run provided tool. */
    void tool(String name, String... args) {
      log(DEBUG, "Running tool '%s' with: %s", name, List.of(args));
      var tool = ToolProvider.findFirst(name).orElseThrow();
      var code = tool.run(out, err, args);
      if (code == 0) {
        log(DEBUG, "Tool '%s' successfully executed.", name);
        return;
      }
      throw new Error("Tool '" + name + "' execution failed with error code: " + code);
    }

    /** Download named tool from given uri. */
    Path load(String name, URI uri) throws Exception {
      var user = Path.of(System.getProperty("user.home"));
      var tool = Path.of(".bach", "tool", name);
      return Util.download(Boolean.getBoolean("offline"), user.resolve(tool), uri);
    }

    /** Run JUnit Platform Console Launcher from Standalone distribution. */
    void junit(Args java, String... args) throws Exception {
      var version = "1.5.0-M1";
      var name = "junit-platform-console-standalone";
      var root = "https://repo1.maven.org/maven2";
      var file = name + "-" + version + ".jar";
      var uri = String.join("/", root, "org/junit/platform", name, version, file);
      var jar = load("junit", URI.create(uri));
      var program = ProcessHandle.current().info().command().map(Path::of).orElseThrow();
      var command = new Args().with(program);
      command.addAll(java);
      command.with("-jar", jar).withEach(List.of(args));
      log(DEBUG, "JUnit: %s", command);
      var process = new ProcessBuilder(command.toStringArray()).start();
      var code = process.waitFor();
      out.print(new String(process.getInputStream().readAllBytes()));
      err.print(new String(process.getErrorStream().readAllBytes()));
      if (code != 0) {
        throw new AssertionError("JUnit run exited with code " + code);
      }
    }

    long toDurationMillis() {
      return TimeUnit.MILLISECONDS.convert(Duration.between(start, Instant.now()));
    }
  }

  /** Building block, source set, scope, directory, named context: {@code main}, {@code test}. */
  static class Realm {
    /** Create realm by guessing the module source path using its name. */
    static Realm of(String name, Path home, Path target, Realm... requiredRealms) {
      var source =
          Util.findFirstDirectory(home, "src/" + name + "/java", "src/" + name, name)
              .map(home::relativize)
              .orElseThrow(() -> new Error("Couldn't find module source path!"));
      var modulePath = new ArrayList<Path>();
      for (var required : requiredRealms) {
        modulePath.add(required.packagedModules);
        modulePath.addAll(required.modulePath);
      }
      var lib = Path.of("lib", name);
      if (Files.isDirectory(lib)) {
        modulePath.add(lib);
      }
      return new Realm(name, source, target, modulePath);
    }

    /** Logical name of the realm. */
    final String name;
    /** Module source path. */
    final Path source;
    /** Target root. */
    final Path target;
    /** Module path */
    final List<Path> modulePath;

    final Path compiledBase;
    final Path compiledJavadoc;
    final Path compiledModules;
    final Path compiledMulti;
    final Path packagedJavadoc;
    final Path packagedModules;
    final Path packagedSources;

    Realm(String name, Path source, Path target, List<Path> modulePath) {
      this.name = name;
      this.source = source;
      this.target = target;
      this.modulePath = modulePath;

      var work = target.resolve(name);
      compiledBase = work.resolve("compiled");
      compiledJavadoc = compiledBase.resolve("javadoc");
      compiledModules = compiledBase.resolve("modules");
      compiledMulti = compiledBase.resolve("multi-release");
      packagedJavadoc = work.resolve("javadoc");
      packagedModules = work.resolve("modules");
      packagedSources = work.resolve("sources");
    }

    /** Realm does not need to be treated with jar, javadoc, and all the bells and whistles. */
    boolean compileOnly() {
      return "test".equals(name);
    }

    /** Launch JUnit Platform for this realm. */
    boolean containsTests() {
      return "test".equals(name);
    }

    @Override
    public String toString() {
      return "Realm{" + "name=" + name + ", source=" + source + '}';
    }
  }

  /** Build modules. */
  abstract class AbstractModuleBuilder {
    final Run run;
    final Realm realm;
    final Path moduleSourcePath;

    AbstractModuleBuilder(Run run, Realm realm) {
      this.run = run;
      this.realm = realm;
      this.moduleSourcePath = home.resolve(realm.source);
    }
  }

  /** Build regular modules. */
  class ModuleBuilder extends AbstractModuleBuilder {

    ModuleBuilder(Run run, Realm realm) {
      super(run, realm);
    }

    void build(List<String> regularModules) {
      if (regularModules.isEmpty()) {
        run.log(DEBUG, "No regular modules available to build.");
        return;
      }
      run.log(DEBUG, "Building %d module(s): %s", regularModules.size(), regularModules);
      compile(regularModules);
      if (realm.compileOnly()) {
        return;
      }
      try {
        for (var module : regularModules) {
          jarModule(module);
          jarSources(module);
          // TODO Create "-javadoc.jar" for this regular module
        }
        javadoc();
      } catch (Exception e) {
        throw new Error("Building regular modules failed!", e);
      }
    }

    private void compile(List<String> modules) {
      var modulePath = new ArrayList<Path>();
      modulePath.add(realm.packagedModules); // grab mr-jar's
      modulePath.addAll(realm.modulePath);
      var javac =
          new Args()
              .with(false, "-verbose")
              .with("-encoding", "UTF-8")
              .with("-Xlint")
              .with("-d", realm.compiledModules)
              .with("--module-path", modulePath)
              .with("--module-version", version)
              .with("--module-source-path", moduleSourcePath)
              .with("--module", String.join(",", modules));
      run.tool("javac", javac.toStringArray());
    }

    private void jarModule(String module) throws Exception {
      Files.createDirectories(realm.packagedModules);
      var modularJar = realm.packagedModules.resolve(module + "@" + version + ".jar");
      var jar =
          new Args()
              .with(debug, "--verbose")
              .with("--create")
              .with("--file", modularJar)
              .with("-C", realm.compiledModules.resolve(module))
              .with(".");
      run.tool("jar", jar.toStringArray());
    }

    private void jarSources(String module) throws Exception {
      Files.createDirectories(realm.packagedSources);
      var sourcesJar = realm.packagedSources.resolve(module + "@" + version + "-sources.jar");
      var jar =
          new Args()
              .with(debug, "--verbose")
              .with("--create")
              .with("--file", sourcesJar)
              .with("-C", moduleSourcePath.resolve(module))
              .with(".");
      run.tool("jar", jar.toStringArray());
    }

    private void javadoc() {
      var modules = Util.listDirectoryNames(moduleSourcePath);
      var javaSources = new ArrayList<String>();
      javaSources.add(moduleSourcePath.toString());
      for (var release = 7; release <= Runtime.version().feature(); release++) {
        var separator = File.separator;
        javaSources.add(moduleSourcePath + separator + "*" + separator + "java-" + release);
      }
      var javadoc =
          new Args()
              .with(false, "-verbose")
              .with("-encoding", "UTF-8")
              .with("-quiet")
              .with("-windowtitle", project + " " + version)
              .with("-d", realm.compiledJavadoc)
              .with("--module-path", realm.modulePath)
              .with("--module-source-path", String.join(File.pathSeparator, javaSources))
              .with("--module", String.join(",", modules));
      run.tool("javadoc", javadoc.toStringArray());
      // TODO Create "project-javadoc.jar"
    }
  }

  /** Build multi-release modules. */
  class MultiReleaseBuilder extends AbstractModuleBuilder {
    MultiReleaseBuilder(Run run, Realm realm) {
      super(run, realm);
    }

    void build(String module) {
      int base = 8; // TODO Find declared low base number: "java-*"
      for (var release = base; release <= Runtime.version().feature(); release++) {
        compile(module, base, release);
      }
      if (realm.compileOnly()) {
        return;
      }
      try {
        jarModule(module, base);
        jarSources(module, base);
        // TODO Create "-javadoc.jar" for this multi-release module
      } catch (Exception e) {
        throw new Error("Jarring module " + module + " failed!", e);
      }
    }

    private void compile(String module, int base, int release) {
      var moduleSourcePath = home.resolve(realm.source);
      var javaR = "java-" + release;
      var source = moduleSourcePath.resolve(module).resolve(javaR);
      if (Files.notExists(source)) {
        run.log(DEBUG, "Skipping %s, no source path exists: %s", javaR, source);
        return;
      }
      var destination = realm.compiledMulti.resolve(javaR);
      var javac =
          new Args()
              .with(false, "-verbose")
              .with("-encoding", "UTF-8")
              .with("-Xlint")
              .with("--release", release);
      if (release < 9) {
        javac.with("-d", destination.resolve(module));
        // TODO "-cp" ...
        javac.withEach(Util.listJavaFiles(source)); // javac.with("**/*.java");
      } else {
        javac.with("-d", destination);
        javac.with("--module-version", version);
        javac.with("--module-path", realm.modulePath);
        var pathR = moduleSourcePath + File.separator + "*" + File.separator + javaR;
        var sources = List.of(pathR, "" + moduleSourcePath);
        javac.with("--module-source-path", String.join(File.pathSeparator, sources));
        javac.with(
            "--patch-module",
            module + '=' + realm.compiledMulti.resolve("java-" + base).resolve(module));
        javac.with("--module", module);
      }
      run.tool("javac", javac.toStringArray());
    }

    private void jarModule(String module, int base) throws Exception {
      Files.createDirectories(realm.packagedModules);
      var file = realm.packagedModules.resolve(module + '@' + version + ".jar");
      var source = realm.compiledMulti;
      var javaBase = source.resolve("java-" + base).resolve(module);
      var jar =
          new Args()
              .with(debug, "--verbose")
              .with("--create")
              .with("--file", file)
              // "base" classes
              .with("-C", javaBase)
              .with(".");
      // "base" + 1 .. N files
      for (var release = base + 1; release <= Runtime.version().feature(); release++) {
        var javaRelease = source.resolve("java-" + release).resolve(module);
        if (Files.notExists(javaRelease)) {
          continue;
        }
        jar.with("--release", release);
        jar.with("-C", javaRelease);
        jar.with(".");
      }
      run.tool("jar", jar.toStringArray());
    }

    private void jarSources(String module, int base) throws Exception {
      Files.createDirectories(realm.packagedSources);
      var file = realm.packagedSources.resolve(module + '@' + version + "-sources.jar");
      var source = home.resolve(realm.source).resolve(module);
      var javaBase = source.resolve("java-" + base);
      var jar =
          new Args()
              .with(debug, "--verbose")
              .with("--create")
              .with("--file", file)
              // "base" classes
              .with("-C", javaBase)
              .with(".");
      // "base" + 1 .. N files
      for (var release = base + 1; release <= Runtime.version().feature(); release++) {
        var javaRelease = source.resolve("java-" + release);
        if (Files.notExists(javaRelease)) {
          continue;
        }
        jar.with("--release", release);
        jar.with("-C", javaRelease);
        jar.with(".");
      }
      run.tool("jar", jar.toStringArray());
    }
  }

  /** Static helpers. */
  static final class Util {
    /** No instance permitted. */
    Util() {
      throw new Error();
    }

    /** Download file from supplied uri to specified destination directory. */
    static Path download(boolean offline, Path folder, URI uri) throws Exception {
      // logger.accept("download(" + uri + ")");
      var fileName = extractFileName(uri);
      var target = Files.createDirectories(folder).resolve(fileName);
      var url = uri.toURL();
      if (offline) {
        if (Files.exists(target)) {
          // logger.accept("Offline mode is active and target already exists.");
          return target;
        }
        throw new IllegalStateException("Target is missing and being offline: " + target);
      }
      var connection = url.openConnection();
      try (var sourceStream = connection.getInputStream()) {
        var millis = connection.getLastModified();
        var lastModified = FileTime.fromMillis(millis == 0 ? System.currentTimeMillis() : millis);
        if (Files.exists(target)) {
          // logger.accept("Local target file exists. Comparing last modified timestamps...");
          var fileModified = Files.getLastModifiedTime(target);
          // logger.accept(" o Remote Last Modified -> " + lastModified);
          // logger.accept(" o Target Last Modified -> " + fileModified);
          if (fileModified.equals(lastModified)) {
            // logger.accept(String.format("Already downloaded %s previously.", fileName));
            return target;
          }
          // logger.accept("Local target file differs from remote source -- replacing it...");
        }
        // logger.accept("Transferring " + uri);
        try (var targetStream = Files.newOutputStream(target)) {
          sourceStream.transferTo(targetStream);
        }
        Files.setLastModifiedTime(target, lastModified);
        // logger.accept(String.format(" o Remote   -> %s", uri));
        // logger.accept(String.format(" o Target   -> %s", target.toUri()));
        // logger.accept(String.format(" o Modified -> %s", lastModified));
        // logger.accept(String.format(" o Size     -> %d bytes", Files.size(target)));
        // logger.accept(String.format("Downloaded %s successfully.", fileName));
      }
      return target;
    }

    /** Extract last path element from the supplied uri. */
    static String extractFileName(URI uri) {
      var path = uri.getPath(); // strip query and fragment elements
      return path.substring(path.lastIndexOf('/') + 1);
    }

    /** Find first subdirectory below the given home path. */
    static Optional<Path> findFirstDirectory(Path home, String... paths) {
      return Arrays.stream(paths).map(home::resolve).filter(Files::isDirectory).findFirst();
    }

    /** Test supplied path for pointing to a regular Java source compilation unit file. */
    static boolean isJavaFile(Path path) {
      if (Files.isRegularFile(path)) {
        var name = path.getFileName().toString();
        if (name.endsWith(".java")) {
          return name.indexOf('.') == name.length() - 5; // single dot in filename
        }
      }
      return false;
    }

    /** Test supplied path for pointing to a regular Java archive file. */
    static boolean isJarFile(Path path) {
      return Files.isRegularFile(path) && path.getFileName().toString().endsWith(".jar");
    }

    /** Return list of child directories directly present in {@code root} path. */
    static List<Path> listDirectories(Path root) {
      if (Files.notExists(root)) {
        return List.of();
      }
      try (var paths = Files.find(root, 1, (path, attr) -> Files.isDirectory(path))) {
        return paths.filter(path -> !root.equals(path)).collect(Collectors.toList());
      } catch (Exception e) {
        throw new Error("findDirectories failed for root: " + root, e);
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

    /** List all regular files matching the given filter. */
    static List<Path> listFiles(Collection<Path> roots, Predicate<Path> filter) {
      var files = new ArrayList<Path>();
      for (var root : roots) {
        try (var stream = Files.walk(root)) {
          stream.filter(Files::isRegularFile).filter(filter).forEach(files::add);
        } catch (Exception e) {
          throw new Error("Finding files failed for: " + roots, e);
        }
      }
      return files;
    }

    /** List all regular Java files in given root directory. */
    static List<Path> listJavaFiles(Path root) {
      return listFiles(List.of(root), Util::isJavaFile);
    }
  }
}
