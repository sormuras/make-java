import static java.lang.System.Logger.Level.DEBUG;
import static java.lang.System.Logger.Level.ERROR;
import static java.lang.System.Logger.Level.INFO;
import static java.lang.System.Logger.Level.WARNING;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
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
      System.err.println("Make.java failed with error code: " + code);
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

  private Make(Configuration configuration) {
    this.configuration = configuration;
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

    var main = new Realm(configuration, "main");
    var test = new Realm(configuration, "test");

    var modules = new ArrayList<String>();
    modules.addAll(main.listModules());
    modules.addAll(test.listModules());

    if (modules.isEmpty()) {
      run.log(ERROR, "No module found: " + configuration.home);
      return -1;
    }

    run.log(DEBUG, "Modules in 'main' realm: %s", main.listModules());
    run.log(DEBUG, "Modules in 'test' realm: %s", test.listModules());

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
      this.home = home.toAbsolutePath().normalize();

      var configurator = new Configurator(properties);
      var work = Path.of(configurator.get("work", "target"));
      this.work = work.isAbsolute() ? work : home.resolve(work);
      this.project =
          new Project(
              configurator.get("name", home.getFileName().toString()),
              configurator.get("version", "1.0.0-SNAPSHOT"));
      this.threshold = configurator.threshold();
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
  }

  /** Modular source set. */
  static class Realm {
    final String name;
    final Path source;
    final Path target;

    Realm(Configuration configuration, String name) {
      this.name = name;
      this.source = configuration.home.resolve("src").resolve(name);
      this.target = configuration.home.resolve("target").resolve(name);
    }

    List<String> listModules() {
      return Files.isDirectory(source) ? Util.listDirectoryNames(source) : List.of();
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
