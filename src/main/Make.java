import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.spi.ToolProvider;

import static java.lang.System.Logger.Level.INFO;

/** Java build tool main program. */
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
  final Project project;
  final Builder builder;

  Make() {
    this(System.getLogger("Make.java"), Boolean.getBoolean("ry-run"), new Project(), new Builder());
  }

  Make(System.Logger logger, boolean dryRun, Project project, Builder builder) {
    this.logger = Objects.requireNonNull(logger);
    this.dryRun = dryRun;
    this.project = project;
    this.builder = builder;
  }

  @Override
  public String name() {
    return "Make.java";
  }

  @Override
  public int run(PrintWriter out, PrintWriter err, String... args) {
    logger.log(INFO, "{0} - {1}", name(), VERSION);
    logger.log(INFO, "  args = {0}", List.of(args));
    if (dryRun) {
      logger.log(INFO, "Dry-run ends here.");
      return 0;
    }
    return builder.build(this, project);
  }

  /** Modular project model. */
  static class Project {
    final Path home, work;
    final String name, version;

    Project() {
      this(USER_PATH, USER_PATH);
    }

    Project(Path source, Path target) {
      this(source, target, "project", "1.0.0-SNAPSHOT");
    }

    Project(Path home, Path work, String name, String version) {
      this.home = home;
      this.work = work;
      this.name = name;
      this.version = version;
    }
  }

  static class Builder {
    int build(Make make, Project project) {
      make.logger.log(INFO, "Building {0} {1}", project.name, project.version);
      make.logger.log(INFO, " home = {0}", project.home.toUri());
      make.logger.log(INFO, " work = {0}", project.work.toUri());
      return 0;
    }
  }
}
