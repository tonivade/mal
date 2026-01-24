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

  /**
   * Runs the computation and returns the final result.
   *
   * This implementation is stack-safe (no Java stack growth) because it uses
   * an explicit loop and an explicit Deque for continuations instead of
   * recursive calls or per-step lambda chaining. It also reduces heap
   * allocations compared to the previous chained-lambda approach by
   * keeping continuations in an ArrayDeque (which grows its internal array,
   * avoiding one-object-per-step allocations).
   *
   * Note: heap usage is still O(n) in the number of pending flatMap
   * continuations: the Deque stores references to the mappers. This is an
   * improvement in allocation churn but not in asymptotic retained memory.
   *
   * @return the final result of the computation
   */
  @SuppressWarnings("unchecked")
  default T run() {
    Trampoline<?> current = this;
    Deque<Function<Object, Trampoline<?>>> stack = new ArrayDeque<>();

    while (true) {
      if (current instanceof Done(var value)) {
        if (stack.isEmpty()) {
          return (T) value; // end of program
        }
        current = stack.pop().apply(value);
      } else if (current instanceof More(var next)) {
        current = next.get();
      } else if (current instanceof FlatMap(var source, var nextFn)) {
        stack.push((Function<Object, Trampoline<?>>) nextFn);
        current = source;
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
