/*
 * Copyright (c) 2024-2025, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package mal;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.pcollections.TreePVector;

class TrampolineTest {

  @Test
  void done() {
    var done = Trampoline.done("done");

    assertEquals("done", done.run());
  }

  @Test
  void more() {
    var more = Trampoline.more(() -> Trampoline.done("done"));

    assertEquals("done", more.run());
  }

  @Test
  void recover() {
    var recover = Trampoline.more(() -> {
      throw new UnsupportedOperationException();
    }).recover(_ -> Trampoline.done("recovered"));

    assertEquals("recovered", recover.run());
  }

  @Test
  void recoverExceptionFromFlatMap() {
    var result = Trampoline.done(1)
      .flatMap(_ -> Trampoline.more(() -> {
        throw new UnsupportedOperationException("boom");
      }))
      .recover(_ -> Trampoline.done(42))
      .run();

    assertEquals(42, result);
  }

  @Test
  void recoverUsesNearestHandler() {
    var result = Trampoline.<Integer>more(() -> {
        throw new UnsupportedOperationException("boom");
      })
      .recover(_ -> Trampoline.done(10))
      .flatMap(value -> Trampoline.done(value + 1))
      .recover(_ -> Trampoline.done(99))
      .run();

    assertEquals(11, result);
  }

  @Test
  void nestedRecoverUsesInnerHandler() {
    var inner = Trampoline.<Integer>more(() -> {
      throw new UnsupportedOperationException("boom");
    }).recover(_ -> Trampoline.done(10));

    var result = inner
      .flatMap(value -> Trampoline.done(value + 1))
      .recover(_ -> Trampoline.done(99))
      .run();

    assertEquals(11, result);
  }

  @Test
  void recoverHandlerCanBeRecoveredByOuterHandler() {
    var result = Trampoline.<Integer>more(() -> {
        throw new UnsupportedOperationException("original");
      })
      .recover(_ -> {
        throw new IllegalStateException("from handler");
      })
      .recover(e -> Trampoline.done(
          e instanceof IllegalStateException ? 42 : -1))
      .run();

    assertEquals(42, result);
  }

  @Test
  void throwsException() {
    var recover = Trampoline.more(() -> {
      throw new UnsupportedOperationException();
    });

    assertThrows(UnsupportedOperationException.class, recover::run);
  }

  @Test
  void sum() {
    assertAll(
        () -> assertEquals(5050, sum(100)),
        () -> assertEquals(20100, sum(200)),
        () -> assertEquals(45150, sum(300)),
        () -> assertEquals(705082704, sum(100000))
      );
  }

  @Test
  void sequence() {
    var trampolines = Trampoline.sequence(
      List.of(
        Trampoline.done(1),
        Trampoline.more(() -> Trampoline.done(2)),
        Trampoline.more(() -> Trampoline.more(() -> Trampoline.done(3))))
      );

    assertEquals(TreePVector.from(List.of(1, 2, 3)), trampolines.run());
  }

  @Test
  void fib() {
    assertAll(
        () -> assertEquals(1, fib(1)),
        () -> assertEquals(1, fib(2)),
        () -> assertEquals(2, fib(3)),
        () -> assertEquals(3, fib(4)),
        () -> assertEquals(5, fib(5)),
        () -> assertEquals(8, fib(6)),
        () -> assertEquals(13, fib(7)),
        () -> assertEquals(21, fib(8)),
        () -> assertEquals(55, fib(10)),
        () -> assertEquals(317811, fib(28))
      );
  }

  private int fib(int n) {
    return fibLoop(n).run();
  }

  private int sum(int n) {
    return sumLoop(n, 0).run();
  }

  private Trampoline<Integer> sumLoop(Integer counter, Integer sum) {
    if (counter == 0) {
      return Trampoline.done(sum);
    }
    return Trampoline.more(() -> sumLoop(counter - 1, sum + counter));
  }

  private Trampoline<Integer> fibLoop(Integer n) {
    if (n < 2) {
      return Trampoline.done(n);
    }
    return Trampoline.more(() -> fibLoop(n - 1)).flatMap(x -> fibLoop(n - 2).map(y -> x + y));
  }
}
