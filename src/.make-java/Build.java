// default package

/**
 * Build program for this project.
 */
class Build {
  public static void main(String... args) {
    var project = new Make.Project("project", "1-ea");
    new Make().make(project);
  }
}
