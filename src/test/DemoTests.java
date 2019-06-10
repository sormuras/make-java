import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
  class ClassPath {

    Path home = Path.of("demo", "class-path");

    @Test
    void checkModules() {
      var make = Make.of(home);
      assertLinesMatch(List.of(), make.main.modules);
      assertLinesMatch(List.of(), make.test.modules);
    }

    @Test
    void run(@TempDir Path work) throws Exception {
      var run = new TestRun();
      var make = run.make(home, work);

      assertTrue(make.configuration.debug);
      assertFalse(make.configuration.dryRun);
      assertEquals("class-path", make.configuration.project.name);
      assertEquals("1.0.0-SNAPSHOT", make.configuration.project.version);
      assertTrue(Files.isSameFile(home, make.configuration.home));

      assertEquals(0, make.run(run, List.of()), run.toString());

      assertLinesMatch(
          List.of(
              "main",
              "main/class-path-1.0.0-SNAPSHOT-sources.jar",
              "main/class-path-1.0.0-SNAPSHOT.jar",
              "main/classes",
              "main/classes/com",
              "main/classes/com/greetings",
              "main/classes/com/greetings/Main.class",
              "test",
              "test/classes",
              "test/classes/com",
              "test/classes/com/greetings",
              "test/classes/com/greetings/MainTests.class",
              "test/reports",
              "test/reports/TEST-junit-jupiter.xml",
              "test/reports/TEST-junit-vintage.xml"),
          TestRun.treeWalk(work));

      assertLinesMatch(
          List.of(
              "__BEGIN__",
              "Making class-path 1.0.0-SNAPSHOT...",
              "Make.java " + Make.VERSION,
              ">> BUILD >>",
              "[         2 tests successful      ]",
              "[         0 tests failed          ]",
              ">> SUMMARY >>",
              "Build successful after \\d+ ms\\."),
          run.normalLines());

      assertLinesMatch(List.of(), run.errorLines());
    }
  }

  @Nested
  class JigsawGreetings {

    Path home = Path.of("demo", "jigsaw-greetings");

    @Test
    void checkModules() {
      var make = Make.of(home);
      assertLinesMatch(List.of("com.greetings"), make.main.modules);
      assertLinesMatch(List.of(), make.test.modules);
    }

    @Test
    void run(@TempDir Path work) {
      var run = new TestRun().run(0, home, work);
      assertLinesMatch(
          List.of(
              "__BEGIN__",
              "Making " + home.getFileName() + " 1.0.0-SNAPSHOT...",
              ">> BUILD >>",
              "__END.__",
              "Build successful after \\d+ ms\\."),
          run.normalLines());

      assertLinesMatch(List.of(), run.errorLines());
    }
  }

  @Nested
  class JigsawWorld {

    Path home = Path.of("demo", "jigsaw-world");

    @Test
    void checkModules() {
      var make = Make.of(home);
      assertLinesMatch(List.of("com.greetings", "org.astro"), make.main.modules);
      assertLinesMatch(List.of("integration", "org.astro"), make.test.modules);
    }

    @Test
    void run(@TempDir Path work) {
      var run = new TestRun().run(0, home, work);

      assertLinesMatch(
          List.of(
              "main",
              "main/compiled",
              "main/compiled/javadoc",
              ">> JAVADOC >>",
              "main/compiled/modules",
              "main/compiled/modules/com.greetings",
              "main/compiled/modules/com.greetings/com",
              "main/compiled/modules/com.greetings/com/greetings",
              "main/compiled/modules/com.greetings/com/greetings/Main.class",
              "main/compiled/modules/com.greetings/module-info.class",
              "main/compiled/modules/org.astro",
              "main/compiled/modules/org.astro/module-info.class",
              "main/compiled/modules/org.astro/org",
              "main/compiled/modules/org.astro/org/astro",
              "main/compiled/modules/org.astro/org/astro/World.class",
              "main/javadoc",
              "main/javadoc/jigsaw-world-1.0.0-SNAPSHOT-javadoc.jar",
              "main/modules",
              "main/modules/com.greetings-1.0.0-SNAPSHOT.jar",
              "main/modules/org.astro-1.0.0-SNAPSHOT.jar",
              "main/sources",
              "main/sources/com.greetings-1.0.0-SNAPSHOT-sources.jar",
              "main/sources/org.astro-1.0.0-SNAPSHOT-sources.jar",
              "test",
              "test/compiled",
              "test/compiled/modules",
              "test/compiled/modules/integration",
              "test/compiled/modules/integration/integration",
              "test/compiled/modules/integration/integration/IntegrationTests.class",
              "test/compiled/modules/integration/integration/MainTests.class",
              "test/compiled/modules/integration/module-info.class",
              "test/compiled/modules/org.astro",
              "test/compiled/modules/org.astro/module-info.class",
              "test/compiled/modules/org.astro/org",
              "test/compiled/modules/org.astro/org/astro",
              "test/compiled/modules/org.astro/org/astro/World.class",
              "test/compiled/modules/org.astro/org/astro/WorldTests.class",
              "test/junit-reports",
              "test/junit-reports/TEST-junit-jupiter.xml",
              "test/junit-reports/TEST-mainrunner.xml"),
          TestRun.treeWalk(work));

      assertLinesMatch(
          List.of(
              "__BEGIN__",
              "Making " + home.getFileName() + " 1.0.0-SNAPSHOT...",
              "Make.java " + Make.VERSION,
              ">> BUILD >>",
              "[         5 tests successful      ]",
              "[         0 tests failed          ]",
              ">> SUMMARY >>",
              "Build successful after \\d+ ms\\."),
          run.normalLines());

      assertLinesMatch(List.of(), run.errorLines());
    }
  }
}
