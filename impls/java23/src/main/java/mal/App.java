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
      case "step1_read_print" -> {
        step1_read_print.main(Arrays.copyOfRange(args, 1, args.length));
      }
      case "step2_eval" -> {
        step2_eval.main(Arrays.copyOfRange(args, 1, args.length));
      }
      case "step3_env" -> {
        step3_env.main(Arrays.copyOfRange(args, 1, args.length));
      }
      case "step4_if_fn_do" -> {
        step4_if_fn_do.main(Arrays.copyOfRange(args, 1, args.length));
      }
      case "step5_tco" -> {
        step5_tco.main(Arrays.copyOfRange(args, 1, args.length));
      }
      case "step6_file" -> {
        step6_file.main(Arrays.copyOfRange(args, 1, args.length));
      }
      case "step7_quote" -> {
        step7_quote.main(Arrays.copyOfRange(args, 1, args.length));
      }
      case "step8_macros" -> {
        step8_macros.main(Arrays.copyOfRange(args, 1, args.length));
      }
      default -> throw new IllegalArgumentException("invalid step: " + args[0]);
    }
  }
}
