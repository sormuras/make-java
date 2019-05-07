import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertLinesMatch;
import static org.junit.jupiter.api.Assertions.assertSame;
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
      var home = Path.of("demo", "jigsaw-quick-start", "greetings");
      var make = new Make(logger, false, "greetings", "47.11", home, work);

      assertSame(logger, make.logger);
      assertFalse(make.dryRun);
      assertEquals("greetings", make.project);
      assertEquals("47.11", make.version);
      assertEquals(home, make.home);
      assertEquals(work, make.work);

      assertTrue(Files.isDirectory(make.home.resolve("src/com.greetings")));
      assertEquals(0, make.run(System.out, System.err), logger.toString());
      assertLinesMatch(
          List.of(
              "Make.java - " + Make.VERSION,
              "  args = []",
              "Building greetings 47.11",
              " home = " + home.toUri(),
              " work = " + work.toUri()),
          logger.getLines());
    }
  }
}
