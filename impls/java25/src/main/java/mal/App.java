/*
 * Copyright (c) 2024-2025, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package mal;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "mal", version = "0.1-SNAPSHOT", description = "mal language repl")
public class App implements Runnable {

  @Option(names = "--step", description = "step to execute", defaultValue = "stepA_mal")
  private String step;

  @Parameters(description = "rest of arguments")
  private String[] rest = new String[] {};

  @Override
  public void run() {
    switch (step) {
      case "step0_repl" -> {
        step0_repl.main(rest);
      }
      case "step1_read_print" -> {
        step1_read_print.main(rest);
      }
      case "step2_eval" -> {
        step2_eval.main(rest);
      }
      case "step3_env" -> {
        step3_env.main(rest);
      }
      case "step4_if_fn_do" -> {
        step4_if_fn_do.main(rest);
      }
      case "step5_tco" -> {
        step5_tco.main(rest);
      }
      case "step6_file" -> {
        step6_file.main(rest);
      }
      case "step7_quote" -> {
        step7_quote.main(rest);
      }
      case "step8_macros" -> {
        step8_macros.main(rest);
      }
      case "step9_try" -> {
        step9_try.main(rest);
      }
      case "stepA_mal" -> {
        stepA_mal.main(rest);
      }
      default -> throw new IllegalArgumentException("invalid step: " + step);
    }
  }

  public static void main(String... args) {
    var result = new CommandLine(new App()).execute(args);
    System.exit(result);
  }
}
