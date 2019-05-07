import static java.lang.System.Logger.Level.DEBUG;
import static java.lang.System.Logger.Level.ERROR;
import static java.lang.System.Logger.Level.INFO;
import static java.lang.System.Logger.Level.TRACE;
import static java.lang.System.Logger.Level.WARNING;

import java.io.File;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.spi.ToolProvider;
import java.util.stream.Collectors;

/** Java build tool main program. */
class Make {

  /** Version is either {@code master} or {@link Runtime.Version#parse(String)}-compatible. */
  static final String VERSION = "master";

  /** Convenient short-cut to {@code "user.dir"} as a path. */
  static final Path USER_PATH = Path.of(System.getProperty("user.dir"));

  /** Main entry-point. */
  public static void main(String... args) {
    var format = "java.util.logging.SimpleFormatter.format";
    if (System.getProperty(format) == null) {
      System.setProperty(format, "%1$tH:%1$tM:%1$tS %4$-7s | %2$s %5$s%6$s%n");
    }
    var make = new Make(args);
    var code = make.run();
    if (code != 0) {
      throw new Error("Make.java failed with error code: " + code);
    }
  }

  final System.Logger logger;
  final Path base;
  final Path work;
  final List<String> arguments;
  final boolean dryRun;

  Make(String... arguments) {
    this(
        System.getLogger("Make.java"),
        USER_PATH,
        USER_PATH,
        Boolean.getBoolean("ry-run"),
        List.of(arguments));
  }

  Make(System.Logger logger, Path base, Path work, boolean dryRun, List<String> arguments) {
    this.logger = Objects.requireNonNull(logger);
    this.base = base;
    this.work = work;
    this.dryRun = dryRun;
    this.arguments = List.copyOf(arguments);
  }

  /** Run default actions. */
  int run() {
    logger.log(INFO, "Make.java - {0}", Make.VERSION);
    if (run(new Action.Check()) != 0) {
      logger.log(ERROR, "Check failed!");
      return 1;
    }
    if (dryRun) {
      logger.log(DEBUG, "Dry-run ends here.");
      return 0;
    }
    if (arguments.isEmpty()) {
      return run(new Action.Build());
    }
    return run(new ArrayDeque<>(arguments));
  }

  /** Run single action on this instance. */
  int run(Action action) {
    logger.log(DEBUG, "run(action={0})", action);
    var code = action.run(this);
    logger.log(code == 0 ? DEBUG : ERROR, action + (code == 0 ? " succeeded." : " failed!"));
    return code;
  }

  /** Run actions. */
  int run(Deque<String> actions) {
    var code = 0;
    while (code == 0 && !actions.isEmpty()) {
      var action = actions.removeFirst();
      if (action.equals("build")) {
        code = run(new Action.Build());
        continue;
      }
      if (action.equals("clean")) {
        var target = work.resolve("target");
        if (Files.exists(target)) {
          code = run(new Action.TreeDelete(target));
        }
        continue;
      }
      if (action.equals("tool")) {
        var name = actions.removeFirst();
        var args = actions.toArray(String[]::new);
        actions.clear();
        return run(System.out, System.err, name, args);
      }
      logger.log(ERROR, "Unsupported action : {0}", action);
      code = 2;
    }
    return code;
  }

  /** Run named tool with given arguments. */
  int run(PrintStream out, PrintStream err, String name, String... args) {
    logger.log(DEBUG, "run(name={0}, args={1})", name, args);
    var tool = ToolProvider.findFirst(name).orElseThrow();
    logger.log(DEBUG, "Running tool: {0}", tool);
    return tool.run(out, err, args);
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

    /** Base action class. */
    abstract class AbstractAction implements Action {
      @Override
      public String toString() {
        return name();
      }
    }

    /** Build the project. */
    class Build extends AbstractAction {

      @Override
      public int run(Make make) {
        make.logger.log(WARNING, "Unsupported action: {0}", name());
        return 0;
      }
    }

    /** Check preconditions. */
    class Check extends AbstractAction {

      @Override
      public int run(Make make) {
        if (make.base.getNameCount() == 0) {
          make.logger.log(ERROR, "Path base has not a single name element!");
          return 1;
        }
        if (make.arguments.contains("FAIL")) {
          make.logger.log(ERROR, "Failing per contract...");
          return 1;
        }
        return 0;
      }
    }

    /** Delete selected files and directories from the root directory. */
    class TreeDelete extends AbstractAction {

      final Path root;
      final Predicate<Path> filter;

      TreeDelete(Path root) {
        this(root, __ -> true);
      }

      TreeDelete(Path root, Predicate<Path> filter) {
        this.root = root;
        this.filter = filter;
      }

      @Override
      public int run(Make make) {
        // trivial case: delete existing single file or empty directory right away
        try {
          if (Files.deleteIfExists(root)) {
            make.logger.log(TRACE, "Deleted {0}", root);
            return 0;
          }
        } catch (Exception ignored) {
          // fall-through
        }
        // default case: walk the tree...
        try (var stream = Files.walk(root)) {
          var selected = stream.filter(filter).sorted((p, q) -> -p.compareTo(q));
          for (var path : selected.collect(Collectors.toList())) {
            Files.deleteIfExists(path);
            make.logger.log(TRACE, "Deleted {0}", path);
          }
        } catch (Exception e) {
          make.logger.log(ERROR, "Deleting tree failed: " + root, e);
          return 1;
        }
        return 0;
      }
    }

    /** Walk directory tree structure. */
    class TreeWalk implements Action {

      final Path root;
      final Consumer<String> out;

      TreeWalk(Path root, Consumer<String> out) {
        this.root = root;
        this.out = out;
      }

      @Override
      public int run(Make make) {
        if (Files.exists(root)) {
          out.accept(root.toString());
        }
        try (var stream = Files.walk(root).sorted()) {
          for (var path : stream.collect(Collectors.toList())) {
            var string = root.relativize(path).toString();
            var prefix = string.isEmpty() ? "" : File.separator;
            out.accept("." + prefix + string);
          }
        } catch (Exception e) {
          // throw new UncheckedIOException("dumping tree failed: " + root, e);
          return 1;
        }
        return 0;
      }
    }
  }
}
