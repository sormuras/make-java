import java.lang.System.Logger.Level;

public class Logger implements Make.Logger {
  @Override
  public Make.Logger log(Level level, String format, Object... args) {
    return this;
  }
}
