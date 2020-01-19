import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class MakeTests {
  @Test
  void test() {
    assertFalse(Make.class.getModule().isNamed());
    assertNotNull(Make.VERSION);
  }
}
