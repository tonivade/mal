package mal;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public sealed interface Mal {

  Mal QUOTE = new MalSymbol("quote");
  Mal QUASIQUOTE = new MalSymbol("quasiquote");
  Mal UNQUOTE = new MalSymbol("unquote");
  Mal SPLICE_UNQUOTE = new MalSymbol("splice-unquote");
  Mal WITH_META = new MalSymbol("with-meta");
  Mal DEREF = new MalSymbol("deref");

  Mal NIL = new MalConstant("nil");
  Mal TRUE = new MalConstant("true");
  Mal FALSE = new MalConstant("false");

  record MalConstant(String name) implements Mal {}

  record MalNumber(Integer value) implements Mal {}
  
  record MalString(String value) implements Mal {}

  record MalList(List<Mal> value) implements Mal {}

  record MalVector(List<Mal> value) implements Mal {}

  record MalMap(Map<String, Mal> value) implements Mal {}

  record MalSymbol(String name) implements Mal {}

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

  static MalSymbol symbol(String name) {
    return new MalSymbol(name);
  }

  private static Map<String, Mal> toMap(List<Mal> tokens) {
    Map<String, Mal> map = new HashMap<>();
    var iterator = tokens.iterator();
    while (iterator.hasNext()) {
      var key = iterator.next();
      var value = iterator.next();
      map.put(switch (key) {
        case MalSymbol(var name) -> name;
        case MalString(var name) -> name;
        default -> throw new IllegalStateException();
      }, value);
    }
    return map;
  }
}
