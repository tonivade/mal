package mal;

import static java.util.stream.Collectors.joining;

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

  public static String print(Mal val) {
    return switch (val) {
      case MalConstant(var name) -> name;
      case MalSymbol(var name) -> name;
      case MalString(var value) -> "\"" + value + "\"";
      case MalKeyword(var value) -> ":" + value;
      case MalNumber(var value) -> Integer.toString(value);
      case MalList(var list) -> {
        yield list.stream()
          .map(Printer::print)
          .collect(joining(" ", "(", ")"));
      }
      case MalVector(var list) -> {
        yield list.stream()
          .map(Printer::print)
          .collect(joining(" ", "[", "]"));
      }
      case MalMap(var map) -> {
        yield map.entrySet().stream()
          .map(entry -> entry.getKey() + " " + print(entry.getValue()))
          .collect(joining(" ", "{", "}"));
      }
      case MalFunction _ -> "#function";
    };
  }
}
