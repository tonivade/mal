/*
 * Copyright (c) 2024-2025, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package mal;

import static java.util.stream.Collectors.joining;
import static mal.Trampoline.done;
import static mal.Trampoline.more;
import static mal.Trampoline.traverse;
import static mal.Trampoline.sequence;
import static mal.Trampoline.zip;
import static org.apache.commons.text.StringEscapeUtils.escapeJava;

import java.util.ArrayList;
import java.util.List;

import mal.MalNode.MalAtom;
import mal.MalNode.MalConstant;
import mal.MalNode.MalError;
import mal.MalNode.MalFunction;
import mal.MalNode.MalKeyword;
import mal.MalNode.MalLazy;
import mal.MalNode.MalMacro;
import mal.MalNode.MalMap;
import mal.MalNode.MalNumber;
import mal.MalNode.MalSequence;
import mal.MalNode.MalString;
import mal.MalNode.MalSymbol;
import mal.MalNode.MalVector;

class Printer {

  static String print(MalNode val, boolean pretty) {
    return safePrint(val, pretty).run();
  }

  static Trampoline<String> safePrint(MalNode val, boolean pretty) {
    return more(() -> {
      return switch (val) {
        case MalConstant(var name, _) -> done(name);
        case MalSymbol(var name, _) -> done(name);
        case MalString(var value, _) when !pretty -> done(value);
        case MalString(var value, _) -> done("\"" + escapeJava(value) + "\"");
        case MalKeyword(var value, _) -> done(":" + value);
        case MalNumber(var value, _) -> done(Long.toString(value));
        case MalMap(var map, _) -> {
          yield traverse(map.entrySet(), entry -> zip(safePrint(entry.getKey(), pretty), safePrint(entry.getValue(), pretty), Printer::concat))
            .map(l -> l.stream().collect(joining(" ", "{", "}")));
        }
        case MalAtom atom -> safePrint(atom.getValue(), pretty).map(str -> "(atom " + str + ")");
        case MalFunction _ -> done("#function");
        case MalMacro _ -> done("#function");
        case MalError(var exception, _) when exception instanceof MalException malException -> done(malException.getMessage(pretty));
        case MalError(var exception, _) -> done(exception.getMessage());
        case MalLazy _ -> done("#lazy");
        case MalVector(var list, _) -> {
          yield sequence(list.stream().map(m -> safePrint(m, pretty)).toList())
            .map(l -> l.stream().collect(joining(" ", "[", "]")));
        }
        case MalSequence sequence when sequence.isEmpty() -> done("()");
        case MalSequence sequence -> {
          List<Trampoline<String>> parts = new ArrayList<>();
          parts.add(safePrint(sequence.first(), pretty));
          for (var current = sequence.rest(); !current.isEmpty();) {
            parts.add(safePrint(current.first(), pretty));
            current = current.rest();
          }
          yield sequence(parts).map(l -> l.stream().collect(joining(" ", "(", ")")));
        }
      };
    });
  }

  private static String concat(String a, String b) {
    return a + " " + b;
  }
}
