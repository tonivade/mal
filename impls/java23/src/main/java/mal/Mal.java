package mal;

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

  record MalConstant(String name) implements Mal {}

  final class MalAtom implements Mal {

    private Mal value;

    public MalAtom(Mal value) {
      this.value = value;
    }

    public Mal getValue() {
      return value;
    }

    public void setValue(Mal value) {
      this.value = value;
    }

    @Override
    public String toString() {
      return "MalAtom[" + value + "]";
    }
  }

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
  }

  record MalString(String value) implements Mal {}

  record MalKeyword(String value) implements Mal {}

  sealed interface MalIterable extends Iterable<Mal> {

    List<Mal> values();

    default Iterator<Mal> iterator() {
      return values().iterator();
    }

    default Mal get(int i) {
      if (i < 0 || i >= values().size()) {
        return NIL;
      }
      return values().get(i);
    }

    default Stream<Mal> stream() {
      return values().stream();
    }

    default boolean isEmpty() {
      return size() == 0;
    }

    default int size() {
      return values().size();
    }
  }

  record MalList(List<Mal> values) implements Mal, MalIterable {}

  record MalVector(List<Mal> values) implements Mal, MalIterable {}

  record MalMap(Map<String, Mal> map) implements Mal, Iterable<Map.Entry<String, Mal>> {

    @Override
    public Iterator<Map.Entry<String, Mal>> iterator() {
      return map.entrySet().iterator();
    }

    public boolean isEmpty() {
      return size() == 0;
    }

    public int size() {
      return map.size();
    }
  }

  record MalSymbol(String name) implements Mal {}

  @FunctionalInterface
  non-sealed interface MalFunction extends Mal {

    Trampoline<Mal> apply(MalList args);

    default MalMacro toMacro() {
      return this::apply;
    }
  }

  interface MalMacro extends MalFunction {

  }

  static MalMap map(Mal...tokens) {
    return map(List.of(tokens));
  }

  static MalMap map(List<Mal> tokens) {
    return map(toMap(tokens));
  }

  static MalMap map(Map<String, Mal> map) {
    if (map.isEmpty()) {
      return EMPTY_MAP;
    }
    return new MalMap(map);
  }

  static MalVector vector(Mal...tokens) {
    return vector(List.of(tokens));
  }

  static MalVector vector(Stream<Mal> tokens) {
    return vector(tokens.toList());
  }

  static MalVector vector(List<Mal> tokens) {
    if (tokens.isEmpty()) {
      return EMPTY_VECTOR;
    }
    return new MalVector(tokens);
  }

  static MalList list(Mal...tokens) {
    return list(List.of(tokens));
  }

  static MalList list(Stream<Mal> tokens) {
    return list(tokens.toList());
  }

  static MalList list(List<Mal> tokens) {
    if (tokens.isEmpty()) {
      return EMPTY_LIST;
    }
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

  static MalAtom atom(Mal value) {
    return new MalAtom(value);
  }

  static MalFunction function(MalFunction function) {
    return function;
  }

  static boolean equals(Mal first, Mal second) {
    if (first instanceof MalIterable a && second instanceof MalIterable b) {
      var i = a.iterator();
      var j = b.iterator();
      while (i.hasNext() && j.hasNext()) {
        if (!equals(i.next(), j.next())) {
          return false;
        }
      }
      return !(i.hasNext() || j.hasNext());
    }
    return first.equals(second);
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
