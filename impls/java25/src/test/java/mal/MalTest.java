/*
 * Copyright (c) 2024-2025, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package mal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

class MalTest {

  @Test
  void step0() {
    // Testing basic string
    assertEquals("abcABC123", Step0.rep("abcABC123"));

    // Testing string containing spaces
    assertEquals("hello mal world", Step0.rep("hello mal world"));

    // Testing string containing symbols
    assertEquals("[]{}\"'* ;:()", Step0.rep("[]{}\"'* ;:()"));

    // Test long string
    assertEquals("hello world abcdefghijklmnopqrstuvwxyz ABCDEFGHIJKLMNOPQRSTUVWXYZ 0123456789 (;:() []{}\"'* ;:() []{}\"'* ;:() []{}\"'*)",
        Step0.rep("hello world abcdefghijklmnopqrstuvwxyz ABCDEFGHIJKLMNOPQRSTUVWXYZ 0123456789 (;:() []{}\"'* ;:() []{}\"'* ;:() []{}\"'*)"));

    // Non alphanumeric characters
    assertEquals("!", Step0.rep("!"));
    assertEquals("&", Step0.rep("&"));
    assertEquals("+", Step0.rep("+"));
    assertEquals(",", Step0.rep(","));
    assertEquals("-", Step0.rep("-"));
    assertEquals("/", Step0.rep("/"));
    assertEquals("<", Step0.rep("<"));
    assertEquals("=", Step0.rep("="));
    assertEquals(">", Step0.rep(">"));
    assertEquals("?", Step0.rep("?"));
    assertEquals("@", Step0.rep("@"));

    // Behaviour of backslash is not specified enough to test anything in step0.
    assertEquals("^", Step0.rep("^"));
    assertEquals("_", Step0.rep("_"));
    assertEquals("`", Step0.rep("`"));
    assertEquals("~", Step0.rep("~"));

    // Optional Functionality
    assertEquals("#", Step0.rep("#"));
    assertEquals("$", Step0.rep("$"));
    assertEquals("%", Step0.rep("%"));
    assertEquals(".", Step0.rep("."));
    assertEquals("|", Step0.rep("|"));
  }

  @Test
  void step1() {
    // Testing read of numbers
    assertEquals("1", Step1.rep("1"));
    assertEquals("7", Step1.rep("7"));
    assertEquals("7", Step1.rep("  7   "));
    assertEquals("-123", Step1.rep("-123"));

    // Testing read of symbols
    assertEquals("+", Step1.rep("+"));
    assertEquals("abc", Step1.rep("abc"));
    assertEquals("abc", Step1.rep("   abc   "));
    assertEquals("abc5", Step1.rep("abc5"));
    assertEquals("abc-def", Step1.rep("abc-def"));

    // Testing non-numbers starting with a dash
    assertEquals("-", Step1.rep("-"));
    assertEquals("-abc", Step1.rep("-abc"));
    assertEquals("->>", Step1.rep("->>"));

    // Testing read of lists
    assertEquals("(+ 1 2)", Step1.rep("(+ 1 2)"));
    assertEquals("()", Step1.rep("()"));
    assertEquals("()", Step1.rep("( )"));
    assertEquals("(nil)", Step1.rep("(nil)"));
    assertEquals("((3 4))", Step1.rep("((3 4))"));
    assertEquals("(+ 1 (+ 2 3))", Step1.rep("(+ 1 (+ 2 3))"));
    assertEquals("(+ 1 (+ 2 3))", Step1.rep("  ( +   1   (+   2 3   )   )  "));
    assertEquals("(* 1 2)", Step1.rep("(* 1 2)"));
    assertEquals("(** 1 2)", Step1.rep("(** 1 2)"));
    assertEquals("(* -3 6)", Step1.rep("(* -3 6)"));
    assertEquals("(() ())", Step1.rep("(()())"));

    // Test commas as whitespace
    assertEquals("(1 2 3)", Step1.rep("(1 2, 3,,,,),,"));

    // Testing read of nil/true/false
    assertEquals("nil", Step1.rep("nil"));
    assertEquals("true", Step1.rep("true"));
    assertEquals("false", Step1.rep("false"));

    // Testing read of strings
    assertEquals("\"abc\"", Step1.rep("\"abc\""));
    assertEquals("\"abc\"", Step1.rep("   \"abc\"   "));
    assertEquals("\"abc (with parens)\"", Step1.rep("\"abc (with parens)\""));
    assertEquals("\"abc\\\"def\"", Step1.rep("\"abc\\\"def\""));
    assertEquals("\"\"", Step1.rep("\"\""));
    assertEquals("\"\\\\\"", Step1.rep("\"\\\\\""));
    assertEquals("\"\\\\\\\\\\\\\\\\\\\\\"", Step1.rep("\"\\\\\\\\\\\\\\\\\\\\\""));
    assertEquals("\"&\"", Step1.rep("\"&\""));
    assertEquals("\"'\"", Step1.rep("\"'\""));
    assertEquals("\"(\"", Step1.rep("\"(\""));
    assertEquals("\")\"", Step1.rep("\")\""));
    assertEquals("\"*\"", Step1.rep("\"*\""));
    assertEquals("\"+\"", Step1.rep("\"+\""));
    assertEquals("\",\"", Step1.rep("\",\""));
    assertEquals("\"-\"", Step1.rep("\"-\""));
    assertEquals("\"/\"", Step1.rep("\"/\""));
    assertEquals("\":\"", Step1.rep("\":\""));
    assertEquals("\";\"", Step1.rep("\";\""));
    assertEquals("\"<\"", Step1.rep("\"<\""));
    assertEquals("\"=\"", Step1.rep("\"=\""));
    assertEquals("\">\"", Step1.rep("\">\""));
    assertEquals("\"?\"", Step1.rep("\"?\""));
    assertEquals("\"@\"", Step1.rep("\"@\""));
    assertEquals("\"[\"", Step1.rep("\"[\""));
    assertEquals("\"]\"", Step1.rep("\"]\""));
    assertEquals("\"^\"", Step1.rep("\"^\""));
    assertEquals("\"_\"", Step1.rep("\"_\""));
    assertEquals("\"`\"", Step1.rep("\"`\""));
    assertEquals("\"{\"", Step1.rep("\"{\""));
    assertEquals("\"}\"", Step1.rep("\"}\""));
    assertEquals("\"~\"", Step1.rep("\"~\""));
    assertEquals("\"!\"", Step1.rep("\"!\""));

    // Testing reader errors
    assertThrows(MalException.class, () -> Step1.rep("(1 2"));
    assertThrows(MalException.class, () -> Step1.rep("[1 2"));
    assertThrows(MalException.class, () -> Step1.rep("{\"a\" 2"));
    assertThrows(MalException.class, () -> Step1.rep("\"abc"));
    assertThrows(MalException.class, () -> Step1.rep("\""));
    assertThrows(MalException.class, () -> Step1.rep("\"\\\""));
    assertThrows(MalException.class, () -> Step1.rep("\"\\\\\\\\\\\\\\\\\\\\\\\""));
    assertThrows(MalException.class, () -> Step1.rep("(1 \"abc"));
    assertThrows(MalException.class, () -> Step1.rep("(1 \"abc\""));

    // Testing read of quoting
    assertEquals("(quote 1)", Step1.rep("'1"));
    assertEquals("(quote (1 2 3))", Step1.rep("'(1 2 3)"));
    assertEquals("(quasiquote 1)", Step1.rep("`1"));
    assertEquals("(quasiquote (1 2 3))", Step1.rep("`(1 2 3)"));
    assertEquals("(quasiquote (a (b) c))", Step1.rep("`(a (b) c)"));
    assertEquals("(unquote 1)", Step1.rep("~1"));
    assertEquals("(unquote (1 2 3))", Step1.rep("~(1 2 3)"));
    assertEquals("(quasiquote (1 (unquote a) 3))", Step1.rep("`(1 ~a 3)"));
    assertEquals("(splice-unquote (1 2 3))", Step1.rep("~@(1 2 3)"));

    // Testing keywords
    assertEquals(":kw", Step1.rep(":kw"));
    assertEquals("(:kw1 :kw2 :kw3)", Step1.rep("(:kw1 :kw2 :kw3)"));

    // Testing read of vectors
    assertEquals("[+ 1 2]", Step1.rep("[+ 1 2]"));
    assertEquals("[]", Step1.rep("[]"));
    assertEquals("[]", Step1.rep("[ ]"));
    assertEquals("[[3 4]]", Step1.rep("[[3 4]]"));
    assertEquals("[+ 1 [+ 2 3]]", Step1.rep("[+ 1 [+ 2 3]]"));
    assertEquals("[+ 1 [+ 2 3]]", Step1.rep("[+ 1 [+ 2 3]]"));
    assertEquals("([])", Step1.rep("([])"));

    // Testing read of hash maps
    assertEquals("{}", Step1.rep("{}"));
    assertEquals("{}", Step1.rep("{ }"));
    assertEquals("{\"abc\" 1}", Step1.rep("{\"abc\" 1}"));
    assertEquals("{\"a\" {\"b\" 2}}", Step1.rep("{\"a\" {\"b\" 2}}"));
    assertEquals("{\"a\" {\"b\" {\"c\" 3}}}", Step1.rep("{\"a\" {\"b\" {\"c\" 3}}}"));
    assertEquals("{\"a\" {\"b\" {\"cde\" 3}}}", Step1.rep("{\"a\" {\"b\" {\"cde\" 3}}}"));
    assertEquals("{:a {:b {:cde 3}}}", Step1.rep("{  :a  {:b   {  :cde     3   }  }}"));
    assertEquals("{\"1\" 1}", Step1.rep("{\"1\" 1}"));
    assertEquals("({})", Step1.rep("({})"));

    // Testing read of comments
    assertEquals("1", Step1.rep("1 ; comment after expression"));
    assertEquals("1", Step1.rep("1; comment after expression"));

    // Testing read of @/deref
    assertEquals("(deref a)", Step1.rep("@a"));
    assertEquals("a:", Step1.rep("a:"));

    // Optional - Metadata
    assertEquals("(with-meta [1 2 3] {\"a\" 1})", Step1.rep("^{ \"a\" 1 } [1 2 3]"));
    assertEquals("(with-meta [1 2 3] 2)", Step1.rep("^2 [1 2 3]"));

    // Optional - Escaped characters
    assertEquals("\"\\n\"", Step1.rep("\"\\n\""));
    assertEquals("\"#\"", Step1.rep("\"#\""));
    assertEquals("\"$\"", Step1.rep("\"$\""));
    assertEquals("\"%\"", Step1.rep("\"%\""));
    assertEquals("\".\"", Step1.rep("\".\""));
    assertEquals("\"\\\\\"", Step1.rep("\"\\\\\""));
    assertEquals("\"|\"", Step1.rep("\"|\""));

    // Optional - Comments with symbols
    assertEquals("1", Step1.rep("1;!"));
    assertEquals("1", Step1.rep("1;\""));
    assertEquals("1", Step1.rep("1; &()*+,-./:;<=>?@[]^_{|}~"));
  }

  @Test
  void step2() {
    // Testing evaluation of arithmetic operations
    assertEquals("3", Step2.rep("(+ 1 2)"));
    assertEquals("1", Step2.rep("(% 5 2)"));
    assertEquals("0", Step2.rep("(% 15 5)"));
    assertEquals("11", Step2.rep("(+ 5 (* 2 3))"));
    assertEquals("8", Step2.rep("(- (+ 5 (* 2 3)) 3)"));
    assertEquals("2", Step2.rep("(/ (- (+ 5 (* 2 3)) 3) 4)"));
    assertEquals("1010", Step2.rep("(/ (- (+ 515 (* 87 311)) 302) 27)"));
    assertEquals("-18", Step2.rep("(* -3 6)"));
    assertEquals("-994", Step2.rep("(/ (- (+ 515 (* -87 311)) 296) 27)"));

    // This should throw an error with no return value
    assertThrows(MalException.class, () -> Step2.rep("(abc 1 2 3)"));

    // Testing empty list
    assertEquals("()", Step2.rep("()"));

    // -------- Deferrable Functionality --------

    // Testing nil inside vector
    assertEquals("[nil]", Step2.rep("[nil]"));

    // Testing evaluation within collection literals
    assertEquals("[1 2 3]", Step2.rep("[1 2 (+ 1 2)]"));
    assertEquals("{\"a\" 15}", Step2.rep("{\"a\" (+ 7 8)}"));
    assertEquals("{:a 15}", Step2.rep("{:a (+ 7 8)}"));

    // Check that evaluation hasn't broken empty collections
    assertEquals("[]", Step2.rep("[]"));
    assertEquals("{}", Step2.rep("{}"));
  }

  @Test
  void step3() {
    // Testing REPL_ENV
    assertEquals("3", Step3.rep("(+ 1 2)"));
    assertEquals("2", Step3.rep("(/ (- (+ 5 (* 2 3)) 3) 4)"));

    // Testing def!
    assertEquals("3", Step3.rep("(def! x 3)"));
    assertEquals("3", Step3.rep("x"));
    assertEquals("4", Step3.rep("(def! x 4)"));
    assertEquals("4", Step3.rep("x"));
    assertEquals("8", Step3.rep("(def! y (+ 1 7))"));
    assertEquals("8", Step3.rep("y"));

    // Verifying symbols are case-sensitive
    assertEquals("111", Step3.rep("(def! mynum 111)"));
    assertEquals("222", Step3.rep("(def! MYNUM 222)"));
    assertEquals("111", Step3.rep("mynum"));
    assertEquals("222", Step3.rep("MYNUM"));

    // Check env lookup non-fatal error
    assertThrows(MalException.class, () -> Step3.rep("(abc 1 2 3)"));

    // Check that error aborts def!
    Step3.rep("(def! w 123)");
    assertThrows(MalException.class, () -> Step3.rep("(def! w (abc))"));
    assertEquals("123", Step3.rep("w"));

    // Testing let*
    assertEquals("9", Step3.rep("(let* (z 9) z)"));
    assertEquals("9", Step3.rep("(let* (x 9) x)"));
    assertEquals("4", Step3.rep("x"));
    assertEquals("6", Step3.rep("(let* (z (+ 2 3)) (+ 1 z))"));
    assertEquals("12", Step3.rep("(let* (p (+ 2 3) q (+ 2 p)) (+ p q))"));
    Step3.rep("(def! y (let* (z 7) z))");
    assertEquals("7", Step3.rep("y"));

    // Testing outer environment
    assertEquals("4", Step3.rep("(def! a 4)"));
    assertEquals("9", Step3.rep("(let* (q 9) q)"));
    assertEquals("4", Step3.rep("(let* (q 9) a)"));
    assertEquals("4", Step3.rep("(let* (z 2) (let* (q 9) a))"));

    // -------- Deferrable Functionality --------

    // Testing let* with vector bindings
    assertEquals("9", Step3.rep("(let* [z 9] z)"));
    assertEquals("12", Step3.rep("(let* [p (+ 2 3) q (+ 2 p)] (+ p q))"));

    // Testing vector evaluation
    assertEquals("[3 4 5 [6 7] 8]", Step3.rep("(let* (a 5 b 6) [3 4 a [b 7] 8])"));

    // -------- Optional Functionality --------

    // Check that last assignment takes priority
    assertEquals("3", Step3.rep("(let* (x 2 x 3) x)"));

    // Check DEBUG-EVAL
    assertEquals("2", Step3.rep("(let* (DEBUG-EVAL false) (- 3 1))"));
    assertEquals("2", Step3.rep("(let* (DEBUG-EVAL nil) (- 3 1))"));

    // DEBUG-EVAL regex checks capturing stdout
    assertStdoutMatches("(?s)EVAL: \\(- a b\\).*\\n1", () -> Step3.rep("(let* (a 3 b 2 DEBUG-EVAL true) (- a b))"));
    assertStdoutMatches("(?s)EVAL: \"a\".*\\n\"a\"", () -> Step3.rep("(let* (DEBUG-EVAL 1) \"a\")"));
    assertStdoutMatches("(?s)EVAL: a.*\\n3", () -> Step3.rep("(let* (a 3 DEBUG-EVAL ()) a)"));
    assertStdoutMatches("(?s)EVAL: a.*\\n3", () -> Step3.rep("(let* (a 3 DEBUG-EVAL 0) a)"));
    assertStdoutMatches("(?s)EVAL: a.*\\n3", () -> Step3.rep("(let* (a 3 DEBUG-EVAL \"\") a)"));
  }

  @Test
  void step4() {
    Step4.init();

    // Testing list functions
    assertEquals("()", Step4.rep("(list)"));
    assertEquals("true", Step4.rep("(list? (list))"));
    assertEquals("false", Step4.rep("(list? nil)"));
    assertEquals("true", Step4.rep("(empty? (list))"));
    assertEquals("false", Step4.rep("(empty? (list 1))"));
    assertEquals("(1 2 3)", Step4.rep("(list 1 2 3)"));
    assertEquals("3", Step4.rep("(count (list 1 2 3))"));
    assertEquals("0", Step4.rep("(count (list))"));
    assertEquals("0", Step4.rep("(count nil)"));
    assertEquals("78", Step4.rep("(if (> (count (list 1 2 3)) 3) 89 78)"));
    assertEquals("89", Step4.rep("(if (>= (count (list 1 2 3)) 3) 89 78)"));

    // Testing if form
    assertEquals("7", Step4.rep("(if true 7 8)"));
    assertEquals("8", Step4.rep("(if false 7 8)"));
    assertEquals("false", Step4.rep("(if false 7 false)"));
    assertEquals("8", Step4.rep("(if true (+ 1 7) (+ 1 8))"));
    assertEquals("9", Step4.rep("(if false (+ 1 7) (+ 1 8))"));
    assertEquals("8", Step4.rep("(if nil 7 8)"));
    assertEquals("7", Step4.rep("(if 0 7 8)"));
    assertEquals("7", Step4.rep("(if (list) 7 8)"));
    assertEquals("7", Step4.rep("(if (list 1 2 3) 7 8)"));
    assertEquals("false", Step4.rep("(= (list) nil)"));

    // Testing 1-way if form
    assertEquals("nil", Step4.rep("(if false (+ 1 7))"));
    assertEquals("nil", Step4.rep("(if nil 8)"));
    assertEquals("7", Step4.rep("(if nil 8 7)"));
    assertEquals("8", Step4.rep("(if true (+ 1 7))"));

    // Testing basic conditionals
    assertEquals("false", Step4.rep("(= 2 1)"));
    assertEquals("true", Step4.rep("(= 1 1)"));
    assertEquals("true", Step4.rep("(> 2 1)"));
    assertEquals("false", Step4.rep("(< 2 1)"));
    assertEquals("true", Step4.rep("(<= 1 1)"));

    // Testing equality and representation of nil false true
    assertEquals("true", Step4.rep("(= nil nil)"));
    assertEquals("false", Step4.rep("(= nil false)"));
    assertEquals("true", Step4.rep("(= (list) (list))"));
    assertEquals("true", Step4.rep("(= (list) ())"));

    // Testing builtin and user defined functions
    assertEquals("7", Step4.rep("( (fn* (a b) (+ b a)) 3 4)"));
    assertEquals("4", Step4.rep("( (fn* () 4) )"));
    assertEquals("8", Step4.rep("( (fn* (f x) (f x)) (fn* (a) (+ 1 a)) 7)"));

    // Testing closures
    assertEquals("12", Step4.rep("( ( (fn* (a) (fn* (b) (+ a b))) 5) 7)"));
    Step4.rep("(def! gen-plusX (fn* (x) (fn* (b) (+ x b))))");
    Step4.rep("(def! plus7 (gen-plusX 7))");
    assertEquals("15", Step4.rep("(plus7 8)"));

    // Testing do form
    assertStdoutMatches("(?s)101\\nnil", () -> Step4.rep("(do (prn 101))"));
    assertStdoutMatches("(?s)101\\n102\\n3", () -> Step4.rep("(do (prn 101) (prn 102) (+ 1 2))"));

    // Testing recursion
    Step4.rep("(def! sumdown (fn* (N) (if (> N 0) (+ N (sumdown (- N 1))) 0)))");
    assertEquals("21", Step4.rep("(sumdown 6)"));

    // -------- Deferrable Functionality --------
    assertEquals("7", Step4.rep("(if \"\" 7 8)"));
    assertEquals("true", Step4.rep("(= \"abc\" \"abc\")"));
    assertEquals("false", Step4.rep("(= \"abc\" \"ABC\")"));

    // Variable length arguments
    assertEquals("3", Step4.rep("( (fn* (& more) (count more)) 1 2 3)"));
    assertEquals("0", Step4.rep("( (fn* (& more) (count more)) )"));
    assertEquals("2", Step4.rep("( (fn* (a & more) (count more)) 1 2 3)"));

    // Testing not function
    assertEquals("true", Step4.rep("(not false)"));
    assertEquals("true", Step4.rep("(not nil)"));
    assertEquals("false", Step4.rep("(not true)"));

    // Testing pr-str and str
    assertEquals("\"\\\"abc\\\"\"", Step4.rep("(pr-str \"abc\")"));
    assertEquals("\"(1 2 \\\"abc\\\" \\\"\\\\\\\"\\\") \\\"def\\\"\"", Step4.rep("(pr-str (list 1 2 \"abc\" \"\\\"\") \"def\")"));
    assertEquals("\"1abc3\"", Step4.rep("(str 1 \"abc\" 3)"));

    // Testing prn and println
    assertStdoutMatches("(?s)\"abc\"\\nnil", () -> Step4.rep("(prn \"abc\")"));
    assertStdoutMatches("(?s)abc\\nnil", () -> Step4.rep("(println \"abc\")"));
    assertStdoutMatches("(?s)abc\\ndef\\nghi\\nnil", () -> Step4.rep("(println \"abc\\ndef\\nghi\")"));

    // Testing vector functions and equality
    assertEquals("3", Step4.rep("(count [1 2 3])"));
    assertEquals("true", Step4.rep("(= [] (list))"));
    assertEquals("true", Step4.rep("(= (list 1 2) [1 2])"));
    assertEquals("8", Step4.rep("( (fn* [f x] (f x)) (fn* [a] (+ 1 a)) 7)"));

    // Nested vector/list equality
    assertEquals("true", Step4.rep("(= [(list)] (list []))"));
    assertEquals("true", Step4.rep("(= [1 2 (list 3 4 [5 6])] (list 1 2 [3 4 (list 5 6)]))"));
  }

  @Test
  void step5() {
    Step5.init();

    // Testing recursive tail-call function
    Step5.rep("(def! sum2 (fn* (n acc) (if (= n 0) acc (sum2 (- n 1) (+ n acc)))))");

    assertEquals("55", Step5.rep("(sum2 10 0)"));

    assertEquals("nil", Step5.rep("(def! res2 nil)"));

    // Testing large recursion to verify TCO
    assertEquals("50005000", Step5.rep("(def! res2 (sum2 10000 0))"));
    assertEquals("50005000", Step5.rep("res2"));

    // Test mutually recursive tail-call functions
    Step5.rep("(def! foo (fn* (n) (if (= n 0) 0 (bar (- n 1)))))");
    Step5.rep("(def! bar (fn* (n) (if (= n 0) 0 (foo (- n 1)))))");

    // Verification of TCO through mutual recursion
    assertEquals("0", Step5.rep("(foo 10000)"));
  }

  @Test
  void step6() {
    Step6.init();

    // Testing that (do (do)) not broken by TCO
    assertEquals("2", Step6.rep("(do (do 1 2))"));

    // Testing read-string, eval and slurp
    assertEquals("(1 2 (3 4) nil)", Step6.rep("(read-string \"(1 2 (3 4) nil)\")"));
    assertEquals("true", Step6.rep("(= nil (read-string \"nil\"))"));
    assertEquals("(+ 2 3)", Step6.rep("(read-string \"(+ 2 3)\")"));
    assertEquals("\"\\n\"", Step6.rep("(read-string \"\\\"\\n\\\"\")"));
    assertEquals("7", Step6.rep("(read-string \"7 ;; comment\")"));

    // Ensure eval works with read-string
    assertEquals("5", Step6.rep("(eval (read-string \"(+ 2 3)\"))"));

    // Testing slurp (assuming test files are in the expected relative path)
    assertEquals("\"A line of text\\n\"", Step6.rep("(slurp \"../tests/test.txt\")"));

    // Testing load-file
    assertEquals("nil", Step6.rep("(load-file \"../tests/inc.mal\")"));
    assertEquals("8", Step6.rep("(inc1 7)"));
    assertEquals("9", Step6.rep("(inc2 7)"));
    assertEquals("12", Step6.rep("(inc3 9)"));

    // Testing atoms
    Step6.rep("(def! inc3 (fn* (a) (+ 3 a)))");
    assertEquals("(atom 2)", Step6.rep("(def! a (atom 2))"));
    assertEquals("true", Step6.rep("(atom? a)"));
    assertEquals("false", Step6.rep("(atom? 1)"));
    assertEquals("2", Step6.rep("(deref a)"));
    assertEquals("3", Step6.rep("(reset! a 3)"));
    assertEquals("6", Step6.rep("(swap! a inc3)"));
    assertEquals("12", Step6.rep("(swap! a (fn* (a) (* 2 a)))"));
    assertEquals("120", Step6.rep("(swap! a (fn* (a b) (* a b)) 10)"));
    assertEquals("123", Step6.rep("(swap! a + 3)"));

    // Test that do only evals each slot once
    Step6.rep("(def! b (atom 0))");
    Step6.rep("(do (swap! b + 1) (swap! b + 10) (swap! b + 100))");
    assertEquals("111", Step6.rep("(deref b)"));

    // Testing swap!/closure interaction
    Step6.rep("(def! inc-it (fn* (a) (+ 1 a)))");
    Step6.rep("(def! atm (atom 7))");
    Step6.rep("(def! f (fn* () (swap! atm inc-it)))");
    assertEquals("8", Step6.rep("(f)"));
    assertEquals("9", Step6.rep("(f)"));

    // -------- Deferrable Functionality --------

    // Testing read-string parsing errors via regex
    assertThrows(MalException.class, () -> Step6.rep("(read-string \"(+ 1\")"));

    // Testing reading of large files
    assertEquals("nil", Step6.rep("(load-file \"../tests/computations.mal\")"));
    assertEquals("3", Step6.rep("(sumdown 2)"));
    assertEquals("1", Step6.rep("(fib 2)"));

    // Testing @ reader macro
    Step6.rep("(def! atm (atom 9))");
    assertEquals("9", Step6.rep("@atm"));

    // Testing *ARGV*
    assertEquals("true", Step6.rep("(list? *ARGV*)"));
    assertEquals("()", Step6.rep("*ARGV*"));

    // Testing eval scope
    assertEquals("7", Step6.rep("(let* (b 12) (do (eval (read-string \"(def! aa 7)\")) aa ))"));

    // -------- Optional Functionality --------

    // Testing eval does not use local environments
    Step6.rep("(def! a 1)");
    assertEquals("1", Step6.rep("(let* (a 2) (eval (read-string \"a\")))"));

    // Non alphanumeric characters in comments in read-string
    assertEquals("1", Step6.rep("(read-string \"1;!\")"));
    assertEquals("1", Step6.rep("(read-string \"1;\\\"\")"));
    assertEquals("1", Step6.rep("(read-string \"1;`\")"));
  }

  @Test
  void step7() {
    Step7.init();

    // Testing cons function
    assertEquals("(1)", Step7.rep("(cons 1 (list))"));
    assertEquals("(1 2)", Step7.rep("(cons 1 (list 2))"));
    assertEquals("(1 2 3)", Step7.rep("(cons 1 (list 2 3))"));
    assertEquals("((1) 2 3)", Step7.rep("(cons (list 1) (list 2 3))"));

    Step7.rep("(def! a (list 2 3))");
    assertEquals("(1 2 3)", Step7.rep("(cons 1 a)"));
    assertEquals("(2 3)", Step7.rep("a"));

    // Testing concat function
    assertEquals("()", Step7.rep("(concat)"));
    assertEquals("(1 2)", Step7.rep("(concat (list 1 2))"));
    assertEquals("(1 2 3 4)", Step7.rep("(concat (list 1 2) (list 3 4))"));
    assertEquals("(1 2 3 4 5 6)", Step7.rep("(concat (list 1 2) (list 3 4) (list 5 6))"));
    assertEquals("()", Step7.rep("(concat (concat))"));
    assertEquals("()", Step7.rep("(concat (list) (list))"));
    assertEquals("true", Step7.rep("(= () (concat))"));

    Step7.rep("(def! a (list 1 2))");
    Step7.rep("(def! b (list 3 4))");
    assertEquals("(1 2 3 4 5 6)", Step7.rep("(concat a b (list 5 6))"));
    assertEquals("(1 2)", Step7.rep("a"));
    assertEquals("(3 4)", Step7.rep("b"));

    // Testing regular quote
    assertEquals("7", Step7.rep("(quote 7)"));
    assertEquals("(1 2 3)", Step7.rep("(quote (1 2 3))"));
    assertEquals("(1 2 (3 4))", Step7.rep("(quote (1 2 (3 4)))"));

    // Testing simple quasiquote
    assertEquals("nil", Step7.rep("(quasiquote nil)"));
    assertEquals("7", Step7.rep("(quasiquote 7)"));
    assertEquals("a", Step7.rep("(quasiquote a)"));
    assertEquals("{\"a\" b}", Step7.rep("(quasiquote {\"a\" b})"));

    // Testing quasiquote with lists
    assertEquals("()", Step7.rep("(quasiquote ())"));
    assertEquals("(1 2 3)", Step7.rep("(quasiquote (1 2 3))"));
    assertEquals("(a)", Step7.rep("(quasiquote (a))"));
    assertEquals("(1 2 (3 4))", Step7.rep("(quasiquote (1 2 (3 4)))"));
    assertEquals("(nil)", Step7.rep("(quasiquote (nil))"));
    assertEquals("(1 ())", Step7.rep("(quasiquote (1 ()))"));
    assertEquals("(() 1)", Step7.rep("(quasiquote (() 1))"));
    assertEquals("(1 () 2)", Step7.rep("(quasiquote (1 () 2))"));
    assertEquals("(())", Step7.rep("(quasiquote (()))"));

    // Testing unquote
    assertEquals("7", Step7.rep("(quasiquote (unquote 7))"));
    Step7.rep("(def! a 8)");
    assertEquals("a", Step7.rep("(quasiquote a)"));
    assertEquals("8", Step7.rep("(quasiquote (unquote a))"));
    assertEquals("(1 a 3)", Step7.rep("(quasiquote (1 a 3))"));
    assertEquals("(1 8 3)", Step7.rep("(quasiquote (1 (unquote a) 3))"));

    Step7.rep("(def! b (quote (1 \"b\" \"d\")))");
    assertEquals("(1 b 3)", Step7.rep("(quasiquote (1 b 3))"));
    assertEquals("(1 (1 \"b\" \"d\") 3)", Step7.rep("(quasiquote (1 (unquote b) 3))"));
    assertEquals("(1 2)", Step7.rep("(quasiquote ((unquote 1) (unquote 2)))"));

    // Quasiquote and environments
    assertEquals("0", Step7.rep("(let* (x 0) (quasiquote (unquote x)))"));

    // Testing splice-unquote
    Step7.rep("(def! c (quote (1 \"b\" \"d\")))");
    assertEquals("(1 c 3)", Step7.rep("(quasiquote (1 c 3))"));
    assertEquals("(1 1 \"b\" \"d\" 3)", Step7.rep("(quasiquote (1 (splice-unquote c) 3))"));
    assertEquals("(1 1 \"b\" \"d\")", Step7.rep("(quasiquote (1 (splice-unquote c)))"));
    assertEquals("(1 \"b\" \"d\" 2)", Step7.rep("(quasiquote ((splice-unquote c) 2))"));
    assertEquals("(1 \"b\" \"d\" 1 \"b\" \"d\")", Step7.rep("(quasiquote ((splice-unquote c) (splice-unquote c)))"));

    // Testing symbol equality
    assertEquals("true", Step7.rep("(= (quote abc) (quote abc))"));
    assertEquals("false", Step7.rep("(= (quote abc) (quote abcd))"));
    assertEquals("false", Step7.rep("(= (quote abc) \"abc\")"));
    assertEquals("false", Step7.rep("(= \"abc\" (quote abc))"));
    assertEquals("true", Step7.rep("(= \"abc\" (str (quote abc)))"));
    assertEquals("false", Step7.rep("(= (quote abc) nil)"));
    assertEquals("false", Step7.rep("(= nil (quote abc))"));

    // -------- Deferrable Functionality --------

    // Testing ' (quote) reader macro
    assertEquals("7", Step7.rep("'7"));
    assertEquals("(1 2 3)", Step7.rep("'(1 2 3)"));
    assertEquals("(1 2 (3 4))", Step7.rep("'(1 2 (3 4))"));

    // Testing cons and concat with vectors
    assertEquals("(1)", Step7.rep("(cons 1 [])"));
    assertEquals("([1] 2 3)", Step7.rep("(cons [1] [2 3])"));
    assertEquals("(1 2 3)", Step7.rep("(cons 1 [2 3])"));
    assertEquals("(1 2 3 4 5 6)", Step7.rep("(concat [1 2] (list 3 4) [5 6])"));
    assertEquals("(1 2)", Step7.rep("(concat [1 2])"));

    // -------- Optional Functionality --------

    // Testing ` (quasiquote) reader macro
    assertEquals("7", Step7.rep("`7"));
    assertEquals("(1 2 3)", Step7.rep("`(1 2 3)"));
    assertEquals("(1 2 (3 4))", Step7.rep("`(1 2 (3 4))"));
    assertEquals("(nil)", Step7.rep("`(nil)"));

    // Testing ~ (unquote) reader macro
    assertEquals("7", Step7.rep("`~7"));
    Step7.rep("(def! a 8)");
    assertEquals("(1 8 3)", Step7.rep("`(1 ~a 3)"));
    Step7.rep("(def! b '(1 \"b\" \"d\"))");
    assertEquals("(1 b 3)", Step7.rep("`(1 b 3)"));
    assertEquals("(1 (1 \"b\" \"d\") 3)", Step7.rep("`(1 ~b 3)"));

    // Testing ~@ (splice-unquote) reader macro
    Step7.rep("(def! c '(1 \"b\" \"d\"))");
    assertEquals("(1 c 3)", Step7.rep("`(1 c 3)"));
    assertEquals("(1 1 \"b\" \"d\" 3)", Step7.rep("`(1 ~@c 3)"));

    // Testing vec function
    assertEquals("[]", Step7.rep("(vec (list))"));
    assertEquals("[1]", Step7.rep("(vec (list 1))"));
    assertEquals("[1 2]", Step7.rep("(vec (list 1 2))"));
    assertEquals("[]", Step7.rep("(vec [])"));
    assertEquals("[1 2]", Step7.rep("(vec [1 2])"));

    // Testing that vec does not mutate the original list
    Step7.rep("(def! a (list 1 2))");
    assertEquals("[1 2]", Step7.rep("(vec a)"));
    assertEquals("(1 2)", Step7.rep("a"));

    // Test quine
    String quine = "((fn* (q) (quasiquote ((unquote q) (quote (unquote q))))) (quote (fn* (q) (quasiquote ((unquote q) (quote (unquote q)))))))";
    assertEquals(quine, Step7.rep(quine));

    // Testing quasiquote with vectors
    assertEquals("[]", Step7.rep("(quasiquote [])"));
    assertEquals("[[]]", Step7.rep("(quasiquote [[]])"));
    assertEquals("[()]", Step7.rep("(quasiquote [()])"));
    assertEquals("([])", Step7.rep("(quasiquote ([]))"));
    Step7.rep("(def! a 8)");
    assertEquals("[1 a 3]", Step7.rep("`[1 a 3]"));
    assertEquals("[a [] b [c] d [e f] g]", Step7.rep("(quasiquote [a [] b [c] d [e f] g])"));

    // Testing unquote with vectors
    assertEquals("[8]", Step7.rep("`[~a]"));
    assertEquals("[(8)]", Step7.rep("`[(~a)]"));
    assertEquals("([8])", Step7.rep("`([~a])"));
    assertEquals("[a 8 a]", Step7.rep("`[a ~a a]"));
    assertEquals("([a 8 a])", Step7.rep("`([a ~a a])"));
    assertEquals("[(a 8 a)]", Step7.rep("`[(a ~a a)]"));

    // Testing splice-unquote with vectors
    Step7.rep("(def! c '(1 \"b\" \"d\"))");
    assertEquals("[1 \"b\" \"d\"]", Step7.rep("`[~@c]"));
    assertEquals("[(1 \"b\" \"d\")]", Step7.rep("`[(~@c)]"));
    assertEquals("([1 \"b\" \"d\"])", Step7.rep("`([~@c])"));
    assertEquals("[1 1 \"b\" \"d\" 3]", Step7.rep("`[1 ~@c 3]"));
    assertEquals("([1 1 \"b\" \"d\" 3])", Step7.rep("`([1 ~@c 3])"));
    assertEquals("[(1 1 \"b\" \"d\" 3)]", Step7.rep("`[(1 ~@c 3)]"));

    // Misplaced unquote or splice-unquote
    assertEquals("(0 unquote)", Step7.rep("`(0 unquote)"));
    assertEquals("(0 splice-unquote)", Step7.rep("`(0 splice-unquote)"));
    assertEquals("[unquote 0]", Step7.rep("`[unquote 0]"));
    assertEquals("[splice-unquote 0]", Step7.rep("`[splice-unquote 0]"));
    assertEquals("(0 unquote 1)", Step7.rep("`(0 unquote 1)"));
    assertEquals("(0 splice-unquote ())", Step7.rep("`(0 splice-unquote ())"));

    // DEBUG-EVAL Trace checks
    assertStdoutMatches("(?s)EVAL: \\(quasiquote nil\\).*\\nEVAL: nil.*\\nnil", () -> Step7.rep("(let* (DEBUG-EVAL true) `nil)"));
    assertStdoutMatches("(?s)EVAL: \\(quasiquote 7\\).*\\nEVAL: 7.*\\n7", () -> Step7.rep("(let* (DEBUG-EVAL true) `7)"));
    assertStdoutMatches("(?s)EVAL: \\(quasiquote a\\).*\\nEVAL: \\(quote a\\).*\\na", () -> Step7.rep("(let* (DEBUG-EVAL true) `a)"));
    assertStdoutMatches("(?s)EVAL: \\(quasiquote \\{\"a\" b\\}\\).*\\nEVAL: \\(quote \\{\"a\" b\\}\\).*\\n\\{\"a\" b\\}", () -> Step7.rep("(let* (DEBUG-EVAL true) `{\"a\" b})"));
    assertStdoutMatches("(?s)EVAL: \\(quasiquote \\(\\)\\).*\\nEVAL: \\(\\).*\\n\\(\\)", () -> Step7.rep("(let* (DEBUG-EVAL true) `())"));
    assertStdoutMatches("(?s)EVAL: \\(quasiquote \\(a 2\\)\\).*\\nEVAL: \\(cons \\(quote a\\) \\(cons 2 \\(\\)\\)\\).*\\n\\(a 2\\)", () -> Step7.rep("(let* (DEBUG-EVAL true) `(a 2))"));
    assertStdoutMatches("(?s)EVAL: \\(quasiquote \\(\\(unquote a\\) 3\\)\\).*\\nEVAL: \\(cons a \\(cons 3 \\(\\)\\)\\).*\\n\\(8 3\\)", () -> Step7.rep("(let* (DEBUG-EVAL true) `(~a 3))"));
    assertStdoutMatches("(?s)EVAL: \\(quasiquote \\(1 \\(splice-unquote c\\) 3\\)\\).*\\nEVAL: \\(cons 1 \\(concat c \\(cons 3 \\(\\)\\)\\)\\).*\\n\\(1 1 \"b\" \"d\" 3\\)", () -> Step7.rep("(let* (DEBUG-EVAL true) `(1 ~@c 3))"));
    assertStdoutMatches("(?s)EVAL: \\(quasiquote \\[\\]\\).*\\nEVAL: \\(vec \\(\\)\\).*\\n\\[\\]", () -> Step7.rep("(let* (DEBUG-EVAL true) `[])"));
  }

  @Test
  void step8() {
    Step8.init();

    // Testing trivial macros
    Step8.rep("(defmacro! one (fn* () 1))");
    assertEquals("1", Step8.rep("(one)"));
    Step8.rep("(defmacro! two (fn* () 2))");
    assertEquals("2", Step8.rep("(two)"));

    // Testing unless macros
    Step8.rep("(defmacro! unless (fn* (pred a b) `(if ~pred ~b ~a)))");
    assertEquals("7", Step8.rep("(unless false 7 8)"));
    assertEquals("8", Step8.rep("(unless true 7 8)"));
    Step8.rep("(defmacro! unless2 (fn* (pred a b) (list 'if (list 'not pred) a b)))");
    assertEquals("7", Step8.rep("(unless2 false 7 8)"));
    assertEquals("8", Step8.rep("(unless2 true 7 8)"));

    // Testing evaluation of macro result
    Step8.rep("(defmacro! identity (fn* (x) x))");
    assertEquals("123", Step8.rep("(let* (a 123) (identity a))"));

    // Test that macros do not break empty list or quasiquote
    assertEquals("()", Step8.rep("()"));
    assertEquals("(1)", Step8.rep("`(1)"));

    // -------- Deferrable Functionality --------

    // Testing non-macro function
    assertEquals("false", Step8.rep("(not (= 1 1))"));
    assertEquals("true", Step8.rep("(not (= 1 2))"));

    // Testing nth, first and rest functions
    assertEquals("1", Step8.rep("(nth (list 1) 0)"));
    assertEquals("2", Step8.rep("(nth (list 1 2) 1)"));
    assertEquals("nil", Step8.rep("(nth (list 1 2 nil) 2)"));
    Step8.rep("(def! x \"x\")");
    assertThrows(MalException.class, () -> Step8.rep("(def! x (nth (list 1 2) 2))"));
    assertEquals("\"x\"", Step8.rep("x"));

    assertEquals("nil", Step8.rep("(first (list))"));
    assertEquals("6", Step8.rep("(first (list 6))"));
    assertEquals("7", Step8.rep("(first (list 7 8 9))"));

    assertEquals("()", Step8.rep("(rest nil)"));
    assertEquals("()", Step8.rep("(rest (list))"));
    assertEquals("()", Step8.rep("(rest (list 6))"));
    assertEquals("(8 9)", Step8.rep("(rest (list 7 8 9))"));

    // Testing cond macro
    assertEquals("nil", Step8.rep("(cond)"));
    assertEquals("7", Step8.rep("(cond true 7)"));
    assertEquals("nil", Step8.rep("(cond false 7)"));
    assertEquals("7", Step8.rep("(cond true 7 true 8)"));
    assertEquals("8", Step8.rep("(cond false 7 true 8)"));
    assertEquals("9", Step8.rep("(cond false 7 false 8 \"else\" 9)"));
    assertEquals("8", Step8.rep("(cond false 7 (= 2 2) 8 \"else\" 9)"));

    // Testing EVAL in let*
    assertEquals("\"yes\"", Step8.rep("(let* (x (cond false \"no\" true \"yes\")) x)"));

    // Testing nth, first, rest with vectors
    assertEquals("1", Step8.rep("(nth [1] 0)"));
    assertEquals("2", Step8.rep("(nth [1 2] 1)"));
    assertEquals("nil", Step8.rep("(first [])"));
    assertEquals("nil", Step8.rep("(first nil)"));
    assertEquals("10", Step8.rep("(first [10 11 12])"));
    assertEquals("()", Step8.rep("(rest [])"));
    assertEquals("(11 12)", Step8.rep("(rest [10 11 12])"));

    // Testing EVAL in vector let*
    assertEquals("\"yes\"", Step8.rep("(let* [x (cond false \"no\" true \"yes\")] x)"));

    // Test return value of defmacro!
    assertEquals("true", Step8.rep("(let* [m (defmacro! _ (fn* [] 1))] (macro? m))"));

    // -------- Optional Functionality --------

    // Test that macros use closures
    Step8.rep("(def! x 2)");
    Step8.rep("(defmacro! a (fn* [] x))");
    assertEquals("2", Step8.rep("(a)"));
    assertEquals("2", Step8.rep("(let* (x 3) (a))"));

    // DEBUG-EVAL Trace checks for macro expansion
    assertStdoutMatches("(?s).*\\nEVAL: \\(if x \\(- 4 3\\) foo\\).*\\n1", () -> Step8.rep("(let* (DEBUG-EVAL true) (unless x foo (- 4 3)))"));
    assertStdoutMatches("(?s).*\\nEVAL: \\(if \\(not x\\) foo \\(- 4 3\\)\\).*\\n1", () -> Step8.rep("(let* (DEBUG-EVAL true) (unless2 x foo (- 4 3)))"));
    assertStdoutMatches("(?s).*\\nEVAL: \\(if x \\(- 4 3\\) \\(cond foo bar\\)\\).*\\n1", () -> Step8.rep("(let* (DEBUG-EVAL true) (cond x (- 4 3) foo bar))"));
  }

  @Test
  void step9() {
    Step9.init();

    // Testing throw
    assertThrows(MalException.class, () -> Step9.rep("(throw \"err1\")"));

    // Testing try*/catch*
    assertEquals("123", Step9.rep("(try* 123 (catch* e 456))"));
    assertStdoutMatches("(?s)\"exc is:\" \"'?abc'? not found\"\\nnil", () -> Step9.rep("(try* abc (catch* exc (prn \"exc is:\" exc)))"));
    assertStdoutMatches("(?s)\"exc is:\" \"'?abc'? not found\"\\nnil", () -> Step9.rep("(try* (abc 1 2) (catch* exc (prn \"exc is:\" exc)))"));

    // Make sure error from core can be caught
    assertStdoutMatches("(?s)\"exc is:\".*(length|range|[Bb]ounds|beyond).*\\nnil", () -> Step9.rep("(try* (nth () 1) (catch* exc (prn \"exc is:\" exc)))"));

    // Make sure no double eval (no TCO from try block)
    assertEquals("(1)", Step9.rep("(try* (list 1) (catch* exc (prn \"exc is:\" exc)))"));
    assertStdoutMatches("(?s)\"exc:\" \"my exception\"\\n7", () -> Step9.rep("(try* (throw \"my exception\") (catch* exc (do (prn \"exc:\" exc) 7)))"));

    // Test that exception handlers get restored correctly
    assertEquals("\"c2\"", Step9.rep("(try* (do (try* \"t1\" (catch* e \"c1\")) (throw \"e1\")) (catch* e \"c2\"))"));
    assertEquals("\"c2\"", Step9.rep("(try* (try* (throw \"e1\") (catch* e (throw \"e2\"))) (catch* e \"c2\"))"));

    // Test that throw is a function
    assertEquals("\"my err\"", Step9.rep("(try* (map throw (list \"my err\")) (catch* exc exc))"));

    // Testing builtin functions
    assertEquals("true", Step9.rep("(symbol? 'abc)"));
    assertEquals("false", Step9.rep("(symbol? \"abc\")"));
    assertEquals("true", Step9.rep("(nil? nil)"));
    assertEquals("false", Step9.rep("(nil? false)"));
    assertEquals("true", Step9.rep("(true? true)"));
    assertEquals("false", Step9.rep("(true? 1)"));
    assertEquals("true", Step9.rep("(false? false)"));
    assertEquals("false", Step9.rep("(false? nil)"));

    // Testing apply function
    assertEquals("5", Step9.rep("(apply + (list 2 3))"));
    assertEquals("9", Step9.rep("(apply + 4 (list 5))"));
    assertStdoutMatches("(?s)1 2 \"3\" \\(\\)\\nnil", () -> Step9.rep("(apply prn (list 1 2 \"3\" (list)))"));
    assertEquals("()", Step9.rep("(apply list (list))"));
    assertEquals("true", Step9.rep("(apply symbol? (list (quote two)))"));

    // Testing apply with user functions and macros
    assertEquals("5", Step9.rep("(apply (fn* (a b) (+ a b)) (list 2 3))"));
    Step9.rep("(defmacro! m (fn* [a b] (+ a b)))");
    assertEquals("5", Step9.rep("(apply m (list 2 3))"));

    // Testing map function
    Step9.rep("(def! nums (list 1 2 3))");
    Step9.rep("(def! double (fn* (a) (* 2 a)))");
    assertEquals("(2 4 6)", Step9.rep("(map double nums)"));
    assertEquals("(false true false)", Step9.rep("(map (fn* (x) (symbol? x)) (list 1 (quote two) \"three\"))"));
    assertEquals("true", Step9.rep("(= () (map str ()))"));

    // -------- Deferrable Functionality --------

    // Test catch of reader errors
    assertStdoutMatches("(?s).*(EOF|end of input|unbalanced).*", () -> Step9.rep("(try* (eval (read-string \"(+ 1\")) (catch* e (prn :e e)))"));

    // Testing symbol and keyword functions
    assertEquals("abc", Step9.rep("(symbol \"abc\")"));
    assertEquals(":abc", Step9.rep("(keyword \"abc\")"));
    assertEquals("true", Step9.rep("(symbol? (symbol \"abc\"))"));
    assertEquals("true", Step9.rep("(keyword? :abc)"));

    // Testing sequential? and vector?
    assertEquals("true", Step9.rep("(sequential? (list 1 2 3))"));
    assertEquals("true", Step9.rep("(sequential? [15])"));
    assertEquals("true", Step9.rep("(vector? [10 11])"));
    assertEquals("false", Step9.rep("(vector? '(12 13))"));
    assertEquals("[3 4 5]", Step9.rep("(vector 3 4 5)"));

    // Testing hash-maps
    assertEquals("{\"a\" 1}", Step9.rep("(hash-map \"a\" 1)"));
    assertEquals("true", Step9.rep("(map? {\"a\" 1})"));
    Step9.rep("(def! hm1 (hash-map))");
    assertEquals("nil", Step9.rep("(get hm1 \"a\")"));
    assertEquals("false", Step9.rep("(contains? hm1 \"a\")"));
    Step9.rep("(def! hm2 (assoc hm1 \"a\" 1))");
    assertEquals("1", Step9.rep("(get hm2 \"a\")"));
    assertEquals("true", Step9.rep("(contains? hm2 \"a\")"));

    assertEquals("(\"a\")", Step9.rep("(keys hm2)"));
    assertEquals("(1)", Step9.rep("(vals hm2)"));

    // Testing keywords as keys
    assertEquals("123", Step9.rep("(get {:abc 123} :abc)"));
    assertEquals("{:bcd 234}", Step9.rep("(assoc {} :bcd 234)"));

    // Testing dissoc
    Step9.rep("(def! hm3 (assoc hm2 \"b\" 2))");
    assertEquals("{}", Step9.rep("(dissoc hm3 \"a\" \"b\")"));

    // Testing hash-map equality
    assertEquals("true", Step9.rep("(= {:a 11 :b 22} (hash-map :b 22 :a 11))"));
    assertEquals("true", Step9.rep("(= {:a [11 22]} {:a (list 11 22)})"));
    assertEquals("false", Step9.rep("(= {} [])"));
  }

  @Test
  void stepA() {
    StepA.init();

    // Testing readline (assuming a mock or specific input environment)
    //assertStdinMocked("\"hello\"", "hello\n", () -> StepA.rep("(readline \"mal-user> \")"));

    // Testing *host-language*
    assertEquals("false", StepA.rep("(= \"something bogus\" *host-language*)"));

    // -------- Deferrable Functionality ----------

    // Testing hash-map evaluation and atoms (simulating an environment)
    StepA.rep("(def! e (atom {\"+\" +}))");
    StepA.rep("(swap! e assoc \"-\" -)");
    assertEquals("15", StepA.rep("( (get @e \"+\") 7 8)"));
    assertEquals("3", StepA.rep("( (get @e \"-\") 11 8)"));
    StepA.rep("(swap! e assoc \"foo\" (list))");
    assertEquals("()", StepA.rep("(get @e \"foo\")"));
    StepA.rep("(swap! e assoc \"bar\" '(1 2 3))");
    assertEquals("(1 2 3)", StepA.rep("(get @e \"bar\")"));

    // Testing for presence of core and optional functions
    assertEquals("nil", StepA.rep("(do (list time-ms string? number? seq conj meta with-meta fn?) nil)"));
    assertEquals("(false false false)", StepA.rep("(map symbol? '(nil false true))"));

    StepA.rep("(def! add1 (fn* (x) (+ x 1)))");

    // Testing fn? function
    assertEquals("true", StepA.rep("(fn? +)"));
    assertEquals("true", StepA.rep("(fn? list?)"));
    assertEquals("true", StepA.rep("(fn? add1)"));
    assertEquals("false", StepA.rep("(fn? 1)"));

    // Testing string? and number?
    assertEquals("true", StepA.rep("(string? \"abc\")"));
    assertEquals("false", StepA.rep("(string? :abc)"));
    assertEquals("true", StepA.rep("(number? 123)"));
    assertEquals("false", StepA.rep("(number? \"123\")"));

    // Testing conj (prepend for lists, append for vectors)
    assertEquals("(3 1 2)", StepA.rep("(conj (list 1 2) 3)"));
    assertEquals("(4 3 1 2)", StepA.rep("(conj (list 1 2) 3 4)"));
    assertEquals("[1 2 3]", StepA.rep("(conj [1 2] 3)"));
    assertEquals("[1 2 3 4]", StepA.rep("(conj [1 2] 3 4)"));

    // Testing seq (converts collections to sequences/lists)
    assertEquals("(1 2 3)", StepA.rep("(seq (list 1 2 3))"));
    assertEquals("(1 2 3)", StepA.rep("(seq [1 2 3])"));
    assertEquals("(\"a\" \"b\" \"c\")", StepA.rep("(seq \"abc\")"));
    assertEquals("nil", StepA.rep("(seq \"\")"));
    assertEquals("nil", StepA.rep("(seq (list))"));
    assertEquals("nil", StepA.rep("(seq [])"));
    assertEquals("nil", StepA.rep("(seq nil)"));

    // Testing metadata on collections
    assertEquals("nil", StepA.rep("(meta [1 2 3])"));
    assertEquals("[1 2 3]", StepA.rep("(with-meta [1 2 3] {\"a\" 1})"));
    assertEquals("{\"a\" 1}", StepA.rep("(meta (with-meta [1 2 3] {\"a\" 1}))"));
    assertEquals("true", StepA.rep("(vector? (with-meta [1 2 3] {\"a\" 1}))"));
    assertEquals("\"abc\"", StepA.rep("(meta (with-meta [1 2 3] \"abc\"))"));
    assertEquals("{\"a\" 1}", StepA.rep("(meta (with-meta (list 1 2 3) {\"a\" 1}))"));
    assertEquals("true", StepA.rep("(list? (with-meta (list 1 2 3) {\"a\" 1}))"));
    assertEquals("{\"a\" 1}", StepA.rep("(meta (with-meta {\"abc\" 123} {\"a\" 1}))"));

    // Testing metadata persistence and immutability
    StepA.rep("(def! l-wm (with-meta [4 5 6] {\"b\" 2}))");
    assertEquals("{\"b\" 2}", StepA.rep("(meta l-wm)"));
    assertEquals("{\"c\" 3}", StepA.rep("(meta (with-meta l-wm {\"c\" 3}))"));
    assertEquals("{\"b\" 2}", StepA.rep("(meta l-wm)"));

    // interop
    assertEquals("6", StepA.rep("(java-eval \"1 + 2 + 3\")"));
    StepA.rep("(import java.lang.String length)");
    assertEquals("5", StepA.rep("(length \"12345\")"));
    StepA.rep("(import java.lang.String split 1)");
    assertEquals("(\"1\" \"2\" \"3\" \"4\")", StepA.rep("(split/1 \"1 2 3 4\" \" \")"));

    StepA.rep("(import java.lang.String lines)");
    assertEquals("(\"1\" \"2\" \"3\" \"4\")", StepA.rep("(lines \"1\n2\n3\n4\")"));
    assertEquals("\"MalLazy\"", StepA.rep("(type-of (lines \"1\n2\n3\n4\"))"));

    StepA.rep("(def! rand (new java.util.Random))");
    StepA.rep("(rand setSeed 12345)");
    assertEquals("1553932502", StepA.rep("(rand nextInt)"));
    assertEquals("\"MalWrapper\"", StepA.rep("(type-of rand)"));
    assertEquals("true", StepA.rep("(instance? rand \"java.lang.Object\")"));
    assertEquals("true", StepA.rep("(instance? rand \"java.util.Random\")"));
    assertEquals("false", StepA.rep("(instance? rand \"java.util.List\")"));
  }

  @Test
  void stepB() {
    StepA.rep("(def! ones (fn* [] (lazy-seq (cons 1 (ones)))))");
    StepA.rep("(def! range (fn* [n] (lazy-seq (cons n (range (+ n 1))))))");
    StepA.rep("(def! take (fn* [i xs] (lazy-seq (if (empty? xs) xs (if (> i 0) (cons (first xs) (take (- i 1) (rest xs))) (list))))))");
    StepA.rep("(def! drop (fn* [i xs] (lazy-seq (if (empty? xs) xs (if (> i 0) (drop (- i 1) (rest xs)) xs)))))");

    assertThrows(MalException.class, () -> StepA.rep("(lazy-seq nil)"));
    assertThrows(MalException.class, () -> StepA.rep("(lazy-seq 1)"));
    assertThrows(MalException.class, () -> StepA.rep("(lazy-seq \"hello\")"));

    assertEquals("1", StepA.rep("(first (ones))"));
    assertEquals("(1 1 1 1 1)", StepA.rep("(take 5 (ones))"));
    assertEquals("\"MalLazy\"", StepA.rep("(type-of (take 5 (ones)))"));
    assertEquals("(2 2 2 2 2)", StepA.rep("(map (fn* [x] (+ x 1)) (take 5 (ones)))"));
    assertEquals("\"MalLazy\"", StepA.rep("(type-of (map (fn* [x] (+ x 1)) (take 5 (ones))))"));
    assertEquals("(2 2 2 2 2)", StepA.rep("(take 5 (map (fn* [x] (+ x 1)) (ones)))"));
    assertEquals("\"MalLazy\"", StepA.rep("(type-of (take 5 (map (fn* [x] (+ x 1)) (ones))))"));
    assertEquals("(4 5 6 7 8)", StepA.rep("(take 5 (drop 3 (range 1)))"));
    assertEquals("\"MalLazy\"", StepA.rep("(type-of (take 5 (drop 3 (range 1))))"));
    assertEquals("110", StepA.rep("(nth (range 10) 100)"));
    assertEquals("11", StepA.rep("(first (rest (range 10)))"));
    assertEquals("(10 11 12)", StepA.rep("(concat (take 3 (range 10)))"));
    assertEquals("(10 11 12 6 7 8)", StepA.rep("(concat (take 3 (range 10)) (take 3 (drop 1 (range 5))))"));
    assertEquals("false", StepA.rep("(empty? (concat (take 3 (range 10)) (take 3 (drop 1 (range 5)))))"));
    assertEquals("\"MalLazy\"", StepA.rep("(type-of (concat (take 3 (range 10)) (take 3 (drop 1 (range 5)))))"));

    StepA.rep("(import java.util.stream.Stream concat 2)");
    assertEquals("(10 11 12 6 7 8)", StepA.rep("(concat/2 (take 3 (range 10)) (take 3 (drop 1 (range 5))))"));
  }

  @Test
  void stepC() {
    StepA.rep("(def! task (spawn (fn* [] (do (sleep 1) 1234))))");
    assertEquals("1234", StepA.rep("(join task)"));
  }

  /**
   * Helper method to redirect System.out, run a command, and validate the output.
   */
  private void assertStdoutMatches(String regex, Supplier<String> action) {
    var originalOut = System.out;
    var outContent = new ByteArrayOutputStream();
    try {
      System.setOut(new PrintStream(outContent));
      var result = action.get();
      var output = outContent.toString() + result;
      assertTrue(output.matches(regex), "Output did not match regex: " + regex + "\nActual output: " + output);
    } finally {
      System.setOut(originalOut);
    }
  }
}
