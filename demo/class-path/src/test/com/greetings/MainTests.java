package com.greetings;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MainTests {
  @Test
  void mainClassLoader() {
    assertEquals("junit-main", Main.class.getClassLoader().getName());
  }

  @Test
  void testClassLoader() {
    assertEquals("junit-test", getClass().getClassLoader().getName());
  }
}
