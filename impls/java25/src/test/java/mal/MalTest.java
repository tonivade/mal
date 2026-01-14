/*
 * Copyright (c) 2024-2025, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package mal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class MalTest {

  @Test
  void step0() {
    assertEquals("hello", Step0.rep("hello"));
    assertEquals("#", Step0.rep("#"));
    assertEquals("$", Step0.rep("$"));
  }

  @Test
  void step1() {
    assertEquals("(a 1 2 3)", Step1.rep("    (a    1 2 3   )"));
    assertEquals("\"hello\"", Step1.rep("\"hello\""));
    assertEquals("\"hello\\\"hello\"", Step1.rep("\"hello\\\"hello\""));
    assertEquals("\"\"", Step1.rep("\"\""));
    assertEquals("\"\\n\"", Step1.rep("\"\\n\""));
    assertEquals("nil", Step1.rep("nil"));
    assertEquals("true", Step1.rep("true"));
    assertEquals("false", Step1.rep("false"));
    assertEquals("asdf", Step1.rep("asdf"));
    assertEquals(":asdf", Step1.rep(":asdf"));
    assertEquals("()", Step1.rep("()"));
    assertEquals("()", Step1.rep("( )"));
    assertEquals("(nil)", Step1.rep("(nil)"));
    assertEquals("((3 4))", Step1.rep("((3 4))"));
    assertThrows(MalException.class, () -> Step1.rep("\""));
    assertThrows(MalException.class, () -> Step1.rep("("));
    assertThrows(MalException.class, () -> Step1.rep("["));
    assertThrows(MalException.class, () -> Step1.rep("{"));
  }

  @Test
  void step2() {
    assertEquals("3", Step2.rep("(+ 1 2)"));
    assertEquals("-1", Step2.rep("(- 1 2)"));
    assertEquals("4", Step2.rep("(* 2 2)"));
    assertEquals("1", Step2.rep("(/ 2 2)"));
  }

  @Test
  void step8() {
    Step8.rep("(defmacro! unless (fn* (pred a b) `(if ~pred ~b ~a)))");
    assertEquals("7", Step8.rep("(unless false 7 8)"));
    assertEquals("8", Step8.rep("(unless true 7 8)"));
  }

  @Test
  void stepA() {
    assertEquals("6", StepA.rep("(java-eval \"1 + 2 + 3\")"));
    StepA.rep("(import java.lang.String length)");
    assertEquals("5", StepA.rep("(length \"12345\")"));
  }

  @Test
  void stepB() {
    StepA.rep("(def! ones (fn* [] (lazy-seq (cons 1 (ones)))))");
    StepA.rep("(def! take (fn* [i xs] (if (empty? xs) xs (if (> i 0) (cons (first xs) (take (- i 1) (rest xs))) (list)))))");
    assertEquals("1", StepA.rep("(first (ones))"));
    assertEquals("(1 1 1 1 1)", StepA.rep("(take 5 (ones))"));
    assertEquals("(2 2 2 2 2)", StepA.rep("(map (fn* [x] (+ x 1)) (take 5 (ones)))"));
    assertEquals("(2 2 2 2 2)", StepA.rep("(take 5 (map (fn* [x] (+ x 1)) (ones)))"));
  }
}
