import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.nio.file.Path;
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
    var make = new Make(logger);
    assertSame(logger, make.logger());
    assertEquals(Path.of(""), make.folder().base());
    assertEquals(Path.of("README.md"), make.folder().base("README.md"));
    assertEquals(Path.of("src"), make.folder().src());
    assertEquals(Path.of("lib"), make.folder().lib());
    assertEquals(Path.of(".make-java"), make.folder().out());
    assertEquals("make-java", make.project().name());
  }
}
