import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import java.lang.reflect.Modifier;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.function.Executable;

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
  void mainDoesNotThrow() {
    assertDoesNotThrow((Executable) Make::main);
  }

  @Test
  void mainWithFailDoesThrow() {
    var e = assertThrows(Error.class, () -> Make.main("FAIL"));
    assertEquals("Make.java failed with error code: " + 1, e.getMessage());
  }

  @Test
  void defaults() {
    var make = new Make();
    assertEquals("Make.java", make.logger.getName());
    assertEquals(System.getProperty("user.dir"), make.base.toString());
    assertEquals(List.of(), make.arguments);
  }

  @Test
  void defaultsWithCustomArguments() {
    var make = new Make(List.of("1", "2", "3"));
    assertEquals(List.of("1", "2", "3"), make.arguments);
  }

  @Test
  void runReturnsZero() {
    var logger = new CollectingLogger("*");
    var base = Path.of(".").toAbsolutePath();
    var make = new Make(logger, base, List.of());
    assertEquals(0, make.run());
    assertTrue(logger.getLines().contains("Make.java - " + Make.VERSION));
  }

  @Test
  void runReturnsOneWhenFailIsFoundInArguments() {
    var logger = new CollectingLogger("*");
    var base = Path.of(".").toAbsolutePath();
    var make = new Make(logger, base, List.of("FAIL"));
    assertEquals(1, make.run());
  }

  @Test
  void runWithEmptyIterableReturnsZero() {
    var logger = new CollectingLogger("*");
    var base = Path.of(".").toAbsolutePath();
    var make = new Make(logger, base, List.of());
    assertEquals(0, make.run(new Make.Action[0]));
    assertEquals(0, make.run(List.of()));
  }

  @TestFactory
  Stream<DynamicTest> runReturnsOneForFileSystemRoots() {
    return StreamSupport.stream(FileSystems.getDefault().getRootDirectories().spliterator(), false)
        .map(path -> dynamicTest("" + path, () -> runReturnsOneForFileSystemRoot(path)));
  }

  private void runReturnsOneForFileSystemRoot(Path root) {
    var logger = new CollectingLogger("*");
    var make = new Make(logger, root, List.of());
    assertEquals(1, make.run());
  }

  @Test
  void openAndRunMakeJavaInJShellReturnsZero() throws Exception {
    var builder = new ProcessBuilder("jshell");
    builder.command().add("--execution=local");
    builder.command().add("-"); // Standard input, without interactive I/O.
    var process = builder.start();
    process.getOutputStream().write("/open src/main/Make.java\n".getBytes());
    process.getOutputStream().write("var make = new Make()\n".getBytes());
    process.getOutputStream().write("var code = make.run()\n".getBytes());
    process.getOutputStream().write("/exit code\n".getBytes());
    process.getOutputStream().flush();
    process.waitFor(5, TimeUnit.SECONDS);
    var code = process.exitValue();
    assertEquals(0, code);
  }

  @Test
  void compileAndRunMakeJavaWithJavaReturnsZero() throws Exception {
    var builder = new ProcessBuilder("java");
    builder.command().add("src/main/Make.java");
    var process = builder.start();
    process.waitFor(5, TimeUnit.SECONDS);
    var code = process.exitValue();
    assertEquals(0, code);
  }
}
