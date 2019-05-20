import static org.junit.jupiter.api.Assertions.assertLinesMatch;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class DemoTests {

  @Test
  void demoJigsawQuickStartGreetings() {
    var make = Make.of(Path.of("demo", "jigsaw-quick-start", "greetings"));
    assertLinesMatch(List.of("com.greetings"), make.main.listModules());
    assertLinesMatch(List.of(), make.test.listModules());
  }

  @Test
  void demoJigsawQuickStartGreetingsWorldWithMainAndTest() {
    var make = Make.of(Path.of("demo", "jigsaw-quick-start", "greetings-world-with-main-and-test"));
    assertLinesMatch(List.of("com.greetings", "org.astro"), make.main.listModules());
    assertLinesMatch(List.of("integration", "org.astro"), make.test.listModules());
  }
}
