import static java.lang.System.Logger.Level.DEBUG;
import static java.lang.System.Logger.Level.ERROR;
import static java.lang.System.Logger.Level.INFO;
import static java.lang.System.Logger.Level.WARNING;

import java.io.File;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Properties;
import java.util.TreeSet;
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

    run.log(DEBUG, "Modules in 'main' realm: %s", main.modules);
    run.log(DEBUG, "Modules in 'test' realm: %s", test.modules);

    return 0;
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
    final System.Logger.Level threshold;
    final PrintWriter out;
    final PrintWriter err;

    Run(System.Logger.Level threshold, PrintWriter out, PrintWriter err) {
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

  /** Static helpers. */
  static class Util {

    /** Return list of child directories directly present in {@code root} path. */
    static List<Path> listDirectories(Path root) {
      try (var paths = Files.find(root, 1, (path, attr) -> Files.isDirectory(path))) {
        return paths.filter(path -> !root.equals(path)).collect(Collectors.toList());
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
  }
}
