/*
 * Copyright (c) 2024-2025, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package mal;

import static java.util.Map.entry;
import static java.util.stream.Collectors.toUnmodifiableMap;
import static mal.MalNode.FALSE;
import static mal.MalNode.NIL;
import static mal.MalNode.TRUE;
import static mal.MalNode.list;
import static mal.MalNode.map;
import static mal.MalNode.number;
import static mal.MalNode.string;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Stream;

import mal.MalNode.MalConstant;
import mal.MalNode.MalKey;
import mal.MalNode.MalMap;
import mal.MalNode.MalNumber;
import mal.MalNode.MalSequence;
import mal.MalNode.MalString;

class Interop {

  static MalNode toMal(Object value) {
    return switch (value) {
      case null -> NIL;
      case String s -> string(s);
      case Boolean b -> b ? TRUE : FALSE;
      case Integer i -> number(i);
      case Long l -> number(l);
      case Collection<?> l -> listToMal(l);
      case Map<?, ?> m -> mapToMal(m);
      case Stream<?> s -> listToMal(s.toList());
      case Object[] a -> listToMal(Stream.of(a).toList());
      default -> throw new MalException("unknown value " + value);
    };
  }

  static Object toJava(MalNode node) {
    return switch (node) {
      case MalString(var value, _) -> value;
      case MalNumber(var value, _) -> value;
      case MalConstant(var value, _) when value.equals("true") -> true;
      case MalConstant(var value, _) when value.equals("false") -> false;
      case MalConstant(var value, _) when value.equals("nil") -> null;
      default -> throw new MalException("not supported " + node);
    };
  }

  private static MalSequence listToMal(Collection<?> list) {
    return list(list.stream().map(Interop::toMal).toList());
  }

  private static MalMap mapToMal(Map<?, ?> map) {
    return map(map.entrySet().stream()
        .map(entry -> entry(convertKey(entry.getKey()), toMal(entry.getValue())))
        .collect(toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue)));
  }

  private static MalKey convertKey(Object value) {
    return switch (value) {
      case String s -> string(s);
      case null, default -> throw new MalException("invalid key" + value);
    };
  }
}
