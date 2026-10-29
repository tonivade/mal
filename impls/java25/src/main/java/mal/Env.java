/*
 * Copyright (c) 2024-2025, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package mal;

import static mal.MalNode.list;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

import mal.MalNode.MalSequence;
import mal.MalNode.MalSymbol;

class Env {

  // the root env, it has no outer env
  public static final Env DEFAULT = new Env(null, Core.NS);

  private final Env outer;
  private final Map<String, MalNode> map;

  // the global env is concurrent because fibers created with spawn can read it,
  // and define into it with eval, while other threads are running
  public Env() {
    this(DEFAULT, new ConcurrentHashMap<>());
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
    // looked up through the outer envs, so tracing also works inside function bodies,
    // but DEFAULT is skipped because the core namespace never defines DEBUG-EVAL
    for (var env = this; env != null && env != DEFAULT; env = env.outer) {
      var debugEval = env.map.get(MalNode.DEBUG_EVAL.name());
      if (debugEval != null) {
        return Evaluator.isTruthy(debugEval);
      }
    }
    return false;
  }

  public MalNode get(String key) {
    for (var env = this; env != null; env = env.outer) {
      var value = env.map.get(key);
      if (value != null) {
        return value;
      }
    }
    return null;
  }

  // the names defined in this env and its outer envs, used by the repl completion
  public Set<String> names() {
    var names = new TreeSet<String>();
    for (var env = this; env != null; env = env.outer) {
      names.addAll(env.map.keySet());
    }
    return names;
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
        if (!i.hasNext()) {
          throw new MalException("invalid parameters: & must be followed by a symbol");
        }
        bind = (MalSymbol) i.next();
        List<MalNode> list = new ArrayList<>();
        while (j.hasNext()) {
          list.add(j.next());
        }
        result.put(bind.name(), list(list));
      } else if (j.hasNext()) {
        var expr = j.next();
        result.put(bind.name(), expr);
      } else {
        throw new MalException("missing arg: " + bind.name());
      }
    }

    if (j.hasNext()) {
      throw new MalException("too many args: expected " + binds.size() + " but got " + exprs.size());
    }

    return result;
  }
}
