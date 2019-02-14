import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class VariablesTests {

  private final CollectingLogger logger = new CollectingLogger("*");
  private final Make make = new Make(logger, Path.of("."), List.of());

  @Test
  void defaults() {
    assertEquals(0, make.var.properties.size());
    assertFalse(make.var.offline);
  }

  @Test
  void loadPropertiesFromDirectoryFails() {
    assertThrows(Error.class, () -> make.var.load(Path.of(".")));
  }

  @Test
  void loadPropertiesFromTestResources() {
    var path = Path.of("src", "test-resources", "Property.load.properties");
    var map = make.var.load(path);
    assertEquals("true", map.get("make.offline"));
    assertEquals("Test Project Name", map.get("project.name"));
    assertEquals("1.2.3-SNAPSHOT", map.get("project.version"));
    assertEquals(3, map.size());
  }

  @Test
  void systemPropertyOverridesManagedProperty() {
    var key = "make.test.systemPropertyOverridesManagedProperty";
    assertNull(make.var.get(key, null));
    make.var.properties.setProperty(key, "123");
    assertEquals("123", make.var.get(key, "456"));
    System.setProperty(key, "789");
    assertEquals("789", make.var.get(key, "456"));
    System.clearProperty(key);
  }
}
