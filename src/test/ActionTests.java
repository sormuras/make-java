import static java.lang.System.Logger.Level.DEBUG;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertLinesMatch;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

class ActionTests {

  private final CollectingLogger logger = new CollectingLogger("*");
  private final Make make = new Make(logger, Path.of("."), Path.of("."), true, List.of());

  @Test
  void runUnsupportedActionReturnsTwo() {
    assertEquals(2, make.run(new ArrayDeque<>(List.of("unsupported"))));
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

      assertEquals(0, make.run(new TreeCopy(root.resolve("x"), root.resolve("a/b/c"))));
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

      assertEquals(0, make.run(new TreeCopy(root.resolve("x"), root.resolve("x/y"))));
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
      var copy = new TreeCopy(root, Path.of("."));
      assertEquals(0, make.run(copy));
    }

    @Test
    void copyAndItsPreconditions(@TempDir Path temp) throws Exception {
      var regular = createFiles(temp, 2).get(0);
      assertEquals(1, make.run(new TreeCopy(regular, Path.of("."))));
      var directory = Files.createDirectory(temp.resolve("directory"));
      createFiles(directory, 3);
      assertEquals(2, make.run(new TreeCopy(directory, regular)));
      assertEquals(0, make.run(new TreeCopy(directory, directory)));
      assertEquals(3, make.run(new TreeCopy(temp, directory)));
      var forbidden = Files.createDirectory(temp.resolve("forbidden"));
      try {
        denyListing(forbidden, false, false, true);
        assertEquals(4, make.run(new TreeCopy(directory, forbidden)));
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

  /** Delete selected files and directories from the root directory. */
  class TreeCopy implements Make.Action {

    final Path source, target;
    final Predicate<Path> filter;

    TreeCopy(Path source, Path target) {
      this(source, target, __ -> true);
    }

    TreeCopy(Path source, Path target, Predicate<Path> filter) {
      this.source = source;
      this.target = target;
      this.filter = filter;
    }

    @Override
    public int run(Make make) {
      // debug("treeCopy(source:`%s`, target:`%s`)%n", source, target);
      if (!Files.exists(source)) {
        return 0;
      }
      if (!Files.isDirectory(source)) {
        // throw new IllegalArgumentException("source must be a directory: " + source);
        return 1;
      }
      if (Files.exists(target)) {
        if (!Files.isDirectory(target)) {
          // throw new IllegalArgumentException("target must be a directory: " + target);
          return 2;
        }
        if (target.equals(source)) {
          return 0;
        }
        if (target.startsWith(source)) {
          // copy "a/" to "a/b/"...
          return 3;
        }
      }
      try (var stream = Files.walk(source).sorted()) {
        int counter = 0;
        var paths = stream.collect(Collectors.toList());
        for (var path : paths) {
          var destination = target.resolve(source.relativize(path));
          if (Files.isDirectory(path)) {
            Files.createDirectories(destination);
            continue;
          }
          if (filter.test(path)) {
            Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING);
            counter++;
          }
        }
        make.logger.log(DEBUG, "Copied {0} file(s) of {1} elements.", counter, paths.size());
      } catch (Exception e) {
        // throw new UncheckedIOException("copyTree failed", e);
        return 4;
      }
      return 0;
    }
  }
}
