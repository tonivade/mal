/*
 * Copyright (c) 2024-2025, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package mal;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.concurrent.ConcurrentHashMap;

import mal.MalNode.MalMacro;
import mal.MalNode.MalSequence;

// the expansion of a macro call is kept by the form of the call, so a call inside a function
// body is expanded only once instead of every time the function runs; forms are compared by
// identity, two calls with the same code in different places are different forms, and they
// are weak keys, so the code created by eval at runtime can be collected
final class MacroCache {

  // the macro is kept to expand the form again if the name is redefined with another macro
  private record Expansion(MalMacro macro, MalNode result) {}

  private final ConcurrentHashMap<Object, Expansion> expansions = new ConcurrentHashMap<>();
  private final ReferenceQueue<MalSequence> collected = new ReferenceQueue<>();

  MalNode get(MalSequence form, MalMacro macro) {
    var expansion = expansions.get(new Lookup(form));
    if (expansion != null && expansion.macro() == macro) {
      return expansion.result();
    }
    return null;
  }

  void put(MalSequence form, MalMacro macro, MalNode result) {
    removeCollected();
    expansions.put(new WeakKey(form, collected), new Expansion(macro, result));
  }

  private void removeCollected() {
    for (var key = collected.poll(); key != null; key = collected.poll()) {
      expansions.remove(key);
    }
  }

  // both keys compare the form by identity, Lookup is used to search without creating a
  // weak reference, WeakKey is the one stored in the map
  private interface Key {
    Object form();
  }

  private record Lookup(Object form) implements Key {

    @Override
    public boolean equals(Object other) {
      return other instanceof Key key && key.form() == form;
    }

    @Override
    public int hashCode() {
      return System.identityHashCode(form);
    }
  }

  private static final class WeakKey extends WeakReference<MalSequence> implements Key {

    private final int hash;

    WeakKey(MalSequence form, ReferenceQueue<MalSequence> queue) {
      super(form, queue);
      this.hash = System.identityHashCode(form);
    }

    @Override
    public Object form() {
      return get();
    }

    @Override
    public boolean equals(Object other) {
      if (this == other) {
        return true;
      }
      // a collected key is only equal to itself, so it can still be removed
      var form = get();
      return form != null && other instanceof Key key && key.form() == form;
    }

    @Override
    public int hashCode() {
      return hash;
    }
  }
}
