import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertLinesMatch;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

class ModularWorldTests {

  /** https://github.com/sormuras/modular-world/releases */
  private static final String VERSION = "0.1";

  @TestFactory
  Stream<DynamicTest> buildModularWorld(@TempDir Path temp) throws Exception {
    // download and extract
    var uri = URI.create("https://github.com/sormuras/modular-world/archive/" + VERSION + ".zip");
    var zip = "modular-world-" + VERSION + ".zip";
    download(log -> {}, temp.resolve(zip), uri);
    var extract =
        new ProcessBuilder("jar", "--extract", "--file", zip).directory(temp.toFile()).start();
    assertEquals(0, extract.waitFor(), extract.toString());
    var homes = Make.Util.listDirectories(temp.resolve("modular-world-" + VERSION));
    assertEquals(3, homes.size());
    // build all modular projects
    return homes.stream()
        .map(home -> dynamicTest(home.getFileName().toString(), () -> build(home)));
  }

  private void build(Path home) throws Exception {
    var logger = new CollectingLogger("*");
    var project = home.getFileName().toString();
    var version = "0-TEST";
    var work = home.resolve("work");
    Files.createDirectories(work);
    var main = new Make.Realm("main", Path.of("src", "main"));

    var make = new Make(logger, false, project, version, home, work, List.of(main));
    assertEquals(
        0,
        make.run(System.out, System.err),
        logger.toString() + "\n\ntree\n" + String.join("\n|  ", treeWalk(home)));
    assertLinesMatch(
        List.of(
            "Make.java - " + Make.VERSION,
            "  args = []",
            String.format("Building %s %s", project, version),
            "  home = " + home.toUri(),
            "  work = " + work.toUri(),
            "  realms[0] = Realm{name=main, source=" + Path.of("src/main") + "}",
            ">> BUILD >>",
            "Build successful after \\d+ ms\\."),
        logger.getLines());
    // treeWalk(home).forEach(System.out::println);
  }

  static Path download(Consumer<String> logger, Path target, URI uri) throws Exception {
    logger.accept("download(" + uri + ")");
    var fileName = target.getFileName().toString();
    var url = uri.toURL();
    var connection = url.openConnection();
    try (var sourceStream = connection.getInputStream()) {
      var millis = connection.getLastModified();
      var lastModified = FileTime.fromMillis(millis == 0 ? System.currentTimeMillis() : millis);
      if (Files.exists(target)) {
        logger.accept("Local target file exists. Comparing last modified timestamps...");
        var fileModified = Files.getLastModifiedTime(target);
        logger.accept(" o Remote Last Modified -> " + lastModified);
        logger.accept(" o Target Last Modified -> " + fileModified);
        if (fileModified.equals(lastModified)) {
          logger.accept(String.format("Already downloaded %s previously.", fileName));
          return target;
        }
        logger.accept("Local target file differs from remote source -- replacing it...");
      }
      logger.accept("Transferring " + uri);
      try (var targetStream = Files.newOutputStream(target)) {
        sourceStream.transferTo(targetStream);
      }
      Files.setLastModifiedTime(target, lastModified);
      logger.accept(String.format(" o Remote   -> %s", uri));
      logger.accept(String.format(" o Target   -> %s", target.toUri()));
      logger.accept(String.format(" o Modified -> %s", lastModified));
      logger.accept(String.format(" o Size     -> %d bytes", Files.size(target)));
      logger.accept(String.format("Downloaded %s successfully.", fileName));
    }
    return target;
  }

  /** Walk directory tree structure. */
  static List<String> treeWalk(Path root) {
    var lines = new ArrayList<String>();
    treeWalk(root, lines::add);
    return lines;
  }

  /** Walk directory tree structure. */
  static void treeWalk(Path root, Consumer<String> out) {
    try (var stream = Files.walk(root)) {
      stream
          .map(root::relativize)
          .map(path -> path.toString().replace('\\', '/'))
          .sorted()
          .filter(Predicate.not(String::isEmpty))
          .forEach(out);
    } catch (Exception e) {
      throw new Error("Walking tree failed: " + root, e);
    }
  }
}
