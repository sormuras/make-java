import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

class CollectingLogger implements System.Logger {

  private final List<String> lines = new ArrayList<>();

  List<String> getLines() {
    return lines;
  }

  @Override
  public String getName() {
    return "*";
  }

  @Override
  public boolean isLoggable(Level level) {
    return true;
  }

  @Override
  public void log(Level level, ResourceBundle bundle, String msg, Throwable thrown) {
    lines.add(level + ": " + msg);
  }

  @Override
  public void log(Level level, ResourceBundle bundle, String format, Object... params) {
    lines.add(level + ": " + MessageFormat.format(format, params));
  }
}
