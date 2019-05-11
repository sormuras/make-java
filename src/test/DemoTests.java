import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertLinesMatch;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
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
      var home = Path.of("demo", "jigsaw-quick-start", "greetings");
      var main = new Make.Realm("main", Path.of("src"), work, List.of());
      var make = new Make(true, false, "greetings", "47.11", home, List.of(main));

      assertTrue(make.debug);
      assertFalse(make.dryRun);
      assertEquals("greetings", make.project);
      assertEquals("47.11", make.version);
      assertEquals(home, make.home);
      assertEquals(1, make.realms.size());

      assertTrue(Files.isDirectory(make.home.resolve(main.source).resolve("com.greetings")));

      var debug = DebugRun.newInstance();
      assertEquals(0, make.run(debug), debug.toString());

      var exploded = main.compiledModules.resolve("com.greetings");
      assertTrue(Files.isDirectory(exploded));
      assertTrue(Files.isRegularFile(exploded.resolve("module-info.class")));
      assertTrue(Files.isRegularFile(exploded.resolve("com/greetings/Main.class")));

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
              "main/modules",
              "main/modules/com.greetings-47.11.jar",
              "main/sources",
              "main/sources/com.greetings-47.11-sources.jar"),
          DebugRun.treeWalk(work));
      var modularJar = main.packagedModules.resolve("com.greetings-47.11.jar");
      var sourcesJar = main.packagedSources.resolve("com.greetings-47.11-sources.jar");

      debug.tool("jar", "--describe-module", "--file", modularJar.toString());
      debug.tool("jar", "--list", "--file", sourcesJar.toString());
      assertLinesMatch(
          List.of(
              "Make.java - " + Make.VERSION,
              "  args = []",
              "  java = " + Runtime.version(),
              "Building project 'greetings', version 47.11...",
              "  home = " + home.toUri(),
              "  realms[0] = Realm{name=main, source=src}",
              ">> BUILD >>",
              "Build successful after \\d+ ms\\.",
              "Running tool 'jar' with: [--describe-module, --file, " + modularJar + "]",
              "com.greetings@47.11 jar:file:.+module-info.class",
              "requires java.base mandated",
              "contains com.greetings",
              "",
              "Tool 'jar' successfully executed.",
              "Running tool 'jar' with: [--list, --file, " + sourcesJar + "]",
              "META-INF/",
              "META-INF/MANIFEST.MF",
              "com/",
              "com/greetings/",
              "com/greetings/Main.java",
              "module-info.java",
              "Tool 'jar' successfully executed."),
          debug.lines());
    }

    @Test
    void greetingsWorldWithMainAndTest(@TempDir Path work) {
      var home = Path.of("demo", "jigsaw-quick-start", "greetings-world-with-main-and-test");
      var main = Make.Realm.of("main", home, work);
      var test = Make.Realm.of("test", home, work, main);
      var make = new Make(true, false, "GWwMaT", "0", home, List.of(main, test));
      var debug = DebugRun.newInstance();
      assertEquals(0, make.run(debug), debug.toString());
      assertLinesMatch(
          List.of(
              "Make.java - " + Make.VERSION,
              "  args = []",
              "  java = " + Runtime.version(),
              "Building project 'GWwMaT', version 0...",
              "  home = " + home.toUri(),
              "  realms[0] = Realm{name=main, source=src" + File.separator + "main}",
              "  realms[1] = Realm{name=test, source=src" + File.separator + "test}",
              ">> BUILD >>",
              "JUnit.+",
              ">> TEST + SUMMARY >>",
              "[         3 tests successful      ]",
              "[         0 tests failed          ]",
              "",
              "Build successful after \\d+ ms\\."),
          debug.lines());
    }
  }
}
