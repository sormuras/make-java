import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.List;
import java.util.spi.ToolProvider;

import static java.lang.System.Logger.Level.INFO;

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

  Make() {
    this(
        System.getLogger("Make.java"),
        Boolean.getBoolean("ry-run"),
        "project",
        "1.0.0-SNAPSHOT",
        USER_PATH,
        USER_PATH);
  }

  Make(System.Logger logger, boolean dryRun, String project, String version, Path home, Path work) {
    this.logger = logger;
    this.dryRun = dryRun;
    this.project = project;
    this.version = version;
    this.home = home;
    this.work = work;
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
    if (dryRun) {
      logger.log(INFO, "Dry-run ends here.");
      return 0;
    }
    return 0;
  }
}
