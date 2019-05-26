import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertLinesMatch;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class SanityTests {

  private static final List<String> EXPECTED_NORMAL_OUTPUT_LINES =
      List.of(
          "__BEGIN__",
          "Making Make.java master...",
          "Make.java master",
          "  args = []",
          "  user.path = " + Make.USER_PATH,
          "  configuration.home = .*",
          "  configuration.work = .*target",
          "  configuration.threshold = ALL",
          "  run\\.type = .*Make\\$Run",
          "Dry-run ends here.");

  @Test
  void openAndRunMakeJavaInJShellReturnsZero() throws Exception {
    var builder = new ProcessBuilder("jshell");
    builder.command().add("--execution=local");
    builder.command().add("-J-Debug");
    builder.command().add("-"); // Standard input, without interactive I/O.
    var process = builder.start();
    process.getOutputStream().write("/open src/main/Make.java\n".getBytes());
    process.getOutputStream().write("var make = Make.of(Make.USER_PATH)\n".getBytes());
    process.getOutputStream().write("var code = make.run(System.out, System.err)\n".getBytes());
    process.getOutputStream().write("/exit code\n".getBytes());
    process.getOutputStream().flush();
    process.waitFor(19, TimeUnit.SECONDS);
    assertStreams(List.of(), process);
    assertEquals(0, process.exitValue(), process.toString());
  }

  @Test
  void compileAndRunMakeJavaWithJavaReturnsZero() throws Exception {
    var builder = new ProcessBuilder("java");
    builder.command().add("-ea");
    builder.command().add("-Debug");
    builder.command().add("src/main/Make.java");
    var process = builder.start();
    process.waitFor(19, TimeUnit.SECONDS);
    assertStreams(List.of(), process);
    assertEquals(0, process.exitValue(), process.toString());
  }

  static void assertStreams(List<String> expectedErrorStreamLines, Process process) {
    var out = lines(process.getInputStream());
    var err = lines(process.getErrorStream());
    try {
      assertLinesMatch(EXPECTED_NORMAL_OUTPUT_LINES, out);
      assertLinesMatch(expectedErrorStreamLines, err);
    } catch (AssertionError e) {
      var msg = String.join("\n", err) + String.join("\n", out);
      System.err.println(msg);
      throw e;
    }
  }

  static List<String> lines(InputStream stream) {
    try (var reader = new BufferedReader(new InputStreamReader(stream))) {
      return reader.lines().collect(Collectors.toList());
    } catch (IOException e) {
      throw new UncheckedIOException("Reading from stream failed!", e);
    }
  }
}
