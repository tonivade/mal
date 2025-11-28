/*
 * Copyright (c) 2024, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package mal;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

public sealed interface Trampoline<T> {

  record Done<T>(T value) implements Trampoline<T> {}
  record More<T>(Supplier<Trampoline<T>> next) implements Trampoline<T> {}
  record FlatMap<T, R>(Trampoline<T> current, Function<T, Trampoline<R>> mapper) implements Trampoline<R> {}

  static <T> Trampoline<T> done(T value) {
    return new Done<>(value);
  }

  static <T> Trampoline<T> more(Supplier<Trampoline<T>> next) {
    return new More<>(next);
  }

  default <R> Trampoline<R> map(Function<T, R> mapper) {
    return flatMap(mapper.andThen(Trampoline::done));
  }

  default <R> Trampoline<R> andThen(Trampoline<R> next) {
    return flatMap(_ -> next);
  }

  default <R> Trampoline<R> flatMap(Function<T, Trampoline<R>> mapper) {
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
  @SuppressWarnings({ "unchecked", "rawtypes" })
  default T run() {
    Trampoline<?> current = this;
    Deque<Function<Object, Trampoline<?>>> stack = new ArrayDeque<>();

    while (true) {
      if (current instanceof Done<?> done) {
        var value = done.value();

        if (stack.isEmpty()) {
          return (T) value; // end of program
        }

        Function<Object, Trampoline<?>> k = stack.pop();
        current = k.apply(value);
      } else if (current instanceof More<?> more) {
        current = more.next().get();
      } else if (current instanceof FlatMap<?, ?> flatMap) {
        Trampoline<Object> source = (Trampoline<Object>) flatMap.current();
        Function<Object, Trampoline<?>> nextFn = (Function) flatMap.mapper();

        // Push the mapper and continue with the source. Using an explicit
        // stack avoids allocating a new closure for each chained flatMap step.
        stack.push(nextFn);
        current = source;
      }
    }
  }

  static <A, B, R> Trampoline<R> map2(Trampoline<A> ta, Trampoline<B> tb, BiFunction<A, B, R> mapper) {
    return ta.flatMap(a -> tb.map(b -> mapper.apply(a, b)));
  }

  static <T> Trampoline<List<T>> traverse(Collection<? extends Trampoline<T>> list) {
    return list.stream().reduce(done(List.<T>of()), Trampoline::add, Trampoline::merge);
  }

  private static <T> Trampoline<List<T>> add(Trampoline<? extends Collection<T>> tlist, Trampoline<T> titem) {
    return map2(tlist, titem, (list, item) -> {
      List<T> newList = new ArrayList<>(list);
      newList.add(item);
      return List.copyOf(newList);
    });
  }

  private static <T> Trampoline<List<T>> merge(
      Trampoline<? extends Collection<T>> tlist1, Trampoline<? extends Collection<T>> tlist2) {
    return map2(tlist1, tlist2, (list1, list2) -> {
      List<T> newList = new ArrayList<>(list1);
      newList.addAll(list2);
      return List.copyOf(newList);
    });
  }
}
