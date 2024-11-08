package mal;

import static mal.Mal.FALSE;
import static mal.Mal.NIL;
import static mal.Mal.list;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import mal.Mal.MalSymbol;

public class Env {

  private static final String DEBUG_EVAL = "DEBUG-EVAL";

  public static final Env DEFAULT = new Env(Core.NS);

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

  public Env(Env outer, Iterable<Mal> binds, Iterable<Mal> exprs) {
    this(outer, toMap(binds, exprs));
  }

  public Env(Env outer, Map<String, Mal> map) {
    this.outer = outer;
    this.map = map;
  }

  public boolean isDebugEval() {
    var debugEval = map.get(DEBUG_EVAL);
    return debugEval != null && debugEval != FALSE;
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
    
  private static Map<String, Mal> toMap(Iterable<Mal> binds, Iterable<Mal> exprs) {
    var i = binds.iterator();
    var j = exprs.iterator();

    Map<String, Mal> result = new HashMap<>();

    while (i.hasNext() && j.hasNext()) {
      var bind = (MalSymbol) i.next();

      if (bind.name().equals("&")) {
        List<Mal> list = new ArrayList<>();
        while (j.hasNext()) {
          list.add(j.next());
        }
        result.put(bind.name(), list(list));
      } else {
        var expr = j.next();
        result.put(bind.name(), expr);
      }
    }

    return result;
  }
}
