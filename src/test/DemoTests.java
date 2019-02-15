import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class DemoTests {
  private final CollectingLogger logger = new CollectingLogger("*");

  @Nested
  class JigsawQuickStart {

    @Test
    void greetings() {
      var base = Path.of("demo", "jigsaw-quick-start", "greetings");
      var make = new Make(logger, base, List.of());
      assertEquals(base, make.base);
      assertTrue(Files.isDirectory(make.based("src")));
      assertEquals("greetings", make.project.name);
      assertEquals("1.0.0-SNAPSHOT", make.project.version);
    }
  }
}
