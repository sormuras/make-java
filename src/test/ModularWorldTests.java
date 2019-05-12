import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertLinesMatch;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

class ModularWorldTests {

  /** https://github.com/sormuras/modular-world/releases */
  private static final String VERSION = "0.3";

  @TestFactory
  Stream<DynamicTest> buildModularWorld(@TempDir Path temp) throws Exception {
    // download and extract
    var uri = URI.create("https://github.com/sormuras/modular-world/archive/" + VERSION + ".zip");
    var zip = Make.Util.download(Boolean.getBoolean("offline"), temp, uri);
    var extract =
        new ProcessBuilder("jar", "--extract", "--file", zip.getFileName().toString())
            .directory(temp.toFile())
            .inheritIO()
            .start();
    assertEquals(0, extract.waitFor(), extract.toString());
    var homes = Make.Util.listDirectories(temp.resolve("modular-world-" + VERSION));
    assertEquals(5, homes.size());
    // build all modular projects
    return homes.stream()
        .map(home -> dynamicTest(home.getFileName().toString(), () -> build(home)));
  }

  private void build(Path home) throws Exception {
    var project = home.getFileName().toString();
    var version = "0-TEST";
    var main = Make.Realm.of("main", home, home.resolve("work"));

    var debug = DebugRun.newInstance();
    var make = new Make(true, false, project, version, home, List.of(main));
    assertEquals(0, make.run(debug), debug + "\n" + String.join("\n", DebugRun.treeWalk(home)));
    var expectedLines = new ArrayList<>(List.of("Make.java - " + Make.VERSION, ">> BUILD >>"));
    expectedLines.add("Running tool 'jdeps' with.+");
    expectedLines.addAll(Files.readAllLines(home.resolve("jdeps-summary.txt")));
    expectedLines.add("Tool 'jdeps' successfully executed.");
    expectedLines.add("Build successful after \\d+ ms\\.");
    assertLinesMatch(expectedLines, debug.lines());
  }
}
