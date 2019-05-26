import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertLinesMatch;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.TestFactory;

class ModularWorldTests {

  /** https://github.com/sormuras/modular-world/releases */
  private static final String VERSION = "0.4";

  @TestFactory
  Stream<DynamicTest> buildModularWorld(@TempDir Path temp) throws Exception {
    // download and extract
    var uri = URI.create("https://github.com/sormuras/modular-world/archive/" + VERSION + ".zip");
    var zip = new Make.Downloader(temp, Boolean.getBoolean("offline")).transfer(uri);
    var extract =
        new ProcessBuilder("jar", "--extract", "--file", zip.getFileName().toString())
            .directory(temp.toFile())
            .inheritIO()
            .start();
    assertEquals(0, extract.waitFor(), extract.toString());
    var homes = Make.Util.listDirectories(temp.resolve("modular-world-" + VERSION));
    assertEquals(5, homes.size());
    // build all modular projects
    return homes.stream().map(this::newDynamicTest);
  }

  private DynamicTest newDynamicTest(Path home) {
    var name = home.getFileName().toString();
    return dynamicTest(name, () -> build(home, name));
  }

  private void build(Path home, String name) throws Exception {
    var run = new TestRun().run(0, home, home.resolve("target"));

    assertLinesMatch(
        List.of(
            "__BEGIN__",
            "Making " + name + " 1.0.0-SNAPSHOT...",
            ">> BUILD >>",
            "__END.__",
            "Build successful after \\d+ ms\\."),
        run.normalLines());

    // assertLinesMatch(List.of(), run.errorLines());

    var expectedLines = Files.readAllLines(home.resolve("jdeps-summary.txt"));
    assertTrue(run.normalLines().containsAll(expectedLines));
  }
}
