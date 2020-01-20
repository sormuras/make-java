import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
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

  @Test
  void infoOfJigsawQuickStart() {
    var base = Path.of("doc/example/jigsaw-quick-start");
    var actual =
        Make.Project.Layout.JIGSAW.find(Make.Folder.of(base), "realm-is-ignored").stream()
            .map(Make.Project.Info::path)
            .sorted()
            .collect(Collectors.toList());
    var expected =
        List.of(
            Path.of("com.greetings/module-info.java"),
            Path.of("org.astro/module-info.java"),
            Path.of("test/module-info.java"));
    assertEquals(expected, actual);
  }
}
