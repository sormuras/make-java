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
    void greetings(@TempDir Path work) {
      var logger = new CollectingLogger("*");
      var home = Path.of("demo", "jigsaw-quick-start", "greetings");
      var main = new Make.Realm("main", Path.of("src"), List.of("com.greetings"));
      var realms = List.of(main);
      var make = new Make(logger, false, "greetings", "47.11", home, work, realms);

      assertSame(logger, make.logger);
      assertFalse(make.dryRun);
      assertEquals("greetings", make.project);
      assertEquals("47.11", make.version);
      assertEquals(home, make.home);
      assertEquals(work, make.work);

      assertTrue(Files.isDirectory(make.home.resolve(main.root).resolve("com.greetings")));
      assertEquals(0, make.run(System.out, System.err), logger.toString());
      assertLinesMatch(
          List.of(
              "Make.java - " + Make.VERSION,
              "  args = []",
              "Building greetings 47.11",
              " home = " + home.toUri(),
              " work = " + work.toUri(),
              " realms[0] = Realm{name='main', root=src, modules=[com.greetings]}",
              ">> BUILD >>",
              "Build successful after \\d+ ms\\."),
          logger.getLines());

      var exploded = make.work.resolve("com.greetings");
      assertTrue(Files.isDirectory(exploded));
      assertTrue(Files.isRegularFile(exploded.resolve("module-info.class")));
      assertTrue(Files.isRegularFile(exploded.resolve("com/greetings/Main.class")));
    }
  }
}
