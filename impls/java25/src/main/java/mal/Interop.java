/*
 * Copyright (c) 2024-2025, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package mal;

import static java.util.Map.entry;
import static java.util.stream.Collectors.toMap;
import static mal.MalNode.FALSE;
import static mal.MalNode.NIL;
import static mal.MalNode.TRUE;
import static mal.MalNode.list;
import static mal.MalNode.map;
import static mal.MalNode.number;
import static mal.MalNode.string;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import mal.MalNode.MalKey;

class Interop {

  static MalNode toMal(Object value) {
    return switch (value) {
      case null -> NIL;
      case String s -> string(s);
      case Boolean b -> b ? TRUE : FALSE;
      case Integer i -> number(i);
      case Long l -> number(l);
      case Collection<?> l -> list(listToMal(l));
      case Map<?, ?> m -> map(mapToMal(m));
      default -> throw new MalException("unknown value " + value);
    };
  }

  static List<MalNode> listToMal(Collection<?> list) {
    return list.stream().map(Interop::toMal).toList();
  }

  static Map<MalKey, MalNode> mapToMal(Map<?, ?> map) {
    return map.entrySet().stream()
        .map(entry -> entry(convertKey(entry.getKey()), toMal(entry.getValue())))
        .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  static MalKey convertKey(Object value) {
    return switch (value) {
      case String s -> string(s);
      case null, default -> throw new MalException("invalid key" + value);
    };
  }
}
