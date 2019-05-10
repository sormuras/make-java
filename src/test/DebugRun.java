import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

class DebugRun extends Make.Run {

  static DebugRun newInstance() {
    return new DebugRun(System.Logger.Level.ALL, new StringWriter());
  }

  /** Walk directory tree structure. */
  static List<String> treeWalk(Path root) {
    var lines = new ArrayList<String>();
    treeWalk(root, lines::add);
    return lines;
  }

  /** Walk directory tree structure. */
  static void treeWalk(Path root, Consumer<String> out) {
    try (var stream = Files.walk(root)) {
      stream
          .map(root::relativize)
          .map(path -> path.toString().replace('\\', '/'))
          .sorted()
          .filter(Predicate.not(String::isEmpty))
          .forEach(out);
    } catch (Exception e) {
      throw new Error("Walking tree failed: " + root, e);
    }
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
    return "\n...\n" + writer + "\n°°°\n";
  }
}
