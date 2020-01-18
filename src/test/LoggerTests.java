import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class LoggerTests {
  @Test
  void simpleNameIsSystemLogger() {
    assertEquals("SystemLogger", Make.Logger.ofSystem().getClass().getSimpleName());
  }
}
