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

  @Test
  void openAndRunMakeJavaInJShellReturnsOne() throws Exception {
    var builder = new ProcessBuilder("jshell");
    builder.command().add("--execution=local");
    builder.command().add("-"); // Standard input, without interactive I/O.
    var process = builder.start();
    process.getOutputStream().write("/open src/main/Make.java\n".getBytes());
    process.getOutputStream().write("var make = Make.of(Make.USER_PATH)\n".getBytes());
    process.getOutputStream().write("var code = make.run(System.out, System.err)\n".getBytes());
    process.getOutputStream().write("/exit code\n".getBytes());
    process.getOutputStream().flush();
    process.waitFor(9, TimeUnit.SECONDS);
    var code = process.exitValue();
    assertEquals(1, code);
    assertStreams(
        List.of("Making Make.java master..."),
        List.of("No module found: " + Make.USER_PATH),
        process);
  }

  @Test
  void compileAndRunMakeJavaWithJavaReturnsOne() throws Exception {
    var builder = new ProcessBuilder("java");
    builder.command().add("-ea");
    builder.command().add("src/main/Make.java");
    var process = builder.start();
    process.waitFor(9, TimeUnit.SECONDS);
    var code = process.exitValue();
    assertEquals(1, code);
    assertStreams(
        List.of("Making Make.java master..."),
        List.of("No module found: " + Make.USER_PATH, "Make.java failed with error code: 1"),
        process);
  }

  static void assertStreams(List<String> expectedOut, List<String> expectedErr, Process process) {
    assertLinesMatch(expectedOut, lines(process.getInputStream()));
    assertLinesMatch(expectedErr, lines(process.getErrorStream()));
  }

  static List<String> lines(InputStream stream) {
    try (var reader = new BufferedReader(new InputStreamReader(stream))) {
      return reader.lines().collect(Collectors.toList());
    } catch (IOException e) {
      throw new UncheckedIOException("Reading from stream failed!", e);
    }
  }
}
