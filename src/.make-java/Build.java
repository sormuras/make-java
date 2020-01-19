// default package

import java.lang.System.Logger.Level;
import java.nio.file.Path;
import java.util.List;

/** Build program for this project. */
class Build {
  public static void main(String... args) {
    var logger = Make.Logger.ofSystem(true).log(Level.DEBUG, "Build.java (args=%s)", List.of(args));
    var project = Make.Project.Builder.of(Path.of("")).setVersion("1-ea").build();
    new Make(logger, project).run();
  }
}
