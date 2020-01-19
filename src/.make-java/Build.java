// default package

import java.lang.System.Logger.Level;
import java.nio.file.Path;
import java.util.List;

/** Build program for this project. */
class Build {
  public static void main(String... args) {
    var base = Path.of("");
    var logger = Make.Logger.ofSystem(true).log(Level.DEBUG, "Build.java (args=%s)", List.of(args));
    var project = Make.Project.Builder.of(logger, base).setVersion("1-ea").build();
    new Make(logger, Make.Folder.of(base), project).run();
  }
}
