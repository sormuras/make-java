import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class UtilTests {

  @Test
  void delay() {
    assertDoesNotThrow(() -> Make.Util.delay(0));
    assertThrows(IllegalArgumentException.class, () -> Make.Util.delay(-1));
  }

  @Test
  void findAllJavaSourceAndArchiveFilePaths() {
    var paths = Make.Util.find(Path.of(""), "*.ja{r,va}");
    assertTrue(paths.size() >= 19, "Found only " + paths.size() + " paths: " + paths);
    assertTrue(paths.contains(Path.of("src", "main", "Make.java")), paths.toString());
    assertTrue(paths.contains(Path.of("src", "test", "MakeTests.java")), paths.toString());
  }

  @Test
  void baseJavaFeatureNumber() {
    assertEquals(0, base("java-0"));
    assertEquals(0, base("java-0", "java-1"));
    assertEquals(0, base("java-1", "java-10", "java-0"));
    assertEquals(7, base("java-9", "java-8", "java-7"));
    assertEquals(8, base("java-8", "java-" + Runtime.version().feature()));
  }

  private int base(String... strings) {
    return Make.Util.findBaseJavaFeatureNumber(List.of(strings));
  }
}
