import static java.lang.System.Logger.Level.DEBUG;
import static java.lang.System.Logger.Level.ERROR;
import static java.lang.System.Logger.Level.INFO;
import static java.lang.System.Logger.Level.WARNING;

import java.io.File;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
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

  private void assemble(Run run) {
    run.log(DEBUG, "Assembling 3rd-party libraries...");
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

      private System.Logger.Level threshold() {
        var debug = System.getProperty("debug".substring(1));
        if ("".equals(debug) || "true".equals(debug)) {
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

    final Path home;
    final Path work;
    final Project project;
    final System.Logger.Level threshold;

    Configuration(Path home, Properties properties) {
      this.home = USER_PATH.relativize(home.toAbsolutePath().normalize());

      var configurator = new Configurator(properties);
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
      this.modules = Files.isDirectory(source) ? Util.listDirectoryNames(source) : List.of();
    }
  }

  /** Classpath-based builder. */
  class ClassicalBuilder {
    final Run run;

    ClassicalBuilder(Run run) {
      this.run = run;
    }

    void build() {
      compile(main);
      jarClasses(main);
      jarSources(main);
      // TODO document(main);
      // TODO compile(test);
      // TODO junit(test);
    }

    private void compile(Realm realm) {
      var destination = realm.target.resolve("classes");
      var units = Util.listPaths(realm.source, "*.java");
      var javac = configuration.newJavacArgs(destination).addEach(units);
      run.tool("javac", javac.toStringArray());
    }

    private void jarClasses(Realm realm) {
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
  }
}
