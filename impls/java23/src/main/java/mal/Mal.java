package mal;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public sealed interface Mal {

  MalSymbol QUOTE = new MalSymbol("quote");
  MalSymbol QUASIQUOTE = new MalSymbol("quasiquote");
  MalSymbol UNQUOTE = new MalSymbol("unquote");
  MalSymbol SPLICE_UNQUOTE = new MalSymbol("splice-unquote");
  MalSymbol WITH_META = new MalSymbol("with-meta");
  MalSymbol DEREF = new MalSymbol("deref");

  MalConstant NIL = new MalConstant("nil");
  MalConstant TRUE = new MalConstant("true");
  MalConstant FALSE = new MalConstant("false");

  MalNumber ZERO = new MalNumber(0);
  MalNumber ONE = new MalNumber(1);

  record MalConstant(String name) implements Mal {}

  record MalNumber(Integer value) implements Mal {

    public MalNumber sum(MalNumber other) {
      return new MalNumber(this.value + other.value);
    }

    public MalNumber subs(MalNumber other) {
      return new MalNumber(this.value - other.value);
    }

    public MalNumber mul(MalNumber other) {
      return new MalNumber(this.value * other.value);
    }

    public MalNumber div(MalNumber other) {
      return new MalNumber(this.value / other.value);
    }
  }

  record MalString(String value) implements Mal {}

  record MalKeyword(String value) implements Mal {}

  record MalList(List<Mal> values) implements Mal, Iterable<Mal> {

    public MalList append(MalList other) {
      List<Mal> result = new ArrayList<>(values);
      result.addAll(other.values);
      return new MalList(result);
    }

    @Override
    public Iterator<Mal> iterator() {
      return values.iterator();
    }

    public Stream<Mal> stream() {
      return values.stream();
    }

    public boolean isEmpty() {
      return values.isEmpty();
    }

    public int size() {
      return values.size();
    }
  }

  record MalVector(List<Mal> values) implements Mal, Iterable<Mal> {

    @Override
    public Iterator<Mal> iterator() {
      return values.iterator();
    }

    public Stream<Mal> stream() {
      return values.stream();
    }

    public boolean isEmpty() {
      return values.isEmpty();
    }

    public int size() {
      return values.size();
    }
  }

  record MalMap(Map<String, Mal> map) implements Mal, Iterable<Map.Entry<String, Mal>> {

    @Override
    public Iterator<Map.Entry<String, Mal>> iterator() {
      return map.entrySet().iterator();
    }

    public boolean isEmpty() {
      return map.isEmpty();
    }

    public int size() {
      return map.size();
    }
  }

  record MalSymbol(String name) implements Mal {}

  @FunctionalInterface
  non-sealed interface MalFunction extends Mal {

    Mal apply(MalList args);
  }

  static MalMap map(Mal...tokens) {
    return map(List.of(tokens));
  }

  static MalMap map(List<Mal> tokens) {
    return map(toMap(tokens));
  }

  static MalMap map(Map<String, Mal> map) {
    return new MalMap(map);
  }

  static MalVector vector(Mal...tokens) {
    return vector(List.of(tokens));
  }

  static MalVector vector(List<Mal> tokens) {
    return new MalVector(tokens);
  }

  static MalList list(Mal...tokens) {
    return list(List.of(tokens));
  }

  static MalList list(List<Mal> tokens) {
    return new MalList(tokens);
  }

  static MalNumber number(Integer value) {
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

  static MalFunction function(MalFunction function) {
    return function;
  }

  private static Map<String, Mal> toMap(List<Mal> tokens) {
    Map<String, Mal> map = new LinkedHashMap<>();
    for (var iterator = tokens.iterator(); iterator.hasNext();) {
      var key = iterator.next();
      var value = iterator.next();
      map.put(switch (key) {
        case MalSymbol(var name) -> name;
        case MalString(var name) -> "\"" + name + "\"";
        case MalKeyword(var name) -> ":" + name;
        default -> throw new IllegalStateException("not supported key: " + key);
      }, value);
    }
    return map;
  }
}
