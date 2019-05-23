import static java.lang.System.Logger.Level.DEBUG;
import static java.lang.System.Logger.Level.ERROR;
import static java.lang.System.Logger.Level.INFO;
import static java.lang.System.Logger.Level.WARNING;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.io.PrintWriter;
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
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.function.UnaryOperator;
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

  private Make(Configuration configuration) {
    this.configuration = configuration;
    this.main = new Realm(configuration, "main");
    this.test = new Realm(configuration, "test");
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
      run.log(INFO, "Build successful after %d ms.", run.toDurationMillis());
      return 0;
    } catch (Throwable throwable) {
      run.log(ERROR, "Build failed: %s", throwable.getMessage());
      throwable.printStackTrace(run.err);
      return 1;
    }
  }

  void assemble(Run run) throws Exception {
    run.log(DEBUG, "Assembling 3rd-party libraries...");
    assemble(run, main);
    assemble(run, test);
  }

  /** Assemble assets and check preconditions. */
  private void assemble(Run run, Realm realm) throws Exception {
    run.log(DEBUG, "Assembling assets for %s realm...", realm.name);
    var libraries = configuration.home.resolve("lib");
    var candidates =
        List.of(
            libraries.resolve(realm.name),
            libraries.resolve(realm.name + "-compile-only"),
            libraries.resolve(realm.name + "-runtime-only"),
            libraries.resolve(realm.name + "-runtime-platform"));
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

  private void build(Run run) {
    if (main.modules.size() + test.modules.size() == 0) {
      run.log(INFO, "No modules found. Trying to build using `--class-path`...");
      new ClassicalBuilder(run).build();
      return;
    }
    run.log(DEBUG, "Modules in 'main' realm: %s", main.modules);
    run.log(DEBUG, "Modules in 'test' realm: %s", test.modules);
    // TODO build modules...
  }

  private void launchJUnitConsole(Run run, ClassLoader loader, Args junit) {
    run.log(DEBUG, "JUnit: %s", junit.list);
    var currentThread = Thread.currentThread();
    var currentContextLoader = currentThread.getContextClassLoader();
    currentThread.setContextClassLoader(loader);
    try {
      var launcher = loader.loadClass("org.junit.platform.console.ConsoleLauncher");
      var execute =
          launcher.getMethod("execute", PrintStream.class, PrintStream.class, String[].class);
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

  /** Global project constants. */
  static class Project {
    final String name;
    final String version;

    Project(String name, String version) {
      this.name = name;
      this.version = version;
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
          throw new RuntimeException("Loading properties failed: " + path, e);
        }
      }
      return properties;
    }

    final boolean dryRun;
    final Path home;
    final Path work;
    final Project project;
    final System.Logger.Level threshold;

    Configuration(Path home, Properties properties) {
      this.home = USER_PATH.relativize(home.toAbsolutePath().normalize());

      var configurator = new Configurator(properties);
      this.dryRun = configurator.is("dry-run", "false");
      var work = Path.of(configurator.get("work", "target"));
      this.work = work.isAbsolute() ? work : this.home.resolve(work);
      this.project =
          new Project(
              configurator.get("name", home.getFileName().toString()),
              configurator.get("version", "1.0.0-SNAPSHOT"));
      this.threshold = configurator.threshold();
    }

    Args newJavacArgs(Path destination) {
      return new Args()
          .add(false, "-verbose")
          .add("-encoding", "UTF-8")
          .add("-Xlint")
          .add("-d", destination);
    }

    Args newJarArgs(Path file) {
      return new Args().add(true, "--verbose").add("--create").add("--file", file);
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
    }

    /** Run provided tool. */
    void tool(String name, String... args) {
      log(DEBUG, "Running tool '%s' with: %s", name, List.of(args));
      var tool = ToolProvider.findFirst(name).orElseThrow();
      var code = tool.run(out, err, args);
      if (code == 0) {
        log(DEBUG, "Tool '%s' successfully run.", name);
        return;
      }
      throw new RuntimeException("Tool '" + name + "' run failed with error code: " + code);
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
    final String name;
    final Path source;
    final Path target;
    final List<String> modules;

    Realm(Configuration configuration, String name) {
      this.name = name;
      this.source = configuration.home.resolve("src").resolve(name);
      this.target = configuration.work.resolve(name);
      this.modules =
          Util.listPaths(source, "module-info.java").size() > 0
              ? Util.listDirectoryNames(source)
              : List.of();
    }
  }

  /** Classpath-based builder. */
  class ClassicalBuilder {
    final Run run;

    ClassicalBuilder(Run run) {
      this.run = run;
    }

    void build() {
      if (Files.exists(main.source)) {
        compile(main);
        jarClasses(main);
        jarSources(main);
        // TODO document(main);
      }
      if (Files.exists(test.source)) {
        compile(test);
        junit();
      }
    }

    void compile(Realm realm) {
      var units = Util.listPaths(realm.source, "*.java");
      if (units.isEmpty()) {
        throw new IllegalStateException("No source files found in: " + realm.source);
      }
      var classPath = new ArrayList<Path>();
      if (realm.name.equals("test")) {
        var name = configuration.project.name + '-' + configuration.project.version;
        classPath.add(main.target.resolve(name + ".jar"));
      }
      var libraries = configuration.home.resolve("lib");
      classPath.addAll(Util.listPaths(libraries.resolve(realm.name), "*.jar"));
      classPath.addAll(Util.listPaths(libraries.resolve(realm.name + "-compile-only"), "*.jar"));
      var javac = configuration.newJavacArgs(realm.target.resolve("classes"));
      if (!classPath.isEmpty()) {
        javac.add("--class-path", classPath);
      }
      run.tool("javac", javac.addEach(units).toStringArray());
    }

    void jarClasses(Realm realm) {
      var destination = realm.target.resolve("classes");
      var name = configuration.project.name + '-' + configuration.project.version;
      var file = realm.target.resolve(name + ".jar");
      var jar = configuration.newJarArgs(file).add("-C", destination).add(".");
      run.tool("jar", jar.toStringArray());
    }

    private void jarSources(Realm realm) {
      var name = configuration.project.name + '-' + configuration.project.version;
      var file = realm.target.resolve(name + "-sources.jar");
      var jar = configuration.newJarArgs(file).add("-C", realm.source).add(".");
      run.tool("jar", jar.toStringArray());
    }

    private void junit() {
      var junit =
          new Args()
              .add("--fail-if-no-tests")
              .add("--reports-dir", test.target.resolve("junit-reports"))
              .add("--class-path", test.target.resolve("classes"))
              .add("--scan-class-path");
      launchJUnitConsole(run, newJUnitConsoleClassLoader(), junit);
    }

    private ClassLoader newJUnitConsoleClassLoader() {
      var libraries = configuration.home.resolve("lib");

      var mainPaths = new ArrayList<Path>();
      var name = configuration.project.name + '-' + configuration.project.version;
      mainPaths.add(main.target.resolve(name + ".jar"));
      mainPaths.addAll(Util.listPaths(libraries.resolve("main"), "*.jar"));
      mainPaths.addAll(Util.listPaths(libraries.resolve("main-runtime-only"), "*.jar"));

      var testPaths = new ArrayList<Path>();
      testPaths.add(test.target.resolve("classes"));
      testPaths.addAll(Util.listPaths(libraries.resolve("test"), "*.jar"));
      testPaths.addAll(Util.listPaths(libraries.resolve("test-runtime-only"), "*.jar"));

      var unitPaths = new ArrayList<Path>();
      unitPaths.addAll(Util.listPaths(libraries.resolve("test-runtime-platform"), "*.jar"));

      var parent = ClassLoader.getPlatformClassLoader();
      var mainLoader = new URLClassLoader("junit-main", Util.toUrls(mainPaths), parent);
      var testLoader = new URLClassLoader("junit-test", Util.toUrls(testPaths), mainLoader);
      var unitLoader = new URLClassLoader("junit-platform", Util.toUrls(unitPaths), testLoader);
      return new URLClassLoader("junit-classical", new URL[0], unitLoader);
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
          var newTarget = target.resolveSibling(contentDisposition.split("=")[1]);
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

    /** List paths specified by a glob pattern. */
    static List<Path> listPaths(Path root, String glob) {
      if (Files.notExists(root)) {
        return List.of();
      }
      var paths = new ArrayList<Path>();
      for (var directory : listDirectories(root, Integer.MAX_VALUE, true)) {
        try (var stream = Files.newDirectoryStream(directory, glob)) {
          stream.forEach(paths::add);
        } catch (Exception e) {
          throw new Error("listPaths failed for directory: " + directory, e);
        }
      }
      return paths;
    }

    static URL[] toUrls(Collection<Path> paths) {
      var urls = new URL[paths.size()];
      var index = 0;
      try {
        for (var path : paths) {
          urls[index++] = path.toUri().toURL();
        }
      } catch (Exception e) {
        throw new Error("toUrls failed: " + paths, e);
      }
      return urls;
    }
  }
}
