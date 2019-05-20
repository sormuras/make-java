import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.spi.ToolProvider;

/** Modular project model and maker. */
class Make implements ToolProvider {

  /** Version is either {@code master} or {@link Runtime.Version#parse(String)}-compatible. */
  static final String VERSION = "master";

  /** Convenient short-cut to {@code "user.dir"} as a path. */
  static final Path USER_PATH = Path.of(System.getProperty("user.dir"));

  /** Main entry-point. */
  public static void main(String... args) {
    var code = Make.of(USER_PATH).run(System.out, System.err, args);
    if (code != 0) {
      throw new Error("Make.java failed with error code: " + code);
    }
  }

  /** Create instance using the given path as the home of project. */
  static Make of(Path home) {
    var debug = Boolean.getBoolean("ebug");
    var dryRun = Boolean.getBoolean("ry-run");
    var project = System.getProperty("project.name", home.getFileName().toString());
    var version = System.getProperty("project.version", "1.0.0-SNAPSHOT");
    var work = home.resolve("work");
    var main = Realm.of("main", home, work);
    var realms = new ArrayList<Realm>();
    realms.add(main);
    try {
      realms.add(Realm.of("test", home, work, main));
    } catch (Error e) {
      // ignore missing test realm...
    }
    return new Make(debug, dryRun, project, version, home, realms);
  }

  @Override
  public String name() {
    return "Make.java";
  }

  @Override
  public int run(PrintWriter out, PrintWriter err, String... args) {
    var run = new Run(debug ? System.Logger.Level.ALL : System.Logger.Level.INFO, out, err);
    return run(run, args);
  }
}
