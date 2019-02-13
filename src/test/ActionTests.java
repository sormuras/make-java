import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertLinesMatch;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ActionTests {

  private final CollectingLogger logger = new CollectingLogger("*");
  private final Make make = new Make(logger, Path.of("."), List.of());

  @Nested
  class Download {
    @Test
    void relativeUriThrows() {
      var down = new Make.Action.Download(Path.of("."), URI.create("void"));
      var code = make.run(down);
      assertEquals(1, code, logger.toString());
      assertTrue(logger.toString().contains("Download failed: URI is not absolute"));
    }

    @Test
    void https(@TempDir Path temp) throws Exception {
      var uri = URI.create("https://junit.org/junit5/index.html");
      var down = new Make.Action.Download(temp, uri);
      var code = make.run(down);
      assertEquals(0, code, logger.toString());
      var text = Files.readString(down.destination.resolve("index.html"));
      assertTrue(text.contains("<title>JUnit 5</title>"));
    }

    @Test
    void defaultFileSystem(@TempDir Path tempRoot) throws Exception {
      var content = List.of("Lorem", "ipsum", "dolor", "sit", "amet");
      var tempFile = Files.createFile(tempRoot.resolve("source.txt"));
      Files.write(tempFile, content);
      var tempPath = Files.createDirectory(tempRoot.resolve("target"));
      var name = tempFile.getFileName().toString();
      var actual = tempPath.resolve(name);

      var download = new Make.Action.Download(tempPath, tempFile.toUri());

      // initial download
      make.run(download);
      assertTrue(Files.exists(actual));
      assertLinesMatch(content, Files.readAllLines(actual));
      assertLinesMatch(
          List.of(
              "Running action Download...",
              "Downloading 1 file(s) to " + tempPath + "...",
              "Downloading " + tempFile.toUri() + "...",
              "Transferring " + tempFile.toUri() + "...",
              "Downloaded source.txt successfully.",
              " o Size -> .. bytes", // 32 on Windows, 27 on Linux/Mac
              " o Last Modified .+",
              "Action Download succeeded."),
          logger.getLines());

      // reload
      logger.clear();
      make.run(download);
      assertLinesMatch(
          List.of(
              "Running action Download...",
              "Downloading 1 file(s) to " + tempPath + "...",
              "Downloading " + tempFile.toUri() + "...",
              "Local file exists. Comparing attributes to remote file...",
              "Local and remote file attributes seem to match.",
              "Action Download succeeded."),
          logger.getLines());

      // offline mode
      logger.clear();
      make.var.offline = true;
      make.run(download);
      assertLinesMatch(
          List.of(
              "Running action Download...",
              "Downloading 1 file(s) to " + tempPath + "...",
              "Downloading " + tempFile.toUri() + "...",
              "Offline mode is active and target already exists.",
              "Action Download succeeded."),
          logger.getLines());

      // offline mode with error
      logger.clear();
      Files.delete(actual);
      assertEquals(1, make.run(download));
      assertLinesMatch(
          List.of(
              "Running action Download...",
              "Downloading 1 file(s) to " + tempPath + "...",
              "Downloading " + tempFile.toUri() + "...",
              "Download failed: Target is missing and being offline: " + actual,
              "Action Download failed with error code: 1"),
          logger.getLines());

      // online but different file
      logger.clear();
      make.var.offline = false;
      Files.write(actual, List.of("Hello world!"));
      make.run(new Make.Action.Download(tempPath, tempFile.toUri()));
      assertLinesMatch(content, Files.readAllLines(actual));
      assertLinesMatch(
          List.of(
              "Running action Download...",
              "Downloading 1 file(s) to " + tempPath + "...",
              "Downloading " + tempFile.toUri() + "...",
              "Local file exists. Comparing attributes to remote file...",
              "Local file differs from remote -- replacing it...",
              "Transferring " + tempFile.toUri() + "...",
              "Downloaded source.txt successfully.",
              " o Size -> .. bytes", // 32 on Windows, 27 on Linux/Mac
              " o Last Modified .+",
              "Action Download succeeded."),
          logger.getLines());
    }
  }
}
