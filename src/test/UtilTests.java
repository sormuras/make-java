import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class UtilTests {

  @Test
  void listAllJavaSourceAndArchiveFilePaths() {
    var paths = Make.Util.listPaths(Path.of(""), "*.ja{r,va}");
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
    return findBaseJavaFeatureNumber(List.of(strings));
  }

  /** Find lowest Java feature number. */
  static int findBaseJavaFeatureNumber(List<String> strings) {
    int base = Integer.MAX_VALUE;
    for (var string : strings) {
      var candidate = Integer.valueOf(string.substring("java-".length()));
      if (candidate < base) {
        base = candidate;
      }
    }
    if (base == Integer.MAX_VALUE) {
      throw new IllegalArgumentException("No base Java feature number found: " + strings);
    }
    return base;
  }
}
