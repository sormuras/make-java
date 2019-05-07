import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertLinesMatch;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Modifier;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

class MakeTests {

  @Test
  void versionIsMasterXorConsumableByRuntimeVersionParse() throws Exception {
    var actual = "" + Make.class.getDeclaredField("VERSION").get(null);
    if (actual.equals("master")) {
      return;
    }
    Runtime.Version.parse(actual);
  }

  @Test
  void userPathIsCurrentWorkingDirectory() {
    assertEquals(Path.of(".").normalize().toAbsolutePath(), Make.USER_PATH);
  }

  @Test
  void hasPublicStaticVoidMainWithVarArgs() throws Exception {
    var main = Make.class.getMethod("main", String[].class);
    assertTrue(Modifier.isPublic(main.getModifiers()));
    assertTrue(Modifier.isStatic(main.getModifiers()));
    assertSame(void.class, main.getReturnType());
    assertEquals("main", main.getName());
    assertTrue(main.isVarArgs());
    assertEquals(0, main.getExceptionTypes().length);
  }

  @Test
  void mainWithoutArguments() {
    assertDoesNotThrow((Executable) Make::main);
  }

  @Test
  void mainWithJar(@TempDir Path temp) throws Exception {
    var hello = Files.write(temp.resolve("hello.txt"), List.of("world"));
    var jar = temp.resolve("hello.jar");
    assertDoesNotThrow(
        () -> Make.main("tool", "jar", "--create", "--file", jar.toString(), hello.toString()));
    assertTrue(Files.exists(jar));
    Files.delete(hello);

    Consumer<String> assertJarList =
        out ->
            assertLinesMatch(
                List.of("META-INF/", "META-INF/MANIFEST.MF", temp.getFileName() + "/hello.txt"),
                out.lines().collect(toList()));
    assertRun(assertJarList, "jar", "--list", "--file", jar.toString());
  }

  @Test
  void mainWithFailArgumentDoesThrow() {
    var e = assertThrows(Error.class, () -> Make.main("FAIL"));
    assertEquals("Make.java failed with error code: " + 1, e.getMessage());
  }

  @Test
  void defaults() {
    var make = new Make();
    assertEquals("Make.java", make.logger.getName());
    assertEquals(System.getProperty("user.dir"), make.base.toString());
    assertEquals(System.getProperty("user.dir"), make.work.toString());
    assertFalse(make.dryRun);
    assertEquals(List.of(), make.arguments);
  }

  @Test
  void defaultsWithCustomArguments() {
    var make = new Make("1", "2", "3");
    assertEquals(List.of("1", "2", "3"), make.arguments);
  }

  @Test
  void runReturnsZero() {
    var logger = new CollectingLogger("*");
    var base = Path.of(".").toAbsolutePath();
    var make = new Make(logger, base, base, true, List.of());
    assertEquals(0, make.run(), logger.toString());
    assertTrue(logger.getLines().contains("Make.java - " + Make.VERSION));
  }

  @Test
  void runReturnsMinusOneWhenFailIsFoundInArguments() {
    var logger = new CollectingLogger("*");
    var base = Path.of(".").toAbsolutePath();
    var make = new Make(logger, base, base, true, List.of("FAIL"));
    assertEquals(1, make.run());
  }

  @Test
  void runJavacVersionReturnsZeroAndPrintsDotSeparatedVersionToTheStandardOutputStream() {
    var version = Runtime.version().version().stream().map(Object::toString).collect(joining("."));
    assertRun(out -> assertEquals("javac " + version, out.strip()), "javac", "--version");
  }

  @TestFactory
  Stream<DynamicTest> runReturnsOneForFileSystemRoots() {
    return StreamSupport.stream(FileSystems.getDefault().getRootDirectories().spliterator(), false)
        .map(path -> dynamicTest("" + path, () -> runReturnsOneForFileSystemRoot(path)));
  }

  private void runReturnsOneForFileSystemRoot(Path root) {
    var logger = new CollectingLogger("*");
    var make = new Make(logger, root, root, true, List.of());
    assertEquals(1, make.run());
  }

  private void assertRun(Consumer<String> consumer, String name, String... args) {
    assertRun(
        logger -> {},
        (out, err) -> {
          consumer.accept(out);
          assertEquals("", err);
        },
        0,
        name,
        args);
  }

  private void assertRun(
      Consumer<CollectingLogger> loggerConsumer,
      BiConsumer<String, String> streamConsumer,
      int expected,
      String name,
      String... args) {
    var logger = new CollectingLogger("*");
    var base = Path.of(".").toAbsolutePath();
    var make = new Make(logger, base, base, false, List.of());
    var out = new ByteArrayOutputStream();
    var err = new ByteArrayOutputStream();
    assertEquals(expected, make.run(new PrintStream(out), new PrintStream(err), name, args));
    loggerConsumer.accept(logger);
    streamConsumer.accept(out.toString(), err.toString());
  }
}
