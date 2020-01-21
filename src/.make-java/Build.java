// default package

import java.lang.System.Logger.Level;
import java.util.List;

/** Build program for this project. */
class Build {
  public static void main(String... args) {
    var logger = Make.Logger.ofSystem(true).log(Level.INFO, "Build.java (args=%s)", List.of(args));
    var folder = Make.Folder.ofCurrentWorkingDirectory();
    var project = Make.Project.Builder.of(logger, folder).setVersion("1-ea").build();
    var plan = Make.Tool.Plan.of(logger, folder, project);

    new Make(logger, folder, project, plan).run();
  }
}
