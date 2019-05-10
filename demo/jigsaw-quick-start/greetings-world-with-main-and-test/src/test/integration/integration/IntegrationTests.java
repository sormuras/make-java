package integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.astro.World;
import org.junit.jupiter.api.Test;

class IntegrationTests {

  @Test
  void accessWorld() {
    assertEquals("world", World.name());
  }

  @Test
  void accessGreetings() {
    // assertEquals("Main", com.greetings.Main.class.getSimpleName()); // Does not compile!
    assertThrows(Throwable.class, () -> Class.forName("com.greetings.Main").getSimpleName());
  }
}
