import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class LayoutTests {
  @Test
  void checkLayoutOfDotGithub() throws Exception {
    assertFalse(Make.Project.Layout.valueOf(Path.of(".github")).isPresent());
  }

  @Test
  void checkLayoutOfDocExampleDefaultMainTest() throws Exception {
    assertEquals(
        Make.Project.Layout.DEFAULT,
        Make.Project.Layout.valueOf(Path.of("doc/example/default-main-test/src")).orElseThrow());
  }

  @Test
  void checkLayoutOfDocExampleGroupModuleRealm() throws Exception {
    assertEquals(
        Make.Project.Layout.GROUPED,
        Make.Project.Layout.valueOf(Path.of("doc/example/group-module-realm/src")).orElseThrow());
  }

  @Test
  void checkLayoutOfDocExampleJigsawQuickStart() throws Exception {
    assertEquals(
        Make.Project.Layout.JIGSAW,
        Make.Project.Layout.valueOf(Path.of("doc/example/jigsaw-quick-start/src")).orElseThrow());
  }
}
