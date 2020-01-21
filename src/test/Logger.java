import java.time.Instant;

public class Logger implements Make.Logger {

  private final boolean verbose;
  private final Instant start;

  Logger() {
    this(false);
  }

  Logger(boolean verbose) {
    this.verbose = verbose;
    this.start = Instant.now();
  }

  @Override
  public boolean verbose() {
    return verbose;
  }

  @Override
  public Make.Logger log(Entry entry) {
    if (verbose) System.out.println(entry.toString(start));
    return this;
  }
}
