import static java.lang.System.Logger.Level.DEBUG;
import static java.lang.System.Logger.Level.ERROR;
import static java.lang.System.Logger.Level.INFO;
import static java.lang.System.Logger.Level.WARNING;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.spi.ToolProvider;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Java build tool main program. */
class Make {

  /** Version is either {@code master} or {@link Runtime.Version#parse(String)}-compatible. */
  static final String VERSION = "master";

  /** Convenient short-cut to {@code "user.dir"} as a path. */
  static final Path USER_PATH = Path.of(System.getProperty("user.dir"));

  /** Main entry-point running all default actions. */
  public static void main(String... args) {
    var format = "java.util.logging.SimpleFormatter.format";
    if (System.getProperty(format) == null) {
      System.setProperty(format, "%1$tY-%1$tm-%1$td %1$tH:%1$tM:%1$tS %4$-6s %2$s %5$s%6$s%n");
    }
    var make = new Make(List.of(args));
    var code = make.run();
    if (code != 0) {
      throw new Error("Make.java failed with error code: " + code);
    }
  }

  final List<String> arguments;
  final Path base;
  final System.Logger logger;
  final Variables var;
  final Project project;

  Make() {
    this(List.of());
  }

  Make(List<String> arguments) {
    this(System.getLogger("Make.java"), USER_PATH, arguments);
  }

  Make(System.Logger logger, Path base, List<String> arguments) {
    this.logger = logger;
    this.base = base;
    this.arguments = List.copyOf(arguments);
    this.var = new Variables();
    this.project = new Project();
  }

  Path based(Path path) {
    if (path.isAbsolute()) {
      return path;
    }
    if (base.equals(USER_PATH)) {
      return path;
    }
    return base.resolve(path).normalize();
  }

  Path based(String first, String... more) {
    return based(Path.of(first, more));
  }

  Path based(Property property) {
    return based(Path.of(var.get(property)));
  }

  /** Run default actions. */
  int run() {
    if (arguments.isEmpty()) {
      return run(new Action.Banner(), new Action.Check());
    }
    if (arguments.get(0).equalsIgnoreCase("tool")) {
      if (arguments.size() == 1) {
        logger.log(ERROR, "Missing name of tool to run!");
        return 1;
      }
      var command = new Command(arguments.get(1));
      command.addAll(arguments.subList(2, arguments.size()));
      var tool = new Action.Tool(command);
      var.out = System.out::println;
      var.err = System.err::println;
      return run(new Action.Banner(), new Action.Check(), tool);
    }
    logger.log(ERROR, "Unsupported operation: " + arguments);
    return 1;
  }

  /** Run supplied actions. */
  int run(Action... actions) {
    return run(List.of(actions));
  }

  /** Run supplied actions. */
  int run(List<Action> actions) {
    if (actions.isEmpty()) {
      logger.log(WARNING, "No actions to run...");
    }
    for (var action : actions) {
      logger.log(DEBUG, "Running action {0}...", action.name());
      var code = action.run(this);
      if (code != 0) {
        logger.log(ERROR, "Action {0} failed with error code: {1}", action.name(), code);
        return code;
      }
      logger.log(DEBUG, "Action {0} succeeded.", action.name());
    }
    return 0;
  }

  /** Constants with default values. */
  enum Property {
    /** Cache of binary tools. */
    PATH_CACHE_TOOLS(".make/tools"),

    /** Cache of resolved modules. */
    PATH_CACHE_MODULES(".make/modules"),

    /** Name of the project. */
    PROJECT_NAME("project"),

    /** Version of the project. */
    PROJECT_VERSION("1.0.0-SNAPSHOT"),

    /** JUnit Platform Console Standalone URI. */
    TOOL_JUNIT_URI(
        "http://central.maven.org/maven2/org/junit/platform/junit-platform-console-standalone/1.4.0/junit-platform-console-standalone-1.4.0.jar");

    final String key;
    final String defaultValue;
    final String description;

    Property(String defaultValue, String... description) {
      this.key = "make." + name().toLowerCase().replace('_', '.');
      this.defaultValue = defaultValue;
      this.description = String.join("", description);
    }
  }

  /** Make's project object model. */
  class Project {
    final String name, version;

    Project() {
      var defaultName =
          Optional.ofNullable(base.getFileName())
              .map(Object::toString)
              .orElse(Property.PROJECT_NAME.defaultValue);
      this.name = var.get(Property.PROJECT_NAME.key, defaultName);
      this.version = var.get(Property.PROJECT_VERSION);
    }
  }

  /** Variable state holder. */
  class Variables {

