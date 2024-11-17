package mal;

import static java.util.Objects.requireNonNull;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public sealed interface MalNode {

  MalSymbol QUOTE = new MalSymbol("quote");
  MalSymbol QUASIQUOTE = new MalSymbol("quasiquote");
  MalSymbol UNQUOTE = new MalSymbol("unquote");
  MalSymbol SPLICE_UNQUOTE = new MalSymbol("splice-unquote");
  MalSymbol WITH_META = new MalSymbol("with-meta");
  MalSymbol DEREF = new MalSymbol("deref");
  
  MalSymbol CONCAT = new MalSymbol("concat");
  MalSymbol CONS = new MalSymbol("cons");

  MalConstant NIL = new MalConstant("nil");
  MalConstant TRUE = new MalConstant("true");
  MalConstant FALSE = new MalConstant("false");

  MalNumber ZERO = new MalNumber(0);
  MalNumber ONE = new MalNumber(1);

  MalList EMPTY_LIST = new MalList(List.of());
  MalVector EMPTY_VECTOR = new MalVector(List.of());
  MalMap EMPTY_MAP = new MalMap(Map.of());

  record MalConstant(String name) implements MalNode {

    public MalConstant {
      requireNonNull(name);
    }
  }

  final class MalAtom implements MalNode {

    private MalNode value;

    public MalAtom(MalNode value) {
      this.value = requireNonNull(value);
    }

    public MalNode getValue() {
      return value;
    }

    public void setValue(MalNode value) {
      this.value = value;
    }

    @Override
    public String toString() {
      return "MalAtom[" + value + "]";
    }
  }

  record MalNumber(long value) implements MalNode {

    public MalNumber {
      requireNonNull(value);
    }

    public MalNumber sum(MalNumber other) {
      return new MalNumber(this.value + other.value);
    }

    public MalNumber sub(MalNumber other) {
      return new MalNumber(this.value - other.value);
    }

    public MalNumber mul(MalNumber other) {
      return new MalNumber(this.value * other.value);
    }

    public MalNumber div(MalNumber other) {
      return new MalNumber(this.value / other.value);
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
  }

  sealed interface MalKey extends MalNode {}

  record MalString(String value) implements MalKey {

    public MalString {
      requireNonNull(value);
    }
  }

  record MalKeyword(String value) implements MalKey {

    public MalKeyword {
      requireNonNull(value);
    }
  }

  sealed interface MalSequence extends MalNode, Iterable<MalNode> {

    List<MalNode> values();

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

  record MalList(List<MalNode> values) implements MalSequence {
    
    public MalList {
      requireNonNull(values);
    }
  }

  record MalVector(List<MalNode> values) implements MalSequence {
    public MalVector {
      requireNonNull(values);
    }
  }

  record MalMap(Map<MalKey, MalNode> map) implements MalNode, Iterable<Map.Entry<MalKey, MalNode>> {

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
      return new MalMap(copy);
    }

    public MalMap removeAll(Collection<? extends MalKey> keys) {
      var copy = new HashMap<>(map);
      keys.forEach(copy::remove);
      return new MalMap(copy);
    }

    public boolean contains(MalKey key) {
      return map.containsKey(key);
    }
  }

  record MalSymbol(String name) implements MalKey {

    public MalSymbol {
      requireNonNull(name);
    }
  }

  record MalError(Exception exception) implements MalNode {

    public MalError {
      requireNonNull(exception);
    }
  }

  @FunctionalInterface
  non-sealed interface MalFunction extends MalNode {

    Trampoline<MalNode> apply(MalList args);

    default MalMacro toMacro() {
      return this::apply;
    }
  }

  interface MalMacro extends MalFunction {

  }

  static MalError error(Exception exception) {
    return new MalError(exception);
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
    return new MalMap(map);
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
    return new MalVector(List.copyOf(tokens));
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
    return new MalList(List.copyOf(tokens));
  }

  static MalNumber number(long value) {
    return new MalNumber(value);
  }

  static MalString string(String value) {
    return new MalString(value);
  }

  static MalKeyword keyword(String value) {
    return new MalKeyword(value);
  }

  static MalSymbol symbol(String name) {
    return new MalSymbol(name);
  }

  static MalAtom atom(MalNode value) {
    return new MalAtom(value);
  }

  static MalFunction function(MalFunction function) {
    return function;
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
