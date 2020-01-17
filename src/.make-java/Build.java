// default package

import java.lang.System.Logger.Level;
import java.util.List;

/** Build program for this project. */
class Build {
  public static void main(String... args) {
    var logger = Make.Logger.ofSystem();
    logger.log(Level.TRACE, "Build.java");
    logger.log(Level.TRACE, "  args = %s", List.of(args));
    var project = new Make.Project("project", "1-ea");
    new Make(logger, project).run();
  }
}
