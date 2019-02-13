import static java.lang.System.Logger.Level.DEBUG;
import static java.lang.System.Logger.Level.ERROR;
import static java.lang.System.Logger.Level.INFO;
import static java.lang.System.Logger.Level.WARNING;

import java.nio.file.Path;
import java.util.List;

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

  Make() {
    this(List.of());
  }

  Make(List<String> arguments) {
    this(System.getLogger("Make.java"), USER_PATH, arguments);
  }

  Make(System.Logger logger, Path base, List<String> arguments) {
    this.logger = logger;
    this.base = base;
    this.arguments = arguments;
  }

  /** Run default actions. */
  int run() {
    return run(new Action.Banner(), new Action.Check());
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
    }
    return 0;
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
        if (make.arguments.contains("FAIL")) {
          make.logger.log(WARNING, "Error trigger 'FAIL' detected!");
          return 1;
        }
        return 0;
      }
    }
  }
}
