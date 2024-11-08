package mal;

import static java.util.stream.Collectors.joining;
import static org.apache.commons.text.StringEscapeUtils.escapeJava;

import mal.Mal.MalConstant;
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
    return switch (val) {
      case MalConstant(var name) -> name;
      case MalSymbol(var name) -> name;
      case MalString(var value) when !pretty -> value;
      case MalString(var value) -> "\"" + escapeJava(value) + "\"";
      case MalKeyword(var value) -> ":" + value;
      case MalNumber(var value) -> Integer.toString(value);
      case MalList(var list) -> {
        yield list.stream()
          .map(m -> print(m, pretty))
          .collect(joining(" ", "(", ")"));
      }
      case MalVector(var list) -> {
        yield list.stream()
          .map(m -> print(m, pretty))
          .collect(joining(" ", "[", "]"));
      }
      case MalMap(var map) -> {
        yield map.entrySet().stream()
          .map(entry -> entry.getKey() + " " + print(entry.getValue(), pretty))
          .collect(joining(" ", "{", "}"));
      }
      case MalFunction _ -> "#function";
    };
  }
}
