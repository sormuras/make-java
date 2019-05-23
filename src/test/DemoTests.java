import static org.junit.jupiter.api.Assertions.assertLinesMatch;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class DemoTests {

  @Test
  void demoClassPath() {
    var make = Make.of(Path.of("demo", "class-path"));
    assertLinesMatch(List.of(), make.main.modules);
    assertLinesMatch(List.of(), make.test.modules);
  }

  @Test
  void demoJigsawQuickStartGreetings() {
    var make = Make.of(Path.of("demo", "jigsaw-quick-start", "greetings"));
    assertLinesMatch(List.of("com.greetings"), make.main.modules);
    assertLinesMatch(List.of(), make.test.modules);
  }

  @Test
  void demoJigsawQuickStartGreetingsWorldWithMainAndTest() {
    var make = Make.of(Path.of("demo", "jigsaw-quick-start", "greetings-world-with-main-and-test"));
    assertLinesMatch(List.of("com.greetings", "org.astro"), make.main.modules);
    assertLinesMatch(List.of("integration", "org.astro"), make.test.modules);
  }
}
