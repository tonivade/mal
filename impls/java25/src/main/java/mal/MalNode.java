/*
 * Copyright (c) 2024-2025, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package mal;

import static java.util.Objects.requireNonNull;
import static mal.Trampoline.done;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
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

    // metadata doesn't take part in equality
    @Override
    public boolean equals(Object obj) {
      return obj instanceof MalConstant(var other, _) && name.equals(other);
    }

    @Override
    public int hashCode() {
      return name.hashCode();
    }
  }

  sealed interface MalValue<T> extends MalNode {

    T value();

    default Trampoline<MalNode> call(String name, MalSequence args) {
      var lambda = Interop.method(value().getClass().getName(), name, args.size());
      return lambda.apply(toList(args.cons(this)));
    }
  }

  final class MalAtom implements MalNode {

    // atoms can be shared between fibers
    private final AtomicReference<MalNode> value;

    public MalAtom(MalNode value) {
      this.value = new AtomicReference<>(requireNonNull(value));
    }

    public MalNode getValue() {
      return value.get();
    }

    public void setValue(MalNode value) {
      this.value.set(requireNonNull(value));
    }

    public boolean compareAndSet(MalNode expected, MalNode newValue) {
      return value.compareAndSet(expected, requireNonNull(newValue));
    }

    @Override
    public MalNode meta() {
      return NIL;
    }

    @Override
    public MalAtom withMeta(MalNode meta) {
      // a copy would be a different atom with its own state, like clojure atoms don't support it
      throw new MalException("with-meta: not supported for atoms");
    }

    @Override
    public String toString() {
      return "MalAtom[value=" + value.get() + "]";
    }
  }

  record MalNumber(Long value, MalNode meta) implements MalValue<Long> {

    public MalNumber {
      requireNonNull(value);
    }

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
      return value.intValue();
    }

    @Override
    public MalNumber withMeta(MalNode meta) {
      return new MalNumber(value, meta);
    }

    // metadata doesn't take part in equality
    @Override
    public boolean equals(Object obj) {
      return obj instanceof MalNumber(var other, _) && value.equals(other);
    }

    @Override
    public int hashCode() {
      return value.hashCode();
    }
  }

  sealed interface MalKey extends MalNode {}

  record MalString(String value, MalNode meta) implements MalValue<String>, MalKey {

    public MalString {
      requireNonNull(value);
    }

    @Override
    public MalString withMeta(MalNode meta) {
      return new MalString(value, meta);
    }

    // metadata doesn't take part in equality
    @Override
    public boolean equals(Object obj) {
      return obj instanceof MalString(var other, _) && value.equals(other);
    }

    @Override
    public int hashCode() {
      return value.hashCode();
    }
  }

  record MalKeyword(String value, MalNode meta) implements MalValue<String>, MalKey {

    public MalKeyword {
      requireNonNull(value);
    }

    @Override
    public MalKeyword withMeta(MalNode meta) {
      return new MalKeyword(value, meta);
    }

    // metadata doesn't take part in equality
    @Override
    public boolean equals(Object obj) {
      return obj instanceof MalKeyword(var other, _) && value.equals(other);
    }

    @Override
    public int hashCode() {
      return value.hashCode();
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

    // metadata doesn't take part in equality
    @Override
    public boolean equals(Object obj) {
      return obj instanceof MalSymbol(var other, _) && name.equals(other);
    }

    @Override
    public int hashCode() {
      return name.hashCode();
    }
  }

  record MalFiber(CompletableFuture<MalNode> future, MalNode meta) implements MalNode {

    public MalFiber {
      requireNonNull(future);
    }

    public MalNode join() {
      try {
        return future.join();
      } catch (CompletionException e) {
        // rethrow what the fiber threw, so catch* gets the original mal error
        if (e.getCause() instanceof RuntimeException cause) {
          throw cause;
        }
        throw e;
      }
    }

    @Override
    public MalNode withMeta(MalNode meta) {
      return new MalFiber(future, meta);
    }
  }

  sealed interface MalSequence extends MalNode, Iterable<MalNode> {

    MalSequence seq();

    default MalNode get(int pos) {
      // a negative index would walk an infinite lazy-seq forever
      if (pos < 0) {
        throw new IndexOutOfBoundsException(pos);
      }
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

    @Override
    default Iterator<MalNode> iterator() {
      return values().iterator();
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

    // a lazy-seq can be realized by several fibers at the same time, the lock makes the thunk
    // run only once, and the volatile flags let realized seqs be read without locking
    private final ReentrantLock lock = new ReentrantLock();
    private Supplier<MalNode> thunk;
    private MalNode value;
    private volatile boolean realized;
    private MalSequence sequence;
    private volatile boolean unwrapped;
    private final MalNode meta;

    public MalLazy(Supplier<MalNode> value, MalNode meta) {
      this.thunk = requireNonNull(value);
      this.meta = meta;
    }

    public boolean isRealized() {
      return realized;
    }

    @Override
    public MalSequence seq() {
      if (!unwrapped) {
        lock.lock();
        try {
          if (!unwrapped) {
            // only when seq is called the lazy-seq is unwrapped completely
            sequence = unwrap(realize()).seq();
            unwrapped = true;
          }
        } finally {
          lock.unlock();
        }
      }
      return sequence;
    }

    @Override
    public MalLazy withMeta(MalNode meta) {
      // the copy realizes this lazy-seq, so the thunk is shared and still runs only once
      return new MalLazy(this::realize, meta);
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
      if (!realized) {
        lock.lock();
        try {
          if (!realized) {
            // it realizes the lazy-seq but without unwrapping nested lazies
            value = thunk.get();
            thunk = null;
            realized = true;
          }
        } finally {
          lock.unlock();
        }
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

  record MalWrapper(Object value, MalNode meta) implements MalValue<Object> {

    public MalWrapper {
      requireNonNull(value);
    }

    @Override
    public MalWrapper withMeta(MalNode meta) {
      return new MalWrapper(value, meta);
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

  static MalVector vectorOf(MalNode...tokens) {
    return vector(List.of(tokens));
  }

  static MalVector toVector(MalSequence tokens) {
    if (tokens instanceof MalVector vec) {
      return vec;
    }
    if (tokens instanceof MalList list) {
      return vector(list.values());
    }
    return vector(elements(tokens));
  }

  static MalVector vector(Collection<? extends MalNode> tokens) {
    if (tokens.isEmpty()) {
      return EMPTY_VECTOR;
    }
    return new MalVector(TreePVector.from(tokens), null);
  }

  static MalList listOf(MalNode...tokens) {
    return list(List.of(tokens));
  }

  static MalList toList(MalSequence tokens) {
    if (tokens instanceof MalList list) {
      return list;
    }
    if (tokens instanceof MalVector vec) {
      return list(vec.values());
    }
    return list(elements(tokens));
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
    return new MalAtom(value);
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

  // cons and lazy-seqs have to be walked to collect their elements
  private static List<MalNode> elements(MalSequence tokens) {
    var values = new ArrayList<MalNode>();
    tokens.forEach(values::add);
    return values;
  }

  private static Map<MalKey, MalNode> toMap(Iterable<MalNode> tokens) {
    Map<MalKey, MalNode> map = new LinkedHashMap<>();
    for (var iterator = tokens.iterator(); iterator.hasNext();) {
      var next = iterator.next();
      if (!(next instanceof MalKey key)) {
        throw new MalException("invalid map key: " + Printer.print(next, true));
      }
      if (!iterator.hasNext()) {
        throw new MalException("missing value for key " + Printer.print(key, true));
      }
      var value = iterator.next();
      map.put(key, value);
    }
    return map;
  }
}
