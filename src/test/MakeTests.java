import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
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
  void mainDoesNotThrow() {
    assertDoesNotThrow((Executable) Make::main);
  }

  @Test
  void defaults() {
    var make = new Make();
    assertEquals("Make.java", make.logger.getName());
  }

  @Test
  void runReturnsZero() {
    var logger = new CollectingLogger();
    var make = new Make(logger);
    assertEquals(0, make.run());
    assertSame(logger, make.logger);
    assertTrue(logger.getLines().contains("INFO: Make.java - " + Make.VERSION));
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
