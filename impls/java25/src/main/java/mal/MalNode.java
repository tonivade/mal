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
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.function.Supplier;

import org.pcollections.HashTreePMap;
import org.pcollections.PMap;
import org.pcollections.PVector;

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

  MalConstant NIL = new MalConstant("nil");
  MalConstant TRUE = new MalConstant("true");
  MalConstant FALSE = new MalConstant("false");

  MalNumber ZERO = number(0);
  MalNumber ONE = number(1);

  MalList EMPTY_LIST = new MalList(ArrayPVector.empty(), null);
  MalVector EMPTY_VECTOR = new MalVector(ArrayPVector.empty(), null);
  MalMap EMPTY_MAP = new MalMap(HashTreePMap.empty(), null);

  record MalConstant(String name) implements MalNode {

    public MalConstant {
      requireNonNull(name);
    }

    @Override
    public MalNode meta() {
      return NIL;
    }

    @Override
    public MalConstant withMeta(MalNode meta) {
      // nil, true and false must stay single values, they are compared by name
      throw new MalException("with-meta: not supported for constants");
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
      // interned, so an env can compare the names with == and String.equals takes
      // its shortcut when two symbols have the same name
      name = name.intern();
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

  // a final class instead of a record, the running thread and the children change while the
  // fiber runs; the thread is null when it has not started or has finished, it's needed to
  // cancel the fiber because the future can't interrupt it; the children are the fibers
  // spawned by this one that are still running, they are cancelled with it
  final class MalFiber implements MalNode {

    // the fiber running in the current thread, so spawn knows the parent of the new fiber
    static final ScopedValue<MalFiber> CURRENT = ScopedValue.newInstance();

    private final CompletableFuture<MalNode> future = new CompletableFuture<>();
    private final AtomicReference<Thread> thread = new AtomicReference<>();
    private final Set<MalFiber> children = ConcurrentHashMap.newKeySet();

    CompletableFuture<MalNode> future() {
      return future;
    }

    private void execute(Supplier<MalNode> task) {
      thread.set(Thread.currentThread());
      try {
        // checked after the thread is set, a cancel that came before could not interrupt it
        if (!future.isCancelled()) {
          var result = task.get();
          // a fiber finishes when its children finish, so a running fiber always has a parent
          // that can cancel it
          awaitChildren();
          future.complete(result);
        }
      } catch (Throwable e) {
        // nobody would wait for the children of a failed fiber
        children.forEach(MalFiber::cancel);
        future.completeExceptionally(e);
      } finally {
        thread.set(null);
      }
    }

    private void awaitChildren() {
      // the children can spawn more children while this waits, so it waits until all are done;
      // the done ones are skipped, they may still be in the set for a moment
      while (true) {
        var running = children.stream()
            .map(child -> child.future)
            .filter(childFuture -> !childFuture.isDone())
            .toArray(CompletableFuture<?>[]::new);
        if (running.length == 0) {
          return;
        }
        try {
          // a child that fails or is cancelled is done too
          CompletableFuture.allOf(running).exceptionally(_ -> null).get();
        } catch (InterruptedException e) {
          throw new Trampoline.Interrupted();
        } catch (ExecutionException e) {
          // not possible, exceptionally turns any failure into null
          throw new IllegalStateException(e);
        }
      }
    }

    private void adopt(MalFiber child) {
      children.add(child);
      child.future.whenComplete((_, _) -> children.remove(child));
      // this fiber may have been cancelled while it was spawning the child
      if (future.isCancelled()) {
        child.cancel();
      }
    }

    // get is used instead of join because it can be interrupted, so ctrl-c in the repl
    // stops waiting for a fiber that never ends; the fiber itself keeps running
    public MalNode join() {
      try {
        return future.get();
      } catch (InterruptedException e) {
        throw new Trampoline.Interrupted();
      } catch (ExecutionException e) {
        // rethrow what the fiber threw, so catch* gets the original mal error
        if (e.getCause() instanceof RuntimeException cause) {
          throw cause;
        }
        throw new MalException("error in fiber", e.getCause());
      } catch (CancellationException e) {
        throw new MalException("fiber cancelled");
      }
    }

    // the future is cancelled first, so join returns at once, then the thread is interrupted,
    // and the fiber stops at the next step of its trampoline; false if it had already finished
    // or was already cancelled, future.cancel alone returns true for a cancelled future
    public boolean cancel() {
      if (future.isDone() || !future.cancel(false)) {
        return false;
      }
      var running = thread.get();
      if (running != null) {
        running.interrupt();
      }
      children.forEach(MalFiber::cancel);
      return true;
    }

    @Override
    public MalNode meta() {
      return NIL;
    }

    @Override
    public MalFiber withMeta(MalNode meta) {
      // a copy would be a different fiber, not equal to this one, like atoms it's not supported
      throw new MalException("with-meta: not supported for fibers");
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
        // only when seq is called the lazy-seq is unwrapped completely
        sequence = unwrap(realize()).seq();
        unwrapped = true;
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
      if (realized) {
        return (MalSequence) value;
      }
      if (lock.isHeldByCurrentThread()) {
        throw new MalException("re-entrant lazy-seq");
      }
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

  static MalFiber fork(Supplier<MalNode> task, Executor executor) {
    var fiber = new MalFiber();
    // a fiber spawned inside another fiber is its child
    if (MalFiber.CURRENT.isBound()) {
      MalFiber.CURRENT.get().adopt(fiber);
    }
    executor.execute(() -> ScopedValue.where(MalFiber.CURRENT, fiber).run(() -> fiber.execute(task)));
    return fiber;
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
    return new MalVector(persistent(tokens), null);
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
    return new MalList(persistent(tokens), null);
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

  // a persistent vector is immutable, so it can be shared instead of copied; the cast is safe
  // because a vector of a subtype of MalNode can only be read as MalNodes or copied by plus
  @SuppressWarnings("unchecked")
  private static PVector<MalNode> persistent(Collection<? extends MalNode> tokens) {
    if (tokens instanceof PVector<?> vector) {
      return (PVector<MalNode>) vector;
    }
    return ArrayPVector.from(tokens);
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
