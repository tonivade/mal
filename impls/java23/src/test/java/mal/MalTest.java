/*
 * Copyright (c) 2024, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package mal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class MalTest {

  @Test
  void step0() {
    assertEquals("hello", step0_repl.rep("hello"));
    assertEquals("#", step0_repl.rep("#"));
    assertEquals("$", step0_repl.rep("$"));
  }

  @Test
  void step1() {
    assertEquals("(a 1 2 3)", step1_read_print.rep("    (a    1 2 3   )"));
    assertEquals("\"hello\"", step1_read_print.rep("\"hello\""));
    assertEquals("\"hello\\\"hello\"", step1_read_print.rep("\"hello\\\"hello\""));
    assertEquals("\"\"", step1_read_print.rep("\"\""));
    assertEquals("\"\\n\"", step1_read_print.rep("\"\\n\""));
    assertEquals("nil", step1_read_print.rep("nil"));
    assertEquals("true", step1_read_print.rep("true"));
    assertEquals("false", step1_read_print.rep("false"));
    assertEquals("asdf", step1_read_print.rep("asdf"));
    assertEquals(":asdf", step1_read_print.rep(":asdf"));
    assertThrows(MalException.class, () -> step1_read_print.rep("\""));
    assertThrows(MalException.class, () -> step1_read_print.rep("("));
    assertThrows(MalException.class, () -> step1_read_print.rep("["));
    assertThrows(MalException.class, () -> step1_read_print.rep("{"));
  }

  @Test
  void step2() {
    assertEquals("3", step2_eval.rep("(+ 1 2)"));
    assertEquals("-1", step2_eval.rep("(- 1 2)"));
    assertEquals("4", step2_eval.rep("(* 2 2)"));
    assertEquals("1", step2_eval.rep("(/ 2 2)"));
  }
}
