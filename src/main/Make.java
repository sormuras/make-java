import static java.lang.System.Logger.Level.DEBUG;
import static java.lang.System.Logger.Level.ERROR;
import static java.lang.System.Logger.Level.INFO;
import static java.lang.System.Logger.Level.WARNING;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
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
  final Variables var;

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
      logger.log(DEBUG, "Action {0} succeeded.", action.name());
    }
    return 0;
  }

  /** Variable state holder. */
  class Variables {
    boolean offline = false;
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
        }
        catch (Exception e) {
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
          make.logger.log(DEBUG, " o Last Modified -> {0}",  urlLastModifiedTime);
        }
      }
    }
  }
}
