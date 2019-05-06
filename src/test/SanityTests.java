import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class SanityTests {

  @Test
  void openAndRunMakeJavaInJShellReturnsZero() throws Exception {
    var builder = new ProcessBuilder("jshell");
    builder.command().add("--execution=local");
    builder.command().add("-J-D" + "ry-run=true");
    builder.command().add("-"); // Standard input, without interactive I/O.
    var process = builder.start();
    process.getOutputStream().write("/open src/main/Make.java\n".getBytes());
    process.getOutputStream().write("var make = new Make()\n".getBytes());
    process.getOutputStream().write("var code = make.run()\n".getBytes());
    process.getOutputStream().write("/exit code\n".getBytes());
    process.getOutputStream().flush();
    process.waitFor(9, TimeUnit.SECONDS);
    var code = process.exitValue();
    assertEquals(0, code);
  }

  @Test
  void compileAndRunMakeJavaWithJavaReturnsZero() throws Exception {
    var builder = new ProcessBuilder("java");
    builder.command().add("-ea");
    builder.command().add("-D" + "ry-run=true");
    builder.command().add("src/main/Make.java");
    var process = builder.start();
    process.waitFor(9, TimeUnit.SECONDS);
    var code = process.exitValue();
    assertEquals(0, code);
  }
}
