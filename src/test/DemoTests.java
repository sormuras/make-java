import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertLinesMatch;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DemoTests {
  private final CollectingLogger logger = new CollectingLogger("*");
  private final Make make = new Make(logger, Path.of("."), List.of());

  @Nested
  class JigsawQuickStart {

    @Test
    void greetings(@TempDir Path workspace) {
      var demo = Path.of("demo", "jigsaw-quick-start", "greetings");
      var base = workspace.resolve(demo.getFileName());
      make.run(new Make.Action.TreeCopy(demo, base));

      var logger = new CollectingLogger("*");
      var make = new Make(logger, base, List.of());
      assertEquals(base, make.base);
      assertTrue(Files.isDirectory(make.based("src")));
      assertEquals("greetings", make.project.name);
      assertEquals("1.0.0-SNAPSHOT", make.project.version);
      assertEquals(0, make.run());
      assertLinesMatch(
          List.of(
              "Running action Banner...",
              "Make.java - " + Make.VERSION,
              "Action Banner succeeded.",
              "Running action Check...",
              "Action Check succeeded."),
          logger.getLines());
    }
  }
}
