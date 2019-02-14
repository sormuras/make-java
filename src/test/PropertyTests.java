import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class PropertyTests {

  private final CollectingLogger logger = new CollectingLogger("*");
  private final Make make = new Make(logger, Path.of("."), List.of());

  @ParameterizedTest
  @EnumSource(Make.Property.class)
  void assertProperty(Make.Property property) {
    assertTrue(property.key.startsWith("make."));
    assertFalse(property.defaultValue.isBlank());
  }

  @Test
  void pathCacheTools() {
    assertNotNull(Make.Property.PATH_CACHE_TOOLS);
    assertEquals(".make/tools", make.var.get(Make.Property.PATH_CACHE_TOOLS));
    assertEquals(Path.of(".make", "tools"), make.based(Make.Property.PATH_CACHE_TOOLS));
  }

  @Test
  void pathCacheModules() {
    assertNotNull(Make.Property.PATH_CACHE_MODULES);
    assertEquals(".make/modules", make.var.get(Make.Property.PATH_CACHE_MODULES));
    assertEquals(Path.of(".make", "modules"), make.based(Make.Property.PATH_CACHE_MODULES));
  }

  @Test
  void toolJUnitUriEndsWithJar() {
    assertTrue(make.var.get(Make.Property.TOOL_JUNIT_URI).endsWith(".jar"));
  }
}
