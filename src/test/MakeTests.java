import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

class MakeTests {
  @Test
  void defaults() {
    var make = Make.of(Path.of(".").normalize().toAbsolutePath());
    var configuration = make.configuration;

    assertEquals(Path.of(""), configuration.home);
    assertEquals("Make.java", configuration.project.name);
    assertEquals("master", configuration.project.version);
  }

  @Test
  void run() {
    var make = Make.of(Path.of(".").normalize().toAbsolutePath());
    var run = new TestRun();

    var code = make.run(run, List.of());
    var out = run.out.toString();

    assertEquals(0, code, out + "\n" + run.err);
    assertTrue(out.contains(make.name() + ' ' + Make.VERSION));
    assertTrue(out.contains("args = []"));
    assertTrue(out.contains("Build successful after"));
    assertEquals("", run.err.toString());
  }
}