    /** Managed properties loaded from {@code ${base}/make.properties} file. */
    final Properties properties = load(base.resolve("make.properties"));

    /** Offline mode. */
    boolean offline = Boolean.parseBoolean(get("make.offline", "false"));

    /** Standard output line consumer. */
    Consumer<String> out = line -> logger.log(DEBUG, line);

    /** Error output line consumer. */
    Consumer<String> err = line -> logger.log(ERROR, line);

    /** Get value for the supplied property, using its key and default value. */
    String get(Property property) {
      return get(property.key, property.defaultValue);
    }

    /** Get value for the supplied property key. */
    String get(String key, String defaultValue) {
      var value = System.getProperty(key);
      if (value != null) {
        return value;
      }
      return properties.getProperty(key, defaultValue);
    }

    /** Load from properties from path. */
    Properties load(Path path) {
      var properties = new Properties();
      if (Files.exists(path)) {
        try (var stream = Files.newInputStream(path)) {
          properties.load(stream);
        } catch (Exception e) {
          throw new Error("Loading properties failed: " + path, e);
        }
      }
      return properties;
    }
  }

  /** Action running on Make instances. */
  @FunctionalInterface
  interface Action {
    /** Human-readable name of this action. */
    default String name() {
      return getClass().getSimpleName();
    }

    /** Run this action and return zero on success. */
    int run(Make make);

    /** Log banner action. */
    class Banner implements Action {

      @Override
      public int run(Make make) {
        make.logger.log(INFO, "Make.java - {0}", Make.VERSION);
        return 0;
      }
    }

    /** Check preconditions action. */
    class Check implements Action {

      @Override
      public int run(Make make) {
        if (make.base.getNameCount() == 0) {
          make.logger.log(ERROR, "Base path has zero elements!");
          return 1;
        }
        return 0;
      }
    }

    /** Download files from supplied uris to specified destination directory. */
    class Download implements Action {

      /** Extract path last element from the supplied uri. */
      static String fileName(URI uri) {
        var urlString = uri.getPath();
        var begin = urlString.lastIndexOf('/') + 1;
        return urlString.substring(begin).split("\\?")[0].split("#")[0];
      }

      final Path destination;
      final List<URI> uris;

      Download(Path destination, URI... uris) {
        this.destination = destination;
        this.uris = List.of(uris);
      }

      @Override
      public int run(Make make) {
        make.logger.log(DEBUG, "Downloading {0} file(s) to {1}...", uris.size(), destination);
        try {
          for (var uri : uris) {
            run(make, uri);
          }
          return 0;
        } catch (Exception e) {
          make.logger.log(ERROR, "Download failed: " + e.getMessage());
          return 1;
        }
      }

      private void run(Make make, URI uri) throws Exception {
        make.logger.log(DEBUG, "Downloading {0}...", uri);
        var fileName = fileName(uri);
        var target = destination.resolve(fileName);
        if (make.var.offline) {
          if (Files.exists(target)) {
            make.logger.log(DEBUG, "Offline mode is active and target already exists.");
            return;
          }
          throw new IllegalStateException("Target is missing and being offline: " + target);
        }
        Files.createDirectories(destination);
        var connection = uri.toURL().openConnection();
        try (var sourceStream = connection.getInputStream()) {
          var urlLastModifiedMillis = connection.getLastModified();
          var urlLastModifiedTime = FileTime.fromMillis(urlLastModifiedMillis);
          if (Files.exists(target)) {
            make.logger.log(DEBUG, "Local file exists. Comparing attributes to remote file...");
            var unknownTime = urlLastModifiedMillis == 0L;
            if (Files.getLastModifiedTime(target).equals(urlLastModifiedTime) || unknownTime) {
              var localFileSize = Files.size(target);
              var contentLength = connection.getContentLengthLong();
              if (localFileSize == contentLength) {
                make.logger.log(DEBUG, "Local and remote file attributes seem to match.");
                return;
              }
            }
            make.logger.log(DEBUG, "Local file differs from remote -- replacing it...");
          }
          make.logger.log(DEBUG, "Transferring {0}...", uri);
          try (var targetStream = Files.newOutputStream(target)) {
            sourceStream.transferTo(targetStream);
          }
          if (urlLastModifiedMillis != 0L) {
            Files.setLastModifiedTime(target, urlLastModifiedTime);
          }
          make.logger.log(INFO, "Downloaded {0} successfully.", fileName);
          make.logger.log(DEBUG, " o Size -> {0} bytes", Files.size(target));
          make.logger.log(DEBUG, " o Last Modified -> {0}", urlLastModifiedTime);
        }
      }
    }

    /** Tool runner action. */
    class Tool implements Action {

