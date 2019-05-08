import java.io.PrintWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.spi.ToolProvider;
import java.util.stream.Collectors;

import static java.lang.System.Logger.Level.ERROR;
import static java.lang.System.Logger.Level.INFO;
import static java.lang.System.Logger.Level.TRACE;
import static java.lang.System.Logger.Level.WARNING;

/** Modular project model and maker. */
@SuppressWarnings("WeakerAccess")
class Make implements ToolProvider {

  /** Version is either {@code master} or {@link Runtime.Version#parse(String)}-compatible. */
  static final String VERSION = "master";

  /** Convenient short-cut to {@code "user.dir"} as a path. */
  static final Path USER_PATH = Path.of(System.getProperty("user.dir"));

  /** Set "single line" logging format system property, unless the property is already set. */
  static void installSingleLineSimpleFormatterFormat() {
    var format = "java.util.logging.SimpleFormatter.format";
    if (System.getProperty(format) != null) {
      return;
    }
    System.setProperty(format, "| %5$s%6$s%n");
  }

  /** Main entry-point. */
  public static void main(String... args) {
    installSingleLineSimpleFormatterFormat();
    var code = new Make().run(System.out, System.err, args);
    if (code != 0) {
      throw new Error("Make.java failed with error code: " + code);
    }
  }

  /** Logger instance. */
  final System.Logger logger;
  /** Dry-run flag. */
  final boolean dryRun;
  /** Name of the project. */
  final String project;
  /** Version of the project. */
  final String version;
  /** Root path of this project. */
  final Path home;
  /** Set of well-known output directories and files. */
  final Work work;
  /** Realms of this project. */
  final List<Realm> realms;

  Make() {
    this(
        System.getLogger("Make.java"),
        Boolean.getBoolean("ry-run"),
        "project",
        "1.0.0-SNAPSHOT",
        USER_PATH,
        USER_PATH.resolve("work"),
        List.of(new Realm("main", Path.of("src", "main"))));
  }

  Make(
      System.Logger logger,
      boolean dryRun,
      String project,
      String version,
      Path home,
      Path work,
      List<Realm> realms) {
    this.logger = logger;
    this.dryRun = dryRun;
    this.project = project;
    this.version = version;
    this.home = home;
    this.work = new Work(work);
    this.realms = realms;
  }

  @Override
  public String name() {
    return "Make.java";
  }

  @Override
  public int run(PrintWriter out, PrintWriter err, String... args) {
    logger.log(INFO, "{0} - {1}", name(), VERSION);
    logger.log(INFO, "  args = {0}", List.of(args));
    logger.log(INFO, "Building {0} {1}", project, version);
    logger.log(INFO, "  home = {0}", home.toUri());
    logger.log(INFO, "  work = {0}", work.base.toUri());
    for (int i = 0; i < realms.size(); i++) {
      logger.log(INFO, "  realms[{0}] = {1}", i, realms.get(i));
    }
    if (dryRun) {
      logger.log(INFO, "Dry-run ends here.");
      return 0;
    }
    var run = new Run(out, err);
    try {
      build(run);
      logger.log(INFO, "Build successful after {0} ms.", run.toDurationMillis());
      return 0;
    } catch (Exception e) {
      logger.log(ERROR, "Build failed: " + e, e);
      return 1;
    }
  }

  void tool(Run run, String name, String... args) {
    logger.log(TRACE, "Running tool named {0} with: {1}", name, List.of(args));
    var tool = ToolProvider.findFirst(name).orElseThrow();
    var code = tool.run(run.out, run.err, args);
    if (code == 0) {
      logger.log(TRACE, "Tool {0} successfully executed.", name);
      return;
    }
    throw new Error("Tool " + name + " execution failed with error code: " + code);
  }

  private void build(Run run) throws Exception {
    for (var realm : realms) {
      build(run, realm);
    }
  }

  private void build(Run run, Realm realm) throws Exception {
    var moduleSourcePath = home.resolve(realm.source);
    if (Files.notExists(moduleSourcePath)) {
      logger.log(WARNING, "Source path of {0} realm not found: {1}", realm.name, moduleSourcePath);
      return;
    }
    var modules = Util.listDirectoryNames(moduleSourcePath);
    if (modules.isEmpty()) {
      throw new Error("No module directories found in source path: " + moduleSourcePath);
    }
    var args =
        new Args()
            .with("-d", work.compiledModules)
            .with("--module-version", version)
            .with("--module-source-path", moduleSourcePath)
            .with("--module", String.join(",", modules));
    tool(run, "javac", args.toStringArray());
    Files.createDirectories(work.packagedModules);
    for (var module : modules) {
      args =
          new Args()
              .with("--create")
              .with("--file", work.packagedModules.resolve(module + "@" + version + ".jar"))
              .with("-C", work.compiledModules.resolve(module))
              .with(".");
      tool(run, "jar", args.toStringArray());
    }
  }

  /** Workspace paths and other assets. */
  class Work {
    final Path base;
    final Path compiledBase;
    final Path compiledModules;
    final Path packagedModules;

    Work(Path base) {
      this.base = base;
      compiledBase = base.resolve("compiled");
      compiledModules = compiledBase.resolve("modules");
      packagedModules = base.resolve("modules");
    }
  }

  /** Command-line program argument list builder. */
  static class Args extends ArrayList<String> {
    Args with(Object value) {
      add(value.toString());
      return this;
    }

    Args with(Object key, Object value) {
      return with(key).with(value);
    }

    String[] toStringArray() {
      return toArray(String[]::new);
    }
  }

  /** Runtime context information. */
  static class Run {
    /** Stream to which "expected" output should be written. */
    final PrintWriter out;
    /** Stream to which any error messages should be written. */
    final PrintWriter err;
    /** Time instant recorded on creation of this instance. */
    final Instant start;

    Run(Writer writer) {
      this(new PrintWriter(writer), new PrintWriter(writer));
    }

    Run(PrintWriter out, PrintWriter err) {
      this.out = out;
      this.err = err;
      this.start = Instant.now();
    }

    long toDurationMillis() {
      return TimeUnit.MILLISECONDS.convert(Duration.between(start, Instant.now()));
    }
  }

  /** Building block, source set, scope, directory, named context: {@code main}, {@code test}. */
  static class Realm {
    /** Logical name of the realm. */
    final String name;
    /** Module source path. */
    final Path source;

    Realm(String name, Path source) {
      this.name = name;
      this.source = source;
    }

    @Override
    public String toString() {
      return "Realm{" + "name=" + name + ", source=" + source + '}';
    }
  }

  /** Static helpers. */
  static final class Util {
    /** No instance permitted. */
    Util() {
      throw new Error();
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

    /** Return list of child directory names directly present in {@code root} path. */
    static List<String> listDirectoryNames(Path root) {
      return listDirectories(root).stream()
          .map(root::relativize)
          .map(Path::toString)
          .collect(Collectors.toList());
    }
  }
}
