package mal;

import java.util.stream.Collectors;

public class Printer {

  public static String print(Mal val) {
    return switch (val) {
      case Mal.MalSymbol(var name) -> name;
      case Mal.MalNumber(var value) -> Integer.toString(value);
      case Mal.MalList(var list) -> {
        yield list.stream().map(Printer::print).collect(Collectors.joining(" ", "(", ")"));
      }
      default -> null;
    };
  }

}
