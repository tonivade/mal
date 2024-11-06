package mal;

import java.util.Arrays;

public class App {
  public static void main(String[] args) {
    if (args.length == 0) {
      throw new IllegalArgumentException("mandatory step");
    }
    switch (args[0]) {
      case "step0_repl" -> {
        step0_repl.main(Arrays.copyOfRange(args, 1, args.length));
      }
      default -> throw new IllegalArgumentException("invalid step: " + args[0]);
    }
  }
}
