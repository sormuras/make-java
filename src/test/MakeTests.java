import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class MakeTests {
  @Test
  void statics() {
    assertFalse(Make.class.getModule().isNamed());
    assertNotNull(Make.VERSION);
  }

  @Test
  void defaults() {
    var logger = new Logger();
    var folder = Make.Folder.ofCurrentWorkingDirectory();
    var project = Make.Project.Builder.of(logger, folder).build();
    var planner = new Make.Tool.Planner();
    var make = new Make(logger, folder, project, planner);
    assertSame(logger, make.logger());
    assertEquals(Path.of(""), make.folder().base());
    assertEquals(Path.of("README.md"), make.folder().base("README.md"));
    assertEquals(Path.of("src"), make.folder().src());
    assertEquals(Path.of("src", "foo"), make.folder().src("foo"));
    assertEquals(Path.of("lib"), make.folder().lib());
    assertEquals(Path.of(".make-java"), make.folder().out());
    assertEquals("make-java", make.project().name());
    assertEquals("1-ea", make.project().version().toString());
    assertEquals(Make.Project.Layout.DEFAULT, make.project().layout());
    assertEquals("main", make.project().realms().get(0).name());
    assertEquals(List.of(), make.project().realms().get(0).modules());
    assertEquals("test", make.project().realms().get(1).name());
    assertEquals(List.of(), make.project().realms().get(1).modules());
  }
}
