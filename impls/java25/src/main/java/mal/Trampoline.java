/*
 * Copyright (c) 2024-2025, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package mal;

import static java.util.function.Function.identity;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import org.pcollections.PVector;
import org.pcollections.TreePVector;

sealed interface Trampoline<T> {

  record Done<T>(T value) implements Trampoline<T> {}
  record More<T>(Supplier<? extends Trampoline<T>> next) implements Trampoline<T> {}
  record FlatMap<T, R>(Trampoline<T> current, Function<? super T, ? extends Trampoline<R>> mapper) implements Trampoline<R> {}
  record Recover<T>(Trampoline<T> current, Function<? super RuntimeException, ? extends Trampoline<T>> recover) implements Trampoline<T> {}

  // thrown when the running thread is interrupted, like with ctrl-c in the repl;
  // recover handlers are skipped, so a try* in mal code can't swallow it
  final class Interrupted extends RuntimeException {

    private static final long serialVersionUID = 1L;

    Interrupted() {
      super("interrupted", null, false, false);
    }
  }

  static <T> Trampoline<T> done(T value) {
    return new Done<>(value);
  }

  static <T> Trampoline<T> more(Supplier<Trampoline<T>> next) {
    return new More<>(next);
  }

  default <R> Trampoline<R> map(Function<? super T, ? extends R> mapper) {
    return flatMap(mapper.andThen(Trampoline::done));
  }

  default <R> Trampoline<R> andThen(Trampoline<R> next) {
    return flatMap(_ -> next);
  }

  default <R> Trampoline<R> flatMap(Function<? super T, ? extends Trampoline<R>> mapper) {
    return new FlatMap<>(this, mapper);
  }

  default Trampoline<T> recover(Function<? super RuntimeException, ? extends Trampoline<T>> mapper) {
    return new Recover<>(this, mapper);
  }

  /**
   * Runs the computation and returns the final result.
   *
   * This implementation is stack-safe (no Java stack growth) because it uses
   * an explicit loop and keeps the pending continuations in an ArrayDeque
   * instead of making recursive calls.
   *
   * Note: heap usage is O(n) in the number of pending flatMap and recover
   * continuations, because the Deque keeps a reference to each of them.
   *
   * @return the final result of the computation
   */
  @SuppressWarnings("unchecked")
  default T run() {
    Trampoline<?> current = this;
    Deque<Trampoline<?>> stack = new ArrayDeque<>();

    while (true) {
      if (Thread.interrupted()) {
        throw new Interrupted();
      }
      try {
        if (current instanceof Done(var value)) {
          boolean resumed = false;
          while (!resumed) {
            switch (stack.poll()) {
              case null -> {
                return (T) value;
              }
              case FlatMap(_, var nextFn) -> {
                current = ((Function<Object, Trampoline<?>>) nextFn).apply(value);
                resumed = true;
              }
              default -> {}
            }
          }
        } else if (current instanceof More(var next)) {
          current = next.get();
        } else if (current instanceof FlatMap(var source, _)) {
          stack.push(current);
          current = source;
        } else if (current instanceof Recover(var source, _)) {
          stack.push(current);
          current = source;
        }

      } catch (Interrupted e) {
        // thrown by a nested run, it has to reach the repl
        throw e;
      } catch (RuntimeException e) {
        boolean resumed = false;
        while (!resumed) {
          switch (stack.poll()) {
            case null -> {
              throw e;
            }
            case Recover(_, var nextFn) -> {
              current = more(() -> ((Function<RuntimeException, Trampoline<?>>) nextFn).apply(e));
              resumed = true;
            }
            default -> {}
          }
        }
      }
    }
  }

  static <A, B, R> Trampoline<R> zip(Trampoline<A> ta, Trampoline<B> tb, BiFunction<A, B, R> mapper) {
    return ta.flatMap(a -> tb.map(b -> mapper.apply(a, b)));
  }

  static <T, R> Trampoline<PVector<R>> traverse(Iterable<? extends T> list, Function<? super T, ? extends Trampoline<R>> mapper) {
    Trampoline<PVector<R>> acc = done(TreePVector.<R>empty());
    for (T current : list) {
      acc = zip(acc, mapper.apply(current), PVector::plus);
    }
    return acc;
  }

  static <T> Trampoline<PVector<T>> sequence(Iterable<? extends Trampoline<T>> list) {
    return traverse(list, identity());
  }
}
