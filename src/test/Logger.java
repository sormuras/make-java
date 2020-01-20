import java.lang.System.Logger.Level;

public class Logger implements Make.Logger {

  boolean verbose;

  Logger() {
    this(false);
  }

  Logger(boolean verbose) {
    this.verbose = verbose;
  }

  @Override
  public Make.Logger log(Level level, String format, Object... args) {
    if (verbose) System.out.println(String.format(format, args));
    return this;
  }
}
