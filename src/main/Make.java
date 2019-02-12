import java.nio.file.Path;

import static java.lang.System.Logger.Level.ERROR;
import static java.lang.System.Logger.Level.INFO;

class Make {

  static final String VERSION = "master";

  static final Path USER_PATH = Path.of(System.getProperty("user.dir"));

  public static void main(String... args) {
    var format = "java.util.logging.SimpleFormatter.format";
    if (System.getProperty(format) == null) {
      System.setProperty(format, "%1$tY-%1$tm-%1$td %1$tH:%1$tM:%1$tS %4$-6s %2$s %5$s%6$s%n");
    }
    var make = new Make();
    var code = make.run();
    if (code != 0) {
      throw new Error("Make.java failed with error code: " + code);
    }
  }

  final Path base;
  final System.Logger logger;

  Make() {
    this(System.getLogger("Make.java"), USER_PATH);
  }

  Make(System.Logger logger, Path base) {
    this.logger = logger;
    this.base = base;
  }

  int run() {
    logger.log(INFO, "Make.java - {0}", VERSION);
    if (base.getFileName() == null) {
      logger.log(ERROR, "Base path has zero elements!");
      return 1;
    }
    return 0;
  }
}
