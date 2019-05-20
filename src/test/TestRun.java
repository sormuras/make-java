import java.io.PrintWriter;
import java.io.StringWriter;

class TestRun extends Make.Run {

  final StringWriter out;
  final StringWriter err;

  TestRun() {
    this(new StringWriter(), new StringWriter());
  }

  private TestRun(StringWriter out, StringWriter err) {
    super(System.Logger.Level.ALL, new PrintWriter(out), new PrintWriter(err));
    this.out = out;
    this.err = err;
  }

  @Override
  public String toString() {
    return "\n\n___out" + out + "\n\n___err" + err;
  }
}
