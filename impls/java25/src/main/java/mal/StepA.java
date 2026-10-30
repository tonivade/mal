/*
 * Copyright (c) 2024-2025, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package mal;

import static mal.Evaluator.eval;
import static mal.Evaluator.safeEval;
import static mal.MalNode.DEBUG_EVAL;
import static mal.MalNode.EMPTY_LIST;
import static mal.MalNode.function;
import static mal.MalNode.list;
import static mal.MalNode.listOf;
import static mal.MalNode.string;
import static mal.MalNode.symbol;
import static mal.Printer.print;
import static mal.Reader.read;
import static mal.Readline.readline;

import java.util.Arrays;
import java.util.stream.Stream;

import org.jline.reader.EndOfFileException;
import org.jline.reader.UserInterruptException;

import mal.MalNode.MalFiber;

class StepA {

  private static final Env ENV = Env.global();

  static void setDebug(boolean debug) {
    if (debug) {
      ENV.set(DEBUG_EVAL, MalNode.TRUE);
    }
  }

  static String rep(String input) {
    return print(eval(read(input), ENV), true);
  }

  static void init(String... arguments) {
    ENV.set(symbol("eval"), function(args -> safeEval(args.get(0), ENV)));
    ENV.set(symbol("*ARGV*"), argv(arguments));
    ENV.set(symbol("*host-language*"), string("java25"));

    rep("(def! not (fn* (a) (if a false true)))");
    rep("(def! load-file (fn* (f) (eval (read-string (str \"(do \" (slurp f) \"\\nnil)\")))))");
    rep("(defmacro! cond (fn* (& xs) (if (> (count xs) 0) (list 'if (first xs) (if (> (count xs) 1) (nth xs 1) (throw \"odd number of forms to cond\")) (cons 'cond (rest (rest xs)))))))");
  }

  static void main(String... arguments) {
    String prompt = "user> ";

    init(arguments);

    if (arguments.length > 0) {
      // the path is passed as a string node, so quotes or backslashes in it are not parsed as mal code
      eval(listOf(symbol("load-file"), string(arguments[0])), ENV);
      return;
    }

    rep("(println (str \"Mal [\" *host-language* \"]\"))");

    Readline.completeFrom(ENV);

    // ctrl-c interrupts the evaluation, the trampoline stops at its next step
    var main = Thread.currentThread();
    Readline.onInterrupt(main::interrupt);

    while (true) {
      String line = null;
      try {
        line = readline(prompt);
      } catch (UserInterruptException e) {
        // Ignore
        continue;
      } catch (EndOfFileException e) {
        return;
      }
      // the fibers spawned by this line are children of a root fiber that never runs,
      // so ctrl-c can cancel them together with the evaluation
      var root = new MalFiber();
      try {
        // a ctrl-c pressed after the last evaluation finished must not stop this one
        Thread.interrupted();
        var input = line;
        IO.println(ScopedValue.where(MalFiber.CURRENT, root).call(() -> rep(input)));
      } catch (RuntimeException e) {
        if (e instanceof Trampoline.Interrupted) {
          root.cancel();
        }
        if (ENV.isDebugEval()) {
          e.printStackTrace(System.err);
        } else {
          System.err.println("ERROR: " + Printer.message(e));
        }
      }
    }
  }

  private static MalNode argv(String... arguments) {
    if (arguments.length < 1) {
      return EMPTY_LIST;
    }
    var copy = Arrays.copyOfRange(arguments, 1, arguments.length);
    return list(Stream.of(copy).<MalNode>map(MalNode::string).toList());
  }
}
