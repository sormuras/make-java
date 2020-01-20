import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.nio.file.Path;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ExampleTests {

  private static Make make(String example) {
    var logger = new Logger(true);
    var folder = Make.Folder.of(Path.of("doc", "example", example));
    var project = Make.Project.Builder.of(logger, folder).build();
    var planner = new Make.Tool.Planner();

    return new Make(logger, folder, project, planner);
  }

  @ParameterizedTest
  @ValueSource(strings = {"default-main-test", "jigsaw-quick-start"})
  void buildDefaultMainTest(String example) {
    assertDoesNotThrow(() -> make(example).run());
  }
}
