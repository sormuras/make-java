import static org.junit.jupiter.api.Assertions.assertLinesMatch;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class ArgsTests {
  @Test
  void defaults() {
    assertLinesMatch(
        List.of("a", "b", "c", "d", "e", "f", "g", "h", "i0.i1"),
        new Make.Args()
            .withEach(List.of("a", "b", "c"))
            .with("d", "e")
            .with("f")
            .with(true, "g")
            .with(false, "X")
            .with("h", List.of(Path.of("i0"), Path.of("i1"))));
  }
}
