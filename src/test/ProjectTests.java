import java.nio.file.Path;
import java.util.List;
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
            .setMain(
                new Make.Project.Realm.Builder("main")
                    .setModules(List.of("org.foo", "org.foo.bar"))
                    .build())
            .build();

    new Make(logger, Make.Folder.of(base), project, new Make.Tool.Planner()).run();
  }
}
