import static java.lang.System.Logger.Level.INFO;

class Make {

  static final String VERSION = "master";

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

  final System.Logger logger;

  Make() {
    this(System.getLogger("Make.java"));
  }

  Make(System.Logger logger) {
    this.logger = logger;
  }

  int run() {
    logger.log(INFO, "Make.java - {0}", VERSION);
    return 0;
  }
}
