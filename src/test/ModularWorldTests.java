import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertLinesMatch;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModularWorldTests {

  @Test
  void test(@TempDir Path temp) throws Exception {
    // download and extract
    var uri = URI.create("https://github.com/sormuras/modular-world/archive/master.zip");
    var zip = "modular-world-master.zip";
    download(line -> {}, temp.resolve(zip), uri);
    new ProcessBuilder("jar", "--extract", "--file", zip)
        .directory(temp.toFile())
        .start()
        .waitFor();
    assertLinesMatch(
        List.of("000-a", "001-abc", "010-mr-a"),
        Make.Util.listDirectoryNames(temp.resolve("modular-world-master")));
    // build and run each
    // compare expected to actual results
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
}
