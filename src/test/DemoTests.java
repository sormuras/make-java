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
      var home = Path.of("demo", "jigsaw-quick-start", "greetings");
      var project = new Make.Project(home, work, "greetings", "47.11");
      var builder = new Make.Builder();
      var logger = new CollectingLogger("*");
      var make = new Make(logger, false, project, builder);

      // assertEquals(demo, make.base);
      // assertEquals(work, make.work);
      assertTrue(Files.isDirectory(project.home.resolve("src/com.greetings")));
      assertEquals("greetings", make.project.name);
      assertEquals("47.11", make.project.version);
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
