import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class ArgsTests {
  @Test
  void defaults() {
    assertArrayEquals(
        new String[] {
          "a", "b", "c", "d", "e", "f", "g", "h", "i0" + File.pathSeparator + "i1", "j", "k"
        },
        new Make.Args()
            .addEach(List.of("a", "b", "c"))
            .add("d", "e")
            .add("f")
            .add(true, "g")
            .add(false, "X")
            .add("h", List.of(Path.of("i0"), Path.of("i1")))
            .add("j", List.of(Path.of("k")))
            .toStringArray());
  }
}
