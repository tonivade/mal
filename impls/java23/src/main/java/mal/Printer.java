package mal;

import static java.util.stream.Collectors.joining;
import static mal.Trampoline.done;
import static mal.Trampoline.map2;
import static mal.Trampoline.more;
import static mal.Trampoline.traverse;
import static org.apache.commons.text.StringEscapeUtils.escapeJava;

import mal.Mal.MalAtom;
import mal.Mal.MalConstant;
import mal.Mal.MalError;
import mal.Mal.MalFunction;
import mal.Mal.MalKeyword;
import mal.Mal.MalList;
import mal.Mal.MalMap;
import mal.Mal.MalNumber;
import mal.Mal.MalString;
import mal.Mal.MalSymbol;
import mal.Mal.MalVector;

public class Printer {

  public static String print(Mal val, boolean pretty) {
    return safePrint(val, pretty).run();
  }

  public static Trampoline<String> safePrint(Mal val, boolean pretty) {
    return more(() -> {
      return switch (val) {
        case MalConstant(var name) -> done(name);
        case MalSymbol(var name) -> done(name);
        case MalString(var value) when !pretty -> done(value);
        case MalString(var value) -> done("\"" + escapeJava(value) + "\"");
        case MalKeyword(var value) -> done(":" + value);
        case MalNumber(var value) -> done(Integer.toString(value));
        case MalList(var list) -> {
          yield traverse(list.stream().map(m -> safePrint(m, pretty)).toList())
            .map(l -> l.stream().collect(joining(" ", "(", ")")));
        }
        case MalVector(var list) -> {
          yield traverse(list.stream().map(m -> safePrint(m, pretty)).toList())
            .map(l -> l.stream().collect(joining(" ", "[", "]")));
        }
        case MalMap(var map) -> {
          yield traverse(map.entrySet().stream()
            .map(entry -> map2(safePrint(entry.getKey(), pretty), safePrint(entry.getValue(), pretty), (a, b) -> a + " " + b))
            .toList())
            .map(l -> l.stream().collect(joining(" ", "{", "}")));
        }
        case MalAtom atom -> safePrint(atom.getValue(), pretty).map(str -> "(atom " + str + ")");
        case MalFunction _ -> done("#function");
        case MalError(var exception) -> done("\"" + exception.getMessage() + "\"");
      };
    });
  }
}
