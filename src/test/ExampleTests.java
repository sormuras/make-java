import static org.junit.jupiter.api.Assertions.assertSame;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ExampleTests {
  @Test
  void buildDefaultMainTest() {
    var logger = new Logger();
    var folder = Make.Folder.of(Path.of("doc/example/default-main-test"));
    var project = Make.Project.Builder.of(logger, folder).build();
    var planner = new Make.Tool.Planner();

    var make = new Make(logger, folder, project, planner);
    var actual = make.run();
    assertSame(make, actual);
  }
}
