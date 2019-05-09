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
  class JigsawQuickStart {

    @Test
    void greetings(@TempDir Path work) {
      var home = Path.of("demo", "jigsaw-quick-start", "greetings");
      var main = new Make.Realm("main", Path.of("src"));
      var make = new Make(true, false, "greetings", "47.11", home, work, List.of(main));

      assertTrue(make.debug);
      assertFalse(make.dryRun);
      assertEquals("greetings", make.project);
      assertEquals("47.11", make.version);
      assertEquals(home, make.home);
      assertEquals(work, make.work.base);
      assertEquals(1, make.realms.size());

      assertTrue(Files.isDirectory(make.home.resolve(main.source).resolve("com.greetings")));

      var debug = DebugRun.newInstance();
      assertEquals(0, make.run(debug), debug.toString());

      var exploded = make.work.compiledModules.resolve("com.greetings");
      assertTrue(Files.isDirectory(exploded));
      assertTrue(Files.isRegularFile(exploded.resolve("module-info.class")));
      assertTrue(Files.isRegularFile(exploded.resolve("com/greetings/Main.class")));
      var jar = make.work.packagedModules.resolve("com.greetings@47.11.jar");
      assertTrue(Files.isRegularFile(jar), "file not found: " + jar);
      debug.tool("jar", "--describe-module", "--file", jar.toString());
      assertLinesMatch(
          List.of(
              "Make.java - " + Make.VERSION,
              "  args = []",
              "  java = " + Runtime.version(),
              "Building project 'greetings', version 47.11...",
              "  home = " + home.toUri(),
              "  work = " + work.toUri(),
              "  realms[0] = Realm{name=main, source=src}",
              ">> BUILD >>",
              "Build successful after \\d+ ms\\.",
              "Running tool 'jar' with: [--describe-module, --file, " + jar + "]",
              "com.greetings@47.11 jar:file:.+module-info.class",
              "requires java.base mandated",
              "contains com.greetings",
              "",
              "Tool 'jar' successfully executed."),
          debug.lines());
    }
  }
}
