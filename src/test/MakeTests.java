import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class MakeTests {
  @Test
  void test() {
    assertFalse(Make.class.getModule().isNamed());
    assertNotNull(new Make(new Logger(), new Make.Project("zero", "0")).toString());
  }
}
