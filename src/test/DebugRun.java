import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.util.List;
import java.util.stream.Collectors;

class DebugRun extends Make.Run {

  static DebugRun newInstance() {
    return new DebugRun(System.Logger.Level.ALL, new StringWriter());
  }

  private final Writer writer;

  private DebugRun(System.Logger.Level threshold, Writer writer) {
    super(threshold, new PrintWriter(writer), new PrintWriter(writer));
    this.writer = writer;
  }

  List<String> lines() {
    return writer.toString().lines().collect(Collectors.toList());
  }

  @Override
  public String toString() {
    return "\n..." + writer + "\n°°°\n";
  }
}
