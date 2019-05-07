import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.spi.ToolProvider;

import static java.lang.System.Logger.Level.ERROR;
import static java.lang.System.Logger.Level.INFO;
import static java.lang.System.Logger.Level.TRACE;

/** Modular project model. */
@SuppressWarnings("WeakerAccess")
class Make implements ToolProvider {

  /** Version is either {@code master} or {@link Runtime.Version#parse(String)}-compatible. */
  static final String VERSION = "master";

  /** Convenient short-cut to {@code "user.dir"} as a path. */
  static final Path USER_PATH = Path.of(System.getProperty("user.dir"));

  /** Set "single line" logging format system property, unless already set. */
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

  final System.Logger logger;
  final boolean dryRun;
  final String project, version;
  final Path home, work;
  final List<Realm> realms;

  Make() {
    this(
        System.getLogger("Make.java"),
        Boolean.getBoolean("ry-run"),
        "project",
        "1.0.0-SNAPSHOT",
        USER_PATH,
        USER_PATH,
        List.of(new Realm("main", Path.of("src", "main"), List.of("?"))));
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
    this.work = work;
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
    logger.log(INFO, " home = {0}", home.toUri());
    logger.log(INFO, " work = {0}", work.toUri());
    for (int i = 0; i < realms.size(); i++) {
      logger.log(INFO, " realms[{0}] = {1}", i, realms.get(i));
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

  private void tool(Run run, String name, String... args) {
    logger.log(TRACE, "Running tool named {0} with: {1}", name, List.of(args));
    var tool = ToolProvider.findFirst(name).orElseThrow();
    var code = tool.run(run.out, run.err, args);
    if (code == 0) {
      logger.log(TRACE, "Tool {0} successfully executed.", name);
      return;
    }
    throw new Error("Tool '" + name + "' execution failed with error code: " + code);
  }

  private void build(Run run) throws Exception {
    for (var realm : realms) {
      build(run, realm);
    }
  }

  private void build(Run run, Realm realm) throws Exception {
    var root = home.resolve(realm.root);
    if (Files.notExists(root)) {
      return;
    }
    tool(
        run,
        "javac",
        "-d",
        work.toString(),
        "--module-source-path",
        root.toString(),
        "--module",
        String.join(",", realm.modules));
  }

  /** Runtime context information. */
  static class Run {
    final PrintWriter out;
    final PrintWriter err;
    final Instant start;

    Run(PrintWriter out, PrintWriter err) {
      this.out = out;
      this.err = err;
      this.start = Instant.now();
    }

    long toDurationMillis() {
      return TimeUnit.MILLISECONDS.convert(Duration.between(start, Instant.now()));
    }
  }

  static class Realm {

    final String name;
    final Path root;
    final List<String> modules;

    Realm(String name, Path root, List<String> modules) {
      this.name = name;
      this.root = root;
      this.modules = modules;
    }

    @Override
    public String toString() {
      return "Realm{" + "name='" + name + '\'' + ", root=" + root + ", modules=" + modules + '}';
    }
  }
}
