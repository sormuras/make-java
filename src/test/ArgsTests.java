import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertLinesMatch;

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
