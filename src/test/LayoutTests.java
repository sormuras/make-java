import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class LayoutTests {
  @Test
  void checkLayoutOfDotGithub() {
    assertFalse(Make.Layout.valueOf(Path.of(".github")).isPresent());
  }

  @Test
  void checkLayoutOfDocExampleDefaultMainTest() {
    assertEquals(
        Make.Layout.DEFAULT,
        Make.Layout.valueOf(Path.of("doc/example/default-main-test/src")).orElseThrow());
  }

  @Test
  void checkLayoutOfDocExampleGroupModuleRealm() {
    assertEquals(
        Make.Layout.GROUPED,
        Make.Layout.valueOf(Path.of("doc/example/group-module-realm/src")).orElseThrow());
  }

  @Test
  void checkLayoutOfDocExampleJigsawQuickStart() {
    assertEquals(
        Make.Layout.JIGSAW,
        Make.Layout.valueOf(Path.of("doc/example/jigsaw-quick-start/src")).orElseThrow());
  }
}
