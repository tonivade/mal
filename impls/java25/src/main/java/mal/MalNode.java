/*
 * Copyright (c) 2024-2025, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package mal;

import static java.util.Objects.requireNonNull;
import static mal.Trampoline.done;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;

import org.pcollections.HashTreePMap;
import org.pcollections.PMap;
import org.pcollections.PVector;
import org.pcollections.TreePVector;

public sealed interface MalNode {

  MalNode withMeta(MalNode meta);

  MalNode meta();

  MalSymbol QUOTE = symbol("quote");
  MalSymbol QUASIQUOTE = symbol("quasiquote");
  MalSymbol UNQUOTE = symbol("unquote");
  MalSymbol SPLICE_UNQUOTE = symbol("splice-unquote");
  MalSymbol WITH_META = symbol("with-meta");
  MalSymbol DEREF = symbol("deref");
  MalSymbol DEBUG_EVAL = symbol("DEBUG-EVAL");

  MalSymbol CONCAT = symbol("concat");
  MalSymbol CONS = symbol("cons");
  MalSymbol VEC = symbol("vec");

  MalConstant NIL = new MalConstant("nil", null);
  MalConstant TRUE = new MalConstant("true", null);
  MalConstant FALSE = new MalConstant("false", null);

  MalNumber ZERO = number(0);
  MalNumber ONE = number(1);

  MalList EMPTY_LIST = new MalList(TreePVector.empty(), null);
  MalVector EMPTY_VECTOR = new MalVector(TreePVector.empty(), null);
  MalMap EMPTY_MAP = new MalMap(HashTreePMap.empty(), null);

  record MalConstant(String name, MalNode meta) implements MalNode {

    public MalConstant {
      requireNonNull(name);
    }

    @Override
    public MalConstant withMeta(MalNode meta) {
      return new MalConstant(name, meta);
    }
  }

  final class MalAtom implements MalNode {

    private MalNode value;
    private final MalNode meta;

    public MalAtom(MalNode value, MalNode meta) {
      this.value = requireNonNull(value);
      this.meta = meta;
    }

    public MalNode getValue() {
      return value;
    }

    public void setValue(MalNode value) {
      this.value = requireNonNull(value);
    }

    @Override
    public MalNode meta() {
      return meta;
    }

    @Override
    public MalAtom withMeta(MalNode meta) {
      return new MalAtom(value, meta);
    }

    @Override
    public int hashCode() {
      return Objects.hash(value, meta);
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == null || !(obj instanceof MalAtom other)) {
        return false;
      }
      return Objects.equals(this.value, other.value) &&
          Objects.equals(this.meta, other.meta);
    }

    @Override
    public String toString() {
      return "MalAtom[value=" + value + ", meta=" + meta +"]";
    }
  }

  record MalNumber(long value, MalNode meta) implements MalNode {

    public MalNumber sum(MalNumber other) {
      return new MalNumber(this.value + other.value, null);
    }

    public MalNumber sub(MalNumber other) {
      return new MalNumber(this.value - other.value, null);
    }

    public MalNumber mul(MalNumber other) {
      return new MalNumber(this.value * other.value, null);
    }

    public MalNumber div(MalNumber other) {
      return new MalNumber(this.value / other.value, null);
    }

    public MalNumber mod(MalNumber other) {
      return new MalNumber(this.value % other.value, null);
    }

    public boolean gt(MalNumber other) {
      return this.value > other.value;
    }

    public boolean gte(MalNumber other) {
      return this.value >= other.value;
    }

    public boolean lt(MalNumber other) {
      return this.value < other.value;
    }

    public boolean lte(MalNumber other) {
      return this.value <= other.value;
    }

    public long asLong() {
      return value;
    }

    public int asInt() {
      return (int) value;
    }

    @Override
    public MalNumber withMeta(MalNode meta) {
      return new MalNumber(value, meta);
    }
  }

  sealed interface MalKey extends MalNode {}

  record MalString(String value, MalNode meta) implements MalKey {

    public MalString {
      requireNonNull(value);
    }

    @Override
    public MalString withMeta(MalNode meta) {
      return new MalString(value, meta);
    }
  }

  record MalKeyword(String value, MalNode meta) implements MalKey {

    public MalKeyword {
      requireNonNull(value);
    }

    @Override
    public MalKeyword withMeta(MalNode meta) {
      return new MalKeyword(value, meta);
    }
  }

  record MalSymbol(String name, MalNode meta) implements MalKey {

    public MalSymbol {
      requireNonNull(name);
    }

    @Override
    public MalSymbol withMeta(MalNode meta) {
      return new MalSymbol(name, meta);
    }
  }

  final class MalFiber implements MalNode {

    private final CompletableFuture<MalNode> future;
    private final MalNode meta;

    public MalFiber(CompletableFuture<MalNode> future, MalNode meta) {
      this.future = future;
      this.meta = meta;
    }

    public MalNode join() {
      return future.join();
    }

    @Override
    public MalNode withMeta(MalNode meta) {
      return new MalFiber(future, meta);
    }

    @Override
    public MalNode meta() {
      return meta;
    }

    @Override
    public String toString() {
      return "MalFiber[done=%s,cancelled=%s,error=%]".formatted(future.isDone(), future.isCancelled(), future.isCompletedExceptionally());
    }
  }

  sealed interface MalSequence extends MalNode, Iterable<MalNode> {

    MalSequence seq();

    default MalNode get(int pos) {
      for (var current : this) {
        if (pos == 0) {
          return current;
        }
        pos--;
      }
      throw new IndexOutOfBoundsException(pos);
    }

    MalNode head();

    MalSequence tail();

    default MalSequence cons(MalNode node) {
      return new MalCons(node, this, null);
    }

    boolean isEmpty();

    default int size() {
      int count = 0;
      for (var _ : this) {
        count++;
      }
      return count;
    }

    @Override
    default Iterator<MalNode> iterator() {
      return new Iterator<>() {
        private MalSequence current = MalSequence.this;

        @Override
        public boolean hasNext() {
          return current.seq() != null;
        }

        @Override
        public MalNode next() {
          if (!hasNext()) {
            throw new NoSuchElementException();
          }
          var result = current.head();
          current = current.tail();
          return result;
        }
      };
    }
  }

  sealed interface MalCollection extends MalSequence {

    PVector<MalNode> values();

    @Override
    default MalNode get(int pos) {
      if (pos < 0 || pos >= size()) {
        return NIL;
      }
      return values().get(pos);
    }

    @Override
    default MalSequence cons(MalNode node) {
      return list(values().plus(0, node));
    }

    @Override
    default MalNode head() {
      return get(0);
    }

    @Override
    default MalList tail() {
      if (size() <= 1) {
        return EMPTY_LIST;
      }
      return list(values().minus(0));
    }

    @Override
    default MalSequence seq() {
      if (isEmpty()) {
        return null;
      }
      return this;
    }

    @Override
    default boolean isEmpty() {
      return values().isEmpty();
    }

    @Override
    default int size() {
      return values().size();
    }
  }

  record MalList(PVector<MalNode> values, MalNode meta) implements MalCollection {

    public MalList {
      requireNonNull(values);
    }

    @Override
    public MalList withMeta(MalNode meta) {
      return new MalList(values, meta);
    }
  }

  record MalVector(PVector<MalNode> values, MalNode meta) implements MalCollection {

    public MalVector {
      requireNonNull(values);
    }

    @Override
    public MalVector withMeta(MalNode meta) {
      return new MalVector(values, meta);
    }
  }

  record MalCons(MalNode head, MalSequence tail, MalNode meta) implements MalSequence {

    public MalCons {
      requireNonNull(head);
      requireNonNull(tail);
    }

    @Override
    public MalCons withMeta(MalNode meta) {
      return new MalCons(head, tail, meta);
    }

    @Override
    public boolean isEmpty() {
      return false;
    }

    @Override
    public MalSequence seq() {
      return this;
    }
  }

  final class MalLazy implements MalSequence {

    private Supplier<MalNode> thunk;
    private MalNode value;
    private final MalNode meta;

    public MalLazy(Supplier<MalNode> value, MalNode meta) {
      this.thunk = requireNonNull(value);
      this.meta = meta;
    }

    private MalLazy(Supplier<MalNode> thunk, MalNode value, MalNode meta) {
      this.thunk = thunk;
      this.value = value;
      this.meta = meta;
    }

    public boolean isRealized() {
      return thunk == null;
    }

    @Override
    public MalSequence seq() {
      // only when seq is called the lazy-seq is unwrapped completely
      return unwrap(realize()).seq();
    }

    @Override
    public MalLazy withMeta(MalNode meta) {
      return new MalLazy(thunk, value, meta);
    }

    @Override
    public MalNode meta() {
      return meta;
    }

    @Override
    public MalNode head() {
      return seq().head();
    }

    @Override
    public MalSequence tail() {
      return seq().tail();
    }

    @Override
    public boolean isEmpty() {
      return seq() == null;
    }

    private MalSequence realize() {
      if (!isRealized()) {
        // it realizes the lazy-seq but without unwrapping nested lazies
        value = thunk.get();
        thunk = null;
      }
      return (MalSequence) value;
    }

    private MalSequence unwrap(MalSequence current) {
      while (current instanceof MalLazy lazy) {
        current = lazy.realize();
      }
      return current;
    }

    @Override
    public String toString() {
      return "MalLazy[thunk=%s,value=%s,meta=%s]".formatted(thunk, value, meta);
    }
  }

  record MalMap(PMap<MalKey, MalNode> map, MalNode meta) implements MalNode, Iterable<Map.Entry<MalKey, MalNode>> {

    public MalMap {
      requireNonNull(map);
    }

    @Override
    public Iterator<Map.Entry<MalKey, MalNode>> iterator() {
      return map.entrySet().iterator();
    }

    public boolean isEmpty() {
      return size() == 0;
    }

    public int size() {
      return map.size();
    }

    public MalNode get(MalKey key) {
      return map.getOrDefault(key, NIL);
    }

    public Collection<MalKey> keys() {
      return map.keySet();
    }

    public Collection<MalNode> values() {
      return map.values();
    }

    public MalMap addAll(Map<MalKey, MalNode> entries) {
      return new MalMap(map.plusAll(entries), null);
    }

    public MalMap removeAll(Collection<? extends MalKey> keys) {
      return new MalMap(map.minusAll(keys), null);
    }

    public boolean contains(MalKey key) {
      return map.containsKey(key);
    }

    @Override
    public MalMap withMeta(MalNode meta) {
      return new MalMap(map, meta);
    }
  }

  record MalWrapper(Object value, MalNode meta) implements MalNode {

    public MalWrapper {
      requireNonNull(value);
    }

    @Override
    public MalWrapper withMeta(MalNode meta) {
      return new MalWrapper(value, meta);
    }

    public Trampoline<MalNode> call(String name, MalSequence args) {
      var lambda = Interop.method(value.getClass().getName(), name, args.size());
      return lambda.apply(list(args.cons(this)));
    }
  }

  record MalError(Exception exception, MalNode meta) implements MalNode {

    public MalError {
      requireNonNull(exception);
    }

    @Override
    public MalError withMeta(MalNode meta) {
      return new MalError(exception, meta);
    }
  }

  @FunctionalInterface
  interface MalLambda {
    Trampoline<MalNode> apply(MalList args);
  }

  sealed interface MalWithLambda extends MalNode {

    MalLambda lambda();

    default Trampoline<MalNode> apply(MalList args) {
      return lambda().apply(args);
    }

    default MalNode run(MalList args) {
      return apply(args).run();
    }
  }

  record MalFunction(MalLambda lambda, MalNode meta) implements MalWithLambda {

    public MalMacro toMacro() {
      return new MalMacro(lambda, null);
    }

    @Override
    public MalFunction withMeta(MalNode meta) {
      return new MalFunction(lambda, meta);
    }
  }

  record MalMacro(MalLambda lambda, MalNode meta) implements MalWithLambda {

    @Override
    public MalMacro withMeta(MalNode meta) {
      return new MalMacro(lambda, meta);
    }
  }

  static MalError error(Exception exception) {
    return new MalError(exception, null);
  }

  static MalMap map(MalNode...tokens) {
    return map(List.of(tokens));
  }

  static MalMap map(MalSequence tokens) {
    return map(toMap(tokens));
  }

  static MalMap map(Collection<MalNode> tokens) {
    return map(toMap(tokens));
  }

  static MalMap map(Map<MalKey, MalNode> map) {
    if (map.isEmpty()) {
      return EMPTY_MAP;
    }
    return new MalMap(HashTreePMap.from(map), null);
  }

  static MalCons cons(MalNode first, MalSequence rest) {
    return new MalCons(first, rest, null);
  }

  static MalLazy lazy(Supplier<MalNode> thunk) {
    return new MalLazy(thunk, null);
  }

  static MalFiber fork(CompletableFuture<MalNode> future) {
    return new MalFiber(future, null);
  }

  static MalWrapper wrap(Object o) {
    return new MalWrapper(o, null);
  }

  static MalVector vector(MalNode...tokens) {
    return vector(List.of(tokens));
  }

  static MalVector vector(MalSequence tokens) {
    if (tokens instanceof MalVector vec) {
      return vec;
    }
    if (tokens instanceof MalList list) {
      return vector(list.values());
    }
    return vector(List.of(tokens));
  }

  static MalVector vector(Collection<? extends MalNode> tokens) {
    if (tokens.isEmpty()) {
      return EMPTY_VECTOR;
    }
    return new MalVector(TreePVector.from(tokens), null);
  }

  static MalList list(MalNode...tokens) {
    return list(List.of(tokens));
  }

  static MalList list(MalSequence tokens) {
    if (tokens instanceof MalList list) {
      return list;
    }
    if (tokens instanceof MalVector vec) {
      return list(vec.values());
    }
    return list(List.of(tokens));
  }

  static MalList list(Collection<? extends MalNode> tokens) {
    if (tokens.isEmpty()) {
      return EMPTY_LIST;
    }
    return new MalList(TreePVector.from(tokens), null);
  }

  static MalConstant bool(boolean value) {
    return value ? TRUE : FALSE;
  }

  static MalNumber number(long value) {
    return new MalNumber(value, null);
  }

  static MalNumber number(double value) {
    return number((long) value);
  }

  static MalString string(String value) {
    return new MalString(value, null);
  }

  static MalKeyword keyword(String value) {
    return new MalKeyword(value, null);
  }

  static MalSymbol symbol(String name) {
    return new MalSymbol(name, null);
  }

  static MalAtom atom(MalNode value) {
    return new MalAtom(value, null);
  }

  static MalLambda lambda(Function<MalList, MalNode> lambda) {
    return args -> done(lambda.apply(args));
  }

  static MalFunction function(MalLambda lambda) {
    return new MalFunction(lambda, null);
  }

  static boolean equals(MalNode first, MalNode second) {
    if (first instanceof MalSequence a && second instanceof MalSequence b) {
      var i = a.iterator();
      var j = b.iterator();
      while (i.hasNext() && j.hasNext()) {
        if (!equals(i.next(), j.next())) {
          return false;
        }
      }
      return i.hasNext() == j.hasNext();
    }
    if (first instanceof MalMap a && second instanceof MalMap b) {
      if (a.size() != b.size()) {
        return false;
      }
      if (!a.keys().equals(b.keys())) {
        return false;
      }
      for (var key : a.keys()) {
        if (!equals(a.get(key), b.get(key))) {
          return false;
        }
      }
      return true;
    }
    return Objects.equals(first, second);
  }

  private static Map<MalKey, MalNode> toMap(Iterable<MalNode> tokens) {
    Map<MalKey, MalNode> map = new LinkedHashMap<>();
    for (var iterator = tokens.iterator(); iterator.hasNext();) {
      var key = (MalKey) iterator.next();
      var value = iterator.next();
      map.put(key, value);
    }
    return map;
  }
}
