package mal.lib;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class StringsTest {

  @Test
  void testFindMatches() {
    var result = Strings.findMatches("abc def ghi", "(\\w{3})");

    assertEquals(3, result.size());
    assertEquals("abc", result.get(0).get(0));
    assertEquals("def", result.get(1).get(0));
    assertEquals("ghi", result.get(2).get(0));
  }
}
