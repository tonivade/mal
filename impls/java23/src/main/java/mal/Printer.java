package mal;

import static java.util.stream.Collectors.joining;
import static mal.Trampoline.done;
import static mal.Trampoline.map2;
import static mal.Trampoline.more;
import static mal.Trampoline.traverse;
import static org.apache.commons.text.StringEscapeUtils.escapeJava;

import mal.MalNode.MalAtom;
import mal.MalNode.MalConstant;
import mal.MalNode.MalError;
import mal.MalNode.MalFunction;
import mal.MalNode.MalKeyword;
import mal.MalNode.MalList;
import mal.MalNode.MalMacro;
import mal.MalNode.MalMap;
import mal.MalNode.MalNumber;
import mal.MalNode.MalString;
import mal.MalNode.MalSymbol;
import mal.MalNode.MalVector;

public class Printer {

  public static String print(MalNode val, boolean pretty) {
    return safePrint(val, pretty).run();
  }

  public static Trampoline<String> safePrint(MalNode val, boolean pretty) {
    return more(() -> {
      return switch (val) {
        case MalConstant(var name, var _) -> done(name);
        case MalSymbol(var name, var _) -> done(name);
        case MalString(var value, var _) when !pretty -> done(value);
        case MalString(var value, var _) -> done("\"" + escapeJava(value) + "\"");
        case MalKeyword(var value, var _) -> done(":" + value);
        case MalNumber(var value, var _) -> done(Long.toString(value));
        case MalList(var list, var _) -> {
          yield traverse(list.stream().map(m -> safePrint(m, pretty)).toList())
            .map(l -> l.stream().collect(joining(" ", "(", ")")));
        }
        case MalVector(var list, var _) -> {
          yield traverse(list.stream().map(m -> safePrint(m, pretty)).toList())
            .map(l -> l.stream().collect(joining(" ", "[", "]")));
        }
        case MalMap(var map, var _) -> {
          yield traverse(map.entrySet().stream()
            .map(entry -> map2(safePrint(entry.getKey(), pretty), safePrint(entry.getValue(), pretty), (a, b) -> a + " " + b))
            .toList())
            .map(l -> l.stream().collect(joining(" ", "{", "}")));
        }
        case MalAtom atom -> safePrint(atom.getValue(), pretty).map(str -> "(atom " + str + ")");
        case MalFunction _ -> done("#function");
        case MalMacro _ -> done("#function");
        case MalError(var exception, var _) when exception instanceof MalException malException -> done(malException.getMessage(pretty));
        case MalError(var exception, var _) -> done(exception.getMessage());
      };
    });
  }
}
