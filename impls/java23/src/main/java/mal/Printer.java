package mal;

import java.util.stream.Collectors;

import org.apache.commons.text.StringEscapeUtils;

public class Printer {

  public static String print(Mal val) {
    return switch (val) {
      case Mal.MalConstant(var name) -> name;
      case Mal.MalSymbol(var name) -> name;
      case Mal.MalString(var value) -> "\"" + StringEscapeUtils.escapeJava(value) + "\"";
      case Mal.MalNumber(var value) -> Integer.toString(value);
      case Mal.MalList(var list) -> {
        yield list.stream().map(Printer::print).collect(Collectors.joining(" ", "(", ")"));
      }
      case Mal.MalVector(var list) -> {
        yield list.stream().map(Printer::print).collect(Collectors.joining(" ", "[", "]"));
      }
      default -> null;
    };
  }

}
