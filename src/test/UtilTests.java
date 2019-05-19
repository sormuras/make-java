import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class UtilTests {
  @Test
  void javaFeatureNumber() {
    assertEquals(0, low("java-0"));
    assertEquals(0, low("java-0", "java-1"));
    assertEquals(0, low("java-1", "java-10", "java-0"));
    assertEquals(7, low("java-9", "java-8", "java-7"));
    assertEquals(8, low("java-8", "java-" + Runtime.version().feature()));
  }

  private int low(String... strings) {
    return Make.Util.findBaseJavaFeatureNumber(List.of(strings));
  }
}
