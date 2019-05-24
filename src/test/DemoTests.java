import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertLinesMatch;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DemoTests {

  @Nested
  class ClassPath {

    @Test
    void checkModules() {
      var make = Make.of(Path.of("demo", "class-path"));
      assertLinesMatch(List.of(), make.main.modules);
      assertLinesMatch(List.of(), make.test.modules);
    }

    @Test
    void run(@TempDir Path work) throws Exception {
      var home = Path.of("demo", "class-path");
      var properties = new Properties();
      properties.setProperty("debug", "true");
      properties.setProperty("work", work.toString());
      var configuration = new Make.Configuration(home, properties);
      var make = new Make(configuration);

      assertTrue(make.configuration.debug);
      assertFalse(make.configuration.dryRun);
      assertEquals("class-path", make.configuration.project.name);
      assertEquals("1.0.0-SNAPSHOT", make.configuration.project.version);
      assertTrue(Files.isSameFile(home, make.configuration.home));

      var run = new TestRun();
      assertEquals(0, make.run(run, List.of()), run.toString());

      var classes = make.main.classicalClasses;
      assertTrue(Files.isDirectory(classes));
      assertTrue(Files.isRegularFile(classes.resolve("com/greetings/Main.class")));

      assertLinesMatch(
          List.of(
              "main"
                  , "main/class-path-1.0.0-SNAPSHOT-sources.jar"
                  , "main/class-path-1.0.0-SNAPSHOT.jar"
                  , "main/classes"
                  , "main/classes/com"
                  , "main/classes/com/greetings"
                  , "main/classes/com/greetings/Main.class"
                  , "test"
                  , "test/classes"
                  , "test/classes/com"
                  , "test/classes/com/greetings"
                  , "test/classes/com/greetings/MainTests.class"
                  , "test/reports"
                  , "test/reports/TEST-junit-jupiter.xml"
                  , "test/reports/TEST-junit-vintage.xml"),
          TestRun.treeWalk(work));
//      var modularJar = main.packagedModules.resolve(module + "-" + version + ".jar");
//      var sourcesJar = main.packagedSources.resolve(module + "-" + version + "-sources.jar");
//      debug.tool("jar", "--describe-module", "--file", modularJar.toString());
//      debug.tool("jar", "--list", "--file", sourcesJar.toString());
      assertLinesMatch(
          List.of(
              "__BEGIN__",
              "Making class-path 1.0.0-SNAPSHOT...",
              "Make.java " + Make.VERSION,
              ">> BUILD >>",
              "Build successful after \\d+ ms\\."),
          run.normalLines());
    }

  }

  @Test
  void demoJigsawQuickStartGreetings() {
    var make = Make.of(Path.of("demo", "jigsaw-quick-start", "greetings"));
    assertLinesMatch(List.of("com.greetings"), make.main.modules);
    assertLinesMatch(List.of(), make.test.modules);
  }

  @Test
  void demoJigsawQuickStartGreetingsWorldWithMainAndTest() {
    var make = Make.of(Path.of("demo", "jigsaw-quick-start", "greetings-world-with-main-and-test"));
    assertLinesMatch(List.of("com.greetings", "org.astro"), make.main.modules);
    assertLinesMatch(List.of("integration", "org.astro"), make.test.modules);
  }
}
