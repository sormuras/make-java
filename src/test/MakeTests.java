import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.module.ModuleDescriptor.Version;
import org.junit.jupiter.api.Test;

class MakeTests {
  @Test
  void test() {
    assertFalse(Make.class.getModule().isNamed());
    assertNotNull(new Make(new Logger(), new Make.Project("zero", Version.parse("0"))).toString());
  }
}
