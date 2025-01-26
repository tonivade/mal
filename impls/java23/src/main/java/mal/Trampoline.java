/*
 * Copyright (c) 2024, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package mal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

public sealed interface Trampoline<T> {

  static Trampoline<Void> UNIT = done(null);

  record Done<T>(T value) implements Trampoline<T> {
    @Override
    public <R> Trampoline<R> apply(Function<T, Trampoline<R>> parent) {
      return parent.apply(value);
    }
  }

  record FlatMap<T, R>(Trampoline<T> current, Function<T, Trampoline<R>> next) implements Trampoline<R> {
    @Override
    public <S> Trampoline<S> apply(Function<R, Trampoline<S>> parent) {
      return current.flatMap(value -> next.apply(value).flatMap(parent));
    }

    private Trampoline<R> resume() {
      return current.apply(next);
    }
  }

  static <T> Trampoline<T> done(T value) {
    return new Done<>(value);
  }

  static <T> Trampoline<T> more(Supplier<Trampoline<T>> next) {
    return UNIT.flatMap(_ -> next.get());
  }

  default <R> Trampoline<R> map(Function<T, R> mapper) {
    return flatMap(mapper.andThen(Trampoline::done));
  }

  default <R> Trampoline<R> flatMap(Function<T, Trampoline<R>> mapper) {
    return new FlatMap<>(this, mapper);
  }

  default <R> Trampoline<R> andThen(Trampoline<R> next) {
    return flatMap(_ -> next);
  }

  default boolean isSuspended() {
    return this instanceof FlatMap;
  }

  <R> Trampoline<R> apply(Function<T, Trampoline<R>> parent);

  default T run() {
    var current = this;
    while (current instanceof FlatMap<?, T> flatMap) {
      current = flatMap.resume();
    }
    return ((Done<T>) current).value();
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
