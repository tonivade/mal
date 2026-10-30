/*
 * Copyright (c) 2024-2025, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package mal;

import static mal.MalNode.list;

import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

import mal.MalNode.MalSequence;
import mal.MalNode.MalSymbol;

// the global env and the core namespace keep their definitions in a map compared by equals,
// the envs of functions, let*, catch* and spawn in an identity map, because their names
// always come from symbols
sealed abstract class Env {

  // the root env, it has no outer env
  public static final Env DEFAULT = new MapEnv(null, Core.NS);

  // isDebugEval runs on every eval step, so the lookup is skipped until DEBUG-EVAL is
  // defined somewhere; once set it stays set, even if DEBUG-EVAL is later false
  private static volatile boolean debugEvalDefined;

  private final Env outer;

  private Env(Env outer) {
    this.outer = outer;
  }

  // the global env is concurrent because fibers created with spawn can read it,
  // and define into it with eval, while other threads are running
  static Env global() {
    return new MapEnv(DEFAULT, new ConcurrentHashMap<>());
  }

  static Env local(Env outer) {
    return new LocalEnv(outer, 4);
  }

  static Env local(Env outer, MalSequence binds, MalSequence exprs) {
    var env = new LocalEnv(outer, binds.size());
    env.bind(binds, exprs);
    return env;
  }

  public boolean isDebugEval() {
    if (!debugEvalDefined) {
      return false;
    }
    // looked up through the outer envs, so tracing also works inside function bodies,
    // but DEFAULT is skipped because the core namespace never defines DEBUG-EVAL
    for (var env = this; env != null && env != DEFAULT; env = env.outer) {
      var debugEval = env.find(MalNode.DEBUG_EVAL.name());
      if (debugEval != null) {
        return Evaluator.isTruthy(debugEval);
      }
    }
    return false;
  }

  // the key must be the name of a symbol, local envs compare the interned names with ==
  public MalNode get(String key) {
    for (var env = this; env != null; env = env.outer) {
      var value = env.find(key);
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
      env.addNames(names);
    }
    return names;
  }

  public void set(MalSymbol key, MalNode value) {
    if (key.name() == MalNode.DEBUG_EVAL.name()) {
      debugEvalDefined = true;
    }
    put(key.name(), value);
  }

  // the value of a name defined in this env, without looking in the outer envs
  abstract MalNode find(String key);

  abstract void put(String key, MalNode value);

  abstract void addNames(Collection<String> names);

  private static final class MapEnv extends Env {

    private final Map<String, MalNode> map;

    private MapEnv(Env outer, Map<String, MalNode> map) {
      super(outer);
      this.map = map;
    }

    @Override
    MalNode find(String key) {
      return map.get(key);
    }

    @Override
    void put(String key, MalNode value) {
      map.put(key, value);
    }

    @Override
    void addNames(Collection<String> names) {
      names.addAll(map.keySet());
    }
  }

  // the names come from symbols, that are interned, so they are compared with ==
  private static final class LocalEnv extends Env {

    private final IdentityHashMap<String, MalNode> map;

    private LocalEnv(Env outer, int capacity) {
      super(outer);
      this.map = new IdentityHashMap<>(capacity);
    }

    @Override
    MalNode find(String key) {
      return map.get(key);
    }

    @Override
    void put(String key, MalNode value) {
      map.put(key, value);
    }

    @Override
    void addNames(Collection<String> names) {
      names.addAll(map.keySet());
    }

    private void bind(MalSequence binds, MalSequence exprs) {
      var i = binds.iterator();
      var j = exprs.iterator();

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
          set(bind, list(list));
        } else if (j.hasNext()) {
          set(bind, j.next());
        } else {
          throw new MalException("missing arg: " + bind.name());
        }
      }

      if (j.hasNext()) {
        throw new MalException("too many args: expected " + binds.size() + " but got " + exprs.size());
      }
    }
  }
}
