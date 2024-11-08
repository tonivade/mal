package mal;

import static mal.Mal.NIL;
import static mal.Mal.ZERO;
import static mal.Mal.function;

import java.util.HashMap;
import java.util.Map;

import mal.Mal.MalNumber;
import mal.Mal.MalSymbol;

public class Env {

  public static final Env DEFAULT = new Env(Map.of(
      "+", function(args -> args.stream().map(MalNumber.class::cast).reduce(MalNumber::sum).orElse(ZERO)),
      "-", function(args -> args.stream().map(MalNumber.class::cast).reduce(MalNumber::subs).orElse(ZERO)),
      "*", function(args -> args.stream().map(MalNumber.class::cast).reduce(MalNumber::mul).orElse(ZERO)),
      "/", function(args -> args.stream().map(MalNumber.class::cast).reduce(MalNumber::div).orElse(ZERO))
  ));

  private final Env outer;
  private final Map<String, Mal> map;

  public Env() {
    this(DEFAULT, new HashMap<>());
  }

  public Env(Map<String, Mal> map) {
    this(DEFAULT, map);
  }

  public Env(Env outer) {
    this(outer, new HashMap<>());
  }

  public Env(Env outer, Map<String, Mal> map) {
    this.outer = outer;
    this.map = map;
  }

  public Mal get(String key) {
    var value = map.get(key);
    if (value != null) {
      return value;
    }
    if (outer != null) {
      return outer.get(key);
    }
    return NIL;
  }

  public void set(MalSymbol key, Mal value) {
    map.put(key.name(), value);
  }
}
