/*
 * Copyright (c) 2024, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package mal;

import static java.util.Objects.requireNonNull;
import static mal.Trampoline.done;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

public sealed interface MalNode {

  MalNode withMeta(MalNode meta);

  MalNode meta();

  MalSymbol QUOTE = new MalSymbol("quote", null);
  MalSymbol QUASIQUOTE = new MalSymbol("quasiquote", null);
  MalSymbol UNQUOTE = new MalSymbol("unquote", null);
  MalSymbol SPLICE_UNQUOTE = new MalSymbol("splice-unquote", null);
  MalSymbol WITH_META = new MalSymbol("with-meta", null);
  MalSymbol DEREF = new MalSymbol("deref", null);

  MalSymbol CONCAT = new MalSymbol("concat", null);
  MalSymbol CONS = new MalSymbol("cons", null);

  MalConstant NIL = new MalConstant("nil", null);
  MalConstant TRUE = new MalConstant("true", null);
  MalConstant FALSE = new MalConstant("false", null);

  MalNumber ZERO = new MalNumber(0, null);
  MalNumber ONE = new MalNumber(1, null);

  MalList EMPTY_LIST = new MalList(List.of(), null);
  MalVector EMPTY_VECTOR = new MalVector(List.of(), null);
  MalMap EMPTY_MAP = new MalMap(Map.of(), null);

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
      this.value = value;
    }

    @Override
    public MalNode meta() {
      return meta;
    }

    @Override
    public MalNode withMeta(MalNode meta) {
      return new MalAtom(value, meta);
    }

    @Override
    public String toString() {
      return "MalAtom[" + value + "]";
    }
  }

  record MalNumber(long value, MalNode meta) implements MalNode {

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

  sealed interface MalSequence extends MalNode, Iterable<MalNode> {

    List<MalNode> values();

    @Override
    default Iterator<MalNode> iterator() {
      return values().iterator();
    }

    default MalNode get(int i) {
      if (i < 0 || i >= values().size()) {
        return NIL;
      }
      return values().get(i);
    }

    default Stream<MalNode> stream() {
      return values().stream();
    }

    default boolean isEmpty() {
      return size() == 0;
    }

    default int size() {
      return values().size();
    }
  }

  record MalList(List<MalNode> values, MalNode meta) implements MalSequence {

    public MalList {
      requireNonNull(values);
    }

    @Override
    public MalList withMeta(MalNode meta) {
      return new MalList(values, meta);
    }
  }

  record MalVector(List<MalNode> values, MalNode meta) implements MalSequence {

    public MalVector {
      requireNonNull(values);
    }

    @Override
    public MalVector withMeta(MalNode meta) {
      return new MalVector(values, meta);
    }
  }

  record MalMap(Map<MalKey, MalNode> map, MalNode meta) implements MalNode, Iterable<Map.Entry<MalKey, MalNode>> {

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
      var copy = new HashMap<>(map);
      copy.putAll(entries);
      return new MalMap(copy, null);
    }

    public MalMap removeAll(Collection<? extends MalKey> keys) {
      var copy = new HashMap<>(map);
      keys.forEach(copy::remove);
      return new MalMap(copy, null);
    }

    public boolean contains(MalKey key) {
      return map.containsKey(key);
    }

    @Override
    public MalMap withMeta(MalNode meta) {
      return new MalMap(map, meta);
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
  }

  record MalFunction(MalLambda lambda, MalNode meta) implements MalWithLambda {

    public MalMacro toMacro() {
      return new MalMacro(lambda, null);
    }

    @Override
    public MalNode withMeta(MalNode meta) {
      return new MalFunction(lambda, meta);
    }
  }

  record MalMacro(MalLambda lambda, MalNode meta) implements MalWithLambda {

    @Override
    public MalNode withMeta(MalNode meta) {
      return new MalMacro(lambda, meta);
    }
  }

  static MalError error(Exception exception) {
    return new MalError(exception, null);
  }

  static MalMap map(MalNode...tokens) {
    return map(List.of(tokens));
  }

  static MalMap map(List<MalNode> tokens) {
    return map(toMap(tokens));
  }

  static MalMap map(Map<MalKey, MalNode> map) {
    if (map.isEmpty()) {
      return EMPTY_MAP;
    }
    return new MalMap(map, null);
  }

  static MalVector vector(MalNode...tokens) {
    return vector(List.of(tokens));
  }

  static MalVector vector(Stream<? extends MalNode> tokens) {
    return vector(tokens.toList());
  }

  static MalVector vector(Collection<? extends MalNode> tokens) {
    if (tokens.isEmpty()) {
      return EMPTY_VECTOR;
    }
    return new MalVector(List.copyOf(tokens), null);
  }

  static MalList list(MalNode...tokens) {
    return list(List.of(tokens));
  }

  static MalList list(Stream<? extends MalNode> tokens) {
    return list(tokens.toList());
  }

  static MalList list(Collection<? extends MalNode> tokens) {
    if (tokens.isEmpty()) {
      return EMPTY_LIST;
    }
    return new MalList(List.copyOf(tokens), null);
  }

  static MalNumber number(long value) {
    return new MalNumber(value, null);
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
      if (a.size() != b.size()) {
        return false;
      }
      var i = a.iterator();
      var j = b.iterator();
      while (i.hasNext() && j.hasNext()) {
        if (!equals(i.next(), j.next())) {
          return false;
        }
      }
      return true;
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
    return first.equals(second);
  }

  private static Map<MalKey, MalNode> toMap(List<MalNode> tokens) {
    Map<MalKey, MalNode> map = new LinkedHashMap<>();
    for (var iterator = tokens.iterator(); iterator.hasNext();) {
      var key = (MalKey) iterator.next();
      var value = iterator.next();
      map.put(key, value);
    }
    return map;
  }
}