      class Gobbler extends StringWriter implements Runnable {

        final Consumer<String> consumer;
        final InputStream stream;

        Gobbler(Consumer<String> consumer) {
          this(consumer, InputStream.nullInputStream());
        }

        Gobbler(Consumer<String> consumer, InputStream stream) {
          this.consumer = consumer;
          this.stream = stream;
        }

        @Override
        public void flush() {
          toString().lines().forEach(consumer);
          getBuffer().setLength(0);
        }

        @Override
        public void run() {
          new BufferedReader(new InputStreamReader(stream)).lines().forEach(consumer);
        }
      }

      final String name;
      final List<String> args;

      Tool(Command command) {
        this.name = command.name;
        this.args = command.arguments;
      }

      Tool(String name, String... args) {
        this.name = name;
        this.args = List.of(args);
      }

      @Override
      public int run(Make make) {
        make.logger.log(INFO, "Running tool: {0} {1}", name, String.join(" ", args));
        // ToolProvider SPI
        var provider = ToolProvider.findFirst(name);
        if (provider.isPresent()) {
          var out = new PrintWriter(new Gobbler(make.var.out), true);
          var err = new PrintWriter(new Gobbler(make.var.err), true);
          return provider.get().run(out, err, args.toArray(String[]::new));
        }
        // Delegate to process builder.
        // TODO Map to managed "jar" tool installation.
        // TODO Find foundation tool executable in "${JDK}/bin" folder.
        var command = new ArrayList<String>();
        command.add(name);
        command.addAll(args);
        var executor = Executors.newFixedThreadPool(2);
        try {
          var process = new ProcessBuilder(command).start();
          executor.submit(new Gobbler(make.var.out, process.getInputStream()));
          executor.submit(new Gobbler(make.var.err, process.getErrorStream()));
          return process.waitFor();
        } catch (Exception e) {
          make.logger.log(ERROR, "Running tool failed: " + e.getMessage(), e);
          return 1;
        } finally {
          executor.shutdownNow();
        }
      }
    }
  }

  /** Command line builder. */
  static class Command {

    /** Test supplied path for pointing to a Java source unit file. */
    static boolean isJavaFile(Path path) {
      if (Files.isRegularFile(path)) {
        var name = path.getFileName().toString();
        if (name.endsWith(".java")) {
          return name.indexOf('.') == name.length() - 5; // single dot in filename
        }
      }
      return false;
    }

    final String name;
    final List<String> arguments;

    Command(String name) {
      this.name = name;
      this.arguments = new ArrayList<>();
    }

    /** Add single non-null argument. */
    Command add(Object argument) {
      arguments.add(argument.toString());
      return this;
    }

    /** Add single argument composed of joined path names using {@link File#pathSeparator}. */
    Command add(Collection<Path> paths) {
      return add(paths.stream(), File.pathSeparator);
    }

    /** Add single argument composed of all stream elements joined by specified separator. */
    Command add(Stream<?> stream, String separator) {
      return add(stream.map(Object::toString).collect(Collectors.joining(separator)));
    }

    /** Add all arguments by invoking {@link #add(Object)} for each element. */
    Command addAll(Object... arguments) {
      for (var argument : arguments) {
        add(argument);
      }
      return this;
    }

    /** Add all arguments by invoking {@link #add(Object)} for each element. */
    Command addAll(Iterable<?> arguments) {
      arguments.forEach(this::add);
      return this;
    }

    /** Add all files visited by walking specified root path recursively. */
    Command addAll(Path root, Predicate<Path> predicate) {
      try (var stream = Files.walk(root).filter(predicate)) {
        stream.forEach(this::add);
      } catch (Exception e) {
        throw new Error("walking path `" + root + "` failed", e);
      }
      return this;
    }

    /** Add all files visited by walking specified root paths recursively. */
    Command addAll(Collection<Path> roots, Predicate<Path> predicate) {
      for (var root : roots) {
        if (Files.notExists(root)) {
          continue;
        }
        addAll(root, predicate);
      }
      return this;
    }

    /** Add all {@code .java} source files by walking specified root paths recursively. */
    Command addAllJavaFiles(Collection<Path> roots) {
      return addAll(roots, Command::isJavaFile);
    }

    /** Dump command executables and arguments using the provided string consumer. */
    Command dump(Consumer<String> consumer) {
      var iterator = arguments.listIterator();
      consumer.accept(name);
      while (iterator.hasNext()) {
        var argument = iterator.next();
        var indent = argument.startsWith("-") ? "" : "  ";
        consumer.accept(indent + argument);
      }
      return this;
    }
  }
}
