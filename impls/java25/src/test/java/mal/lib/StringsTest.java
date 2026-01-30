/*
 * Copyright (c) 2024-2025, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package mal.lib;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
