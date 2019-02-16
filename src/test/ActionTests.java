import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertLinesMatch;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.OS;
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

  @Nested
  class Tool {

    @Test
    void failsOnNonExistentTool() {
      var tool = new Make.Action.Tool("does not exist", "really");
      var code = make.run(tool);
      var log = logger.toString();
      assertEquals(1, code, log);
      assertTrue(log.contains("does not exist"), log);
      assertTrue(log.contains("Running tool failed:"), log);
    }

    @Test
    void standardIO() {
      var out = new StringBuilder();
      var tool = new Make.Action.Tool("java", "--version");
      make.var.out = out::append;
      var code = make.run(tool);
      var log = logger.toString();
      assertEquals(0, code, log);
      assertTrue(out.toString().contains(Runtime.version().toString()), out.toString());
    }

    @Test
    void java() {
      var tool = new Make.Action.Tool("java", "--version");
      var code = make.run(tool);
      var log = logger.toString();
      assertEquals(0, code, log);
      assertTrue(log.contains(Runtime.version().toString()), log);
    }

    @Test
    void javac() {
      var tool = new Make.Action.Tool("javac", "--version");
      var code = make.run(tool);
      var log = logger.toString();
      assertEquals(0, code, log);
      assertTrue(log.contains("javac " + Runtime.version().feature()), log);
    }

    @Test
    void javadoc() {
      var tool = new Make.Action.Tool(new Make.Command("javadoc").add("--version"));
      var code = make.run(tool);
      var log = logger.toString();
      assertEquals(0, code, log);
      assertTrue(log.contains("javadoc " + Runtime.version().feature()), log);
    }
  }

  @Nested
  class Trees {

    @Test
    void tree() throws Exception {
      Path root = Files.createTempDirectory("tree-root-");
      assertTrue(Files.exists(root));
      assertEquals(1, Files.walk(root).count());
      assertTreeWalkMatches(root, root.toString(), ".");

      createFiles(root, 3);
      assertEquals(1 + 3, Files.walk(root).count());
      assertTreeWalkMatches(root, root.toString(), ".", "./file-0", "./file-1", "./file-2");

      createFiles(Files.createDirectory(root.resolve("a")), 3);
      createFiles(Files.createDirectory(root.resolve("b")), 3);
      createFiles(Files.createDirectory(root.resolve("x")), 4);
      assertTrue(Files.exists(root));
      assertTreeWalkMatches(
          root,
          root.toString(),
          ".",
          "./a",
          "./a/file-0",
          "./a/file-1",
          "./a/file-2",
          "./b",
          "./b/file-0",
          "./b/file-1",
          "./b/file-2",
          "./file-0",
          "./file-1",
          "./file-2",
          "./x",
          "./x/file-0",
          "./x/file-1",
          "./x/file-2",
          "./x/file-3");

      assertEquals(
          0,
          make.run(new Make.Action.TreeDelete(root, path -> path.startsWith(root.resolve("b")))));
      assertTreeWalkMatches(
          root,
          root.toString(),
          ".",
          "./a",
          "./a/file-0",
          "./a/file-1",
          "./a/file-2",
          "./file-0",
          "./file-1",
          "./file-2",
          "./x",
          "./x/file-0",
          "./x/file-1",
          "./x/file-2",
          "./x/file-3");

      assertEquals(0, make.run(new Make.Action.TreeDelete(root, path -> path.endsWith("file-0"))));
      assertTreeWalkMatches(
          root,
          root.toString(),
          ".",
          "./a",
          "./a/file-1",
          "./a/file-2",
          "./file-1",
          "./file-2",
          "./x",
          "./x/file-1",
          "./x/file-2",
          "./x/file-3");

      assertEquals(0, make.run(new Make.Action.TreeCopy(root.resolve("x"), root.resolve("a/b/c"))));
      assertTreeWalkMatches(
          root,
          root.toString(),
          ".",
          "./a",
          "./a/b",
          "./a/b/c",
          "./a/b/c/file-1",
          "./a/b/c/file-2",
          "./a/b/c/file-3",
          "./a/file-1",
          "./a/file-2",
          "./file-1",
          "./file-2",
          "./x",
          "./x/file-1",
          "./x/file-2",
          "./x/file-3");

      assertEquals(0, make.run(new Make.Action.TreeCopy(root.resolve("x"), root.resolve("x/y"))));
      assertTreeWalkMatches(
          root,
          root.toString(),
          ".",
          "./a",
          "./a/b",
          "./a/b/c",
          "./a/b/c/file-1",
          "./a/b/c/file-2",
          "./a/b/c/file-3",
          "./a/file-1",
          "./a/file-2",
          "./file-1",
          "./file-2",
          "./x",
          "./x/file-1",
          "./x/file-2",
          "./x/file-3",
          "./x/y",
          "./x/y/file-1",
          "./x/y/file-2",
          "./x/y/file-3");

      assertEquals(0, make.run(new Make.Action.TreeDelete(root)));
      assertTrue(Files.notExists(root));
    }

    @Test
    void copyNonExistingDoesNotFail() {
      var root = Path.of("does not exist");
      var copy = new Make.Action.TreeCopy(root, Path.of("."));
      assertEquals(0, make.run(copy));
    }

    @Test
    void copyAndItsPreconditions(@TempDir Path temp) throws Exception {
      var regular = createFiles(temp, 2).get(0);
      assertEquals(1, make.run(new Make.Action.TreeCopy(regular, Path.of("."))));
      var directory = Files.createDirectory(temp.resolve("directory"));
      createFiles(directory, 3);
      assertEquals(2, make.run(new Make.Action.TreeCopy(directory, regular)));
      assertEquals(0, make.run(new Make.Action.TreeCopy(directory, directory)));
      assertEquals(3, make.run(new Make.Action.TreeCopy(temp, directory)));
      var forbidden = Files.createDirectory(temp.resolve("forbidden"));
      try {
        denyListing(forbidden, false, false, true);
        assertEquals(4, make.run(new Make.Action.TreeCopy(directory, forbidden)));
      } finally {
        make.run(new Make.Action.TreeDelete(forbidden));
      }
    }

    @Test
    void deleteEmptyDirectory() throws Exception {
      var empty = Files.createTempDirectory("deleteEmptyDirectory");
      assertTrue(Files.exists(empty));
      make.run(new Make.Action.TreeDelete(empty));
      assertFalse(Files.exists(empty));
    }

    @Test
    void deleteFailsForNonExistingPath() {
      var root = Path.of("does not exist");
      var delete = new Make.Action.TreeDelete(root);
      assertEquals(1, make.run(delete));
    }

    @Test
    void walkFailsForNonExistingPath() {
      var root = Path.of("does not exist");
      var walk = new Make.Action.TreeWalk(root, System.out::println);
      assertEquals(1, make.run(walk));
    }

    private List<Path> createFiles(Path directory, int count) throws Exception {
      var paths = new ArrayList<Path>();
      for (int i = 0; i < count; i++) {
        paths.add(Files.createFile(directory.resolve("file-" + i)));
      }
      return paths;
    }

    private void denyListing(Path path, boolean r, boolean w, boolean x) throws Exception {
      if (OS.WINDOWS.isCurrentOs()) {
        var upls = path.getFileSystem().getUserPrincipalLookupService();
        var user = upls.lookupPrincipalByName(System.getProperty("user.name"));
        var builder = AclEntry.newBuilder();
        var permissions =
            EnumSet.of(
                // AclEntryPermission.EXECUTE, // "x"
                // AclEntryPermission.READ_DATA, // "r"
                AclEntryPermission.READ_ATTRIBUTES,
                AclEntryPermission.READ_NAMED_ATTRS,
                // AclEntryPermission.WRITE_DATA, // "w"
                // AclEntryPermission.APPEND_DATA, // "w"
                AclEntryPermission.WRITE_ATTRIBUTES,
                AclEntryPermission.WRITE_NAMED_ATTRS,
                AclEntryPermission.DELETE_CHILD,
                AclEntryPermission.DELETE,
                AclEntryPermission.READ_ACL,
                AclEntryPermission.WRITE_ACL,
                AclEntryPermission.WRITE_OWNER,
                AclEntryPermission.SYNCHRONIZE);
        if (r) {
          permissions.add(AclEntryPermission.READ_DATA); // == LIST_DIRECTORY
        }
        if (w) {
          permissions.add(AclEntryPermission.WRITE_DATA); // == ADD_FILE
          permissions.add(AclEntryPermission.APPEND_DATA); // == ADD_SUBDIRECTORY
        }
        if (x) {
          permissions.add(AclEntryPermission.EXECUTE);
        }
        builder.setPermissions(permissions);
        builder.setPrincipal(user);
        builder.setType(AclEntryType.ALLOW);
        var aclAttr = Files.getFileAttributeView(path, AclFileAttributeView.class);
        aclAttr.setAcl(List.of(builder.build()));
        return;
      }
      var user = (r ? "r" : "-") + (w ? "w" : "-") + (x ? "x" : "-");
      Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(user + "------"));
    }

    private void assertTreeWalkMatches(Path root, String... expected) {
      var c = File.separatorChar;
      expected[0] = expected[0].replace(c, '/');
      var actualLines = new ArrayList<String>();
      make.run(new Make.Action.TreeWalk(root, line -> actualLines.add(line.replace(c, '/'))));
      assertLinesMatch(List.of(expected), actualLines);
    }
  }
}
