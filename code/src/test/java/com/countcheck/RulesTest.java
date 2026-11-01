package com.countcheck;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class RulesTest {
  @Test
  void countBoundaries() {
    assertEquals(0, Rules.count("0"));
    assertEquals(100000, Rules.count("100000"));
    for (String bad : new String[] {"-1", "100001", "1e2", " 5", "007", "٣", ""})
      assertThrows(IllegalArgumentException.class, () -> Rules.count(bad));
  }

  @Test
  void unicodeAndKeys() {
    assertEquals("Widget  A", Rules.text("  Widget  A  ", 80, "Name"));
    assertEquals("😀".repeat(80), Rules.text("😀".repeat(80), 80, "Name"));
    assertThrows(IllegalArgumentException.class, () -> Rules.text("x\ny", 80, "Name"));
    assertThrows(IllegalArgumentException.class, () -> Rules.key("1-1-1-1-1"));
    assertNotNull(Rules.key(java.util.UUID.randomUUID().toString()));
  }

  @Test
  void signedDifference() {
    assertEquals("+3", Rules.difference(3));
    assertEquals("−3", Rules.difference(-3));
    assertEquals("0", Rules.difference(0));
  }

  @Test
  void csvSafety() {
    assertEquals("\"'=SUM(1)\"", Web.cell("=SUM(1)"));
    assertEquals("\"a\"\"b\"", Web.cell("a\"b"));
  }
}
