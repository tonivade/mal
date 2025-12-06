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

  @Option(names = "--debug", description = "enable debug", defaultValue = "false")
  private boolean debug;

  @Parameters(description = "rest of arguments")
  private String[] rest = new String[] {};

  @Override
  public void run() {
    switch (step) {
      case "step0_repl" -> {
        Step0.main(rest);
      }
      case "step1_read_print" -> {
        Step1.main(rest);
      }
      case "step2_eval" -> {
        Step2.main(rest);
      }
      case "step3_env" -> {
        Step3.main(rest);
      }
      case "step4_if_fn_do" -> {
        Step4.main(rest);
      }
      case "step5_tco" -> {
        Step5.main(rest);
      }
      case "step6_file" -> {
        Step6.main(rest);
      }
      case "step7_quote" -> {
        Step7.main(rest);
      }
      case "step8_macros" -> {
        Step8.main(rest);
      }
      case "step9_try" -> {
        Step9.main(rest);
      }
      case "stepA_mal" -> {
        StepA.setDebug(debug);
        StepA.main(rest);
      }
      default -> throw new IllegalArgumentException("invalid step: " + step);
    }
  }

  public static void main(String... args) {
    var result = new CommandLine(new App()).execute(args);
    System.exit(result);
  }
}
