package mal;

import static java.util.function.Function.identity;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

public sealed interface Trampoline<T> {

  record Done<T>(T value) implements Trampoline<T> {}
  record More<T>(Supplier<Trampoline<T>> next) implements Trampoline<T> {}

  static <T> Trampoline<T> done(T value) {
    return new Done<>(value);
  }

  static <T> Trampoline<T> more(Supplier<Trampoline<T>> next) {
    return new More<>(next);
  }

  default <R> Trampoline<R> map(Function<T, R> mapper) {
    return flatMap(mapper.andThen(Trampoline::done));
  }

  default <R> Trampoline<R> flatMap(Function<T, Trampoline<R>> mapper) {
    return fold(next -> more(() -> next.flatMap(mapper)), mapper);
  }

  default <R> Trampoline<R> andThen(Trampoline<R> next) {
    return flatMap(_ -> next);
  }

  default <R> R fold(Function<Trampoline<T>, R> moreMapper, Function<T, R> doneMapper) {
    return switch (this) {
      case Done<T>(var value) -> doneMapper.apply(value);
      case More<T>(var next) -> moreMapper.apply(next.get());
    };
  }

  default T run() {
    return iterate().fold(_ -> {
      throw new IllegalStateException();
    }, identity());
  }

  private Trampoline<T> iterate() {
    return Stream.iterate(this, t -> t.fold(identity(), _ -> t))
        .dropWhile(t -> t instanceof More).findFirst().orElseThrow();
  }

  static <A, B, R> Trampoline<R> map2(Trampoline<A> ta, Trampoline<B> tb, BiFunction<A, B, R> mapper) {
    return ta.flatMap(a -> tb.map(b -> mapper.apply(a, b)));
  }

  static <T> Trampoline<List<T>> traverse(List<Trampoline<T>> list) {
    return list.stream().reduce(done(List.<T>of()), Trampoline::add, Trampoline::merge);
  }

  private static <T> Trampoline<List<T>> add(Trampoline<List<T>> tlist, Trampoline<T> tnext) {
    return map2(tlist, tnext, (list, next) -> {
      List<T> newList = new ArrayList<>(list);
      newList.add(next);
      return List.copyOf(newList);
    });
  }

  private static <T> Trampoline<List<T>> merge(Trampoline<List<T>> tlist, Trampoline<List<T>> tnext) {
    return map2(tlist, tnext, (list, next) -> {
      List<T> newList = new ArrayList<>(list);
      newList.addAll(next);
      return List.copyOf(newList);
    });
  }
}
