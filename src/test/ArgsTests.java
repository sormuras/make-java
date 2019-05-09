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
        List.of("a", "b", "c", "d", "e", "f", "g"),
        new Make.Args()
            .withEach(List.of("a", "b", "c"))
            .with("d", "e")
            .with("f")
            .with(true, "g")
            .with(false, "X"));
  }
}
