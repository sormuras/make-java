import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ProjectTests {
  @Test
  void buildDocExampleDefaultMainTest() {
    var logger = new Logger();
    var base = Path.of("doc/example/default-main-test");
    var project = Make.Project.Builder.of(logger, base).build();
    assertEquals("default-main-test", project.name());
    assertEquals("1-ea", project.version().toString());
    new Make(logger, Make.Folder.of(base), project).run();
  }
}
