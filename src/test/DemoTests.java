import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertLinesMatch;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DemoTests {

  @Nested
  class JigsawQuickStart {

    @Test
    void greetings(@TempDir Path work) throws Exception {
      var name = "greetings";
      var module = "com.greetings";
      var version = "2";

      var home = Path.of("demo", "jigsaw-quick-start", name);
      Map<String, List<Path>> libs = Map.of("compile", List.of(), "runtime", List.of());
      var main = new Make.Realm("main", Path.of("src"), List.of(module), work, libs, Map.of());
      var make = new Make(true, false, name, version, home, List.of(main));

      assertTrue(make.debug);
      assertFalse(make.dryRun);
      assertEquals(name, make.project);
      assertEquals(version, make.version);
      assertTrue(Files.isSameFile(home, make.home));
      assertEquals(1, make.realms.size());

      assertTrue(Files.isDirectory(make.home.resolve(main.source).resolve(module)));

      var debug = DebugRun.newInstance();
      assertEquals(0, make.run(debug), debug.toString());

      var exploded = main.compiledModules.resolve(module);
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
              "main/javadoc",
              "main/javadoc/" + name + "-" + version + "-javadoc.jar",
              "main/modules",
              "main/modules/" + module + "-" + version + ".jar",
              "main/sources",
              "main/sources/" + module + "-" + version + "-sources.jar"),
          DebugRun.treeWalk(work));
      var modularJar = main.packagedModules.resolve(module + "-" + version + ".jar");
      var sourcesJar = main.packagedSources.resolve(module + "-" + version + "-sources.jar");

      debug.tool("jar", "--describe-module", "--file", modularJar.toString());
      debug.tool("jar", "--list", "--file", sourcesJar.toString());
      assertLinesMatch(
          List.of(
              "Make.java - " + Make.VERSION,
              "  args = []",
              "  java = " + Runtime.version(),
              "Building project '" + name + "', version " + version + "...",
              "  home = " + home.toUri(),
              "  realms[0] = Realm{name=main, source=src}",
              ">> BUILD >>",
              "Build successful after \\d+ ms\\.",
              "Running tool 'jar' with: [--describe-module, --file, " + modularJar + "]",
              module + "@" + version + " jar:file:.+module-info.class",
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
    void greetingsWorldWithMainAndTest() throws Exception {
      var work = Files.createTempDirectory("make-java-demo-GWwMaT-"); // jars are locked!

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
              ">> TEST >>",
              "[         4 tests successful      ]", // TODO Release MAINRUNNER 1.2.0
              "[         0 tests failed          ]",
              "",
              ">> DOCUMENT >>",
              "Build successful after \\d+ ms\\."),
          debug.lines());
    }
  }
}
