import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class BuilderTests {
  @Test
  void buildDocExampleDefaultMainTest() {
    var logger = new Logger();
    var folder = Make.Folder.of(Path.of("doc/example/default-main-test"));
    var actual = Make.Project.Builder.of(logger, folder).build();
    var expected =
        new Make.Project.Builder()
            .setName("default-main-test")
            .setVersion("1-ea")
            .setMain(
                new Make.Project.Realm.Builder("main")
                    .setModules(List.of("org.foo", "org.foo.bar"))
                    .setModuleSourcePaths(List.of(Path.of("src/${MODULE}/main/java")))
                    .build())
            .setTest(
                new Make.Project.Realm.Builder("test")
                    .setModules(List.of("test.base"))
                    .setModuleSourcePaths(List.of(Path.of("src/${MODULE}/test/java")))
                    .build())
            .build();
    // record ... assertEquals(expected, actual);
    assertEquals(expected.name(), actual.name());
    assertEquals(expected.version, actual.version());
  }
}
