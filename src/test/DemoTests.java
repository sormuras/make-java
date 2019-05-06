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

  @Nested
  class JigsawQuickStart {

    @Test
    void greetings(@TempDir Path work) throws Exception {
      var logger = new CollectingLogger("*");
      var demo = Path.of("demo", "jigsaw-quick-start", "greetings");
      var make = new Make(logger, demo, work, false, List.of("clean", "build"));

      Files.createDirectories(work.resolve("target"));

      assertEquals(demo, make.base);
      assertEquals(work, make.work);
      assertTrue(Files.isDirectory(make.base.resolve("src")));
      // assertEquals("greetings", make.project.name);
      // assertEquals("1.0.0-SNAPSHOT", make.project.version);
      assertEquals(0, make.run(), logger.toString());
      assertLinesMatch(
          List.of(
              "Make.java - " + Make.VERSION,
              "run(action=Check)",
              "Check succeeded.",
              "run(action=TreeDelete)",
              ">> CLEAN >>",
              "TreeDelete succeeded.",
              "run(action=Build)",
              ">> BUILD >>",
              "Build succeeded."),
          logger.getLines());
    }
  }
}
