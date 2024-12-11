/*
 * Copyright (c) 2024, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package mal;

import static mal.MalNode.FALSE;
import static mal.MalNode.list;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import mal.MalNode.MalSequence;
import mal.MalNode.MalSymbol;

public class Env {

  private static final String DEBUG_EVAL = "DEBUG-EVAL";

  public static final Env DEFAULT = new Env(Core.NS);

  private final Env outer;
  private final Map<String, MalNode> map;

  public Env() {
    this(DEFAULT, new HashMap<>());
  }

  public Env(Map<String, MalNode> map) {
    this(DEFAULT, map);
  }

  public Env(Env outer) {
    this(outer, new HashMap<>());
  }

  public Env(Env outer, MalSequence binds, MalSequence exprs) {
    this(outer, toMap(binds, exprs));
  }

  public Env(Env outer, Map<String, MalNode> map) {
    this.outer = outer;
    this.map = map;
  }

  public boolean isDebugEval() {
    var debugEval = map.get(DEBUG_EVAL);
    return debugEval != null && debugEval != FALSE;
  }

  public MalNode get(String key) {
    var value = map.get(key);
    if (value != null) {
      return value;
    }
    if (outer != null) {
      return outer.get(key);
    }
    return null;
  }

  public void set(MalSymbol key, MalNode value) {
    map.put(key.name(), value);
  }

  private static Map<String, MalNode> toMap(MalSequence binds, MalSequence exprs) {
    var i = binds.iterator();
    var j = exprs.iterator();

    Map<String, MalNode> result = new HashMap<>();

    while (i.hasNext()) {
      var bind = (MalSymbol) i.next();

      if (bind.name().equals("&")) {
        bind = (MalSymbol) i.next();
        List<MalNode> list = new ArrayList<>();
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
