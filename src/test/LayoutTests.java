import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class LayoutTests {
  @Test
  void checkLayoutOfDotGithub() {
    assertFalse(Make.Project.Layout.valueOf(Path.of(".github")).isPresent());
  }

  @Test
  void checkLayoutOfDocExampleDefaultMainTest() {
    assertEquals(
        Make.Project.Layout.DEFAULT,
        Make.Project.Layout.valueOf(Path.of("doc/example/default-main-test/src")).orElseThrow());
  }

  @Test
  void checkLayoutOfDocExampleJigsawQuickStart() {
    assertEquals(
        Make.Project.Layout.JIGSAW,
        Make.Project.Layout.valueOf(Path.of("doc/example/jigsaw-quick-start/src")).orElseThrow());
  }
}
