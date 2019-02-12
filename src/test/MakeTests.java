import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

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
  void runReturnsZero() {
    assertEquals(0, new Make().run());
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
}
