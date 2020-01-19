import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ProjectTests {
  @Test
  void buildDocExampleDefaultMainTest() {
    var base = Path.of("doc/example/default-main-test");

    var logger = new Logger();
    var project =
        Make.Project.Builder.of(logger, base)
            .setName("foo")
            .setVersion("1")
            //.setMainModules("org.foo", "org.foo.bar")
            //.setMainModuleSourcePath("src/${MODULE}/main/java")
            .build();

    var folder = Make.Folder.of(base);
    var compile =
        Make.Tool.Plan.of(
            "Compile",
            false,
            Make.Tool.Call.of(
                "javac",
                "-d",
                folder.out("classes/main").toString(),
                "--module-source-path",
                folder.src("${MODULE}/main/java").toString().replace("${MODULE}", "*"),
                "--module",
                "org.foo,org.foo.bar"));
    new Make(logger, folder, project).run().run(compile);
  }
}
