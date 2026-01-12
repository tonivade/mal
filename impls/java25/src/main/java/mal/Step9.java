/*
 * Copyright (c) 2024-2025, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package mal;

import static mal.Evaluator.eval;
import static mal.MalNode.EMPTY_LIST;
import static mal.MalNode.function;
import static mal.MalNode.list;
import static mal.MalNode.symbol;
import static mal.Printer.print;
import static mal.Reader.read;

import java.util.Arrays;
import java.util.stream.Stream;

import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;

class Step9 {

  private static final Env ENV = new Env();

  static String rep(String input) {
    return print(eval(read(input), ENV), true);
  }

  static void main(String... arguments) {
    String prompt = "user> ";

    rep("(def! not (fn* (a) (if a false true)))");
    rep("(def! load-file (fn* (f) (eval (read-string (str \"(do \" (slurp f) \"\\nnil)\")))))");
    rep("(defmacro! cond (fn* (& xs) (if (> (count xs) 0) (list 'if (first xs) (if (> (count xs) 1) (nth xs 1) (throw \"odd number of forms to cond\")) (cons 'cond (rest (rest xs)))))))");

    ENV.set(symbol("eval"), function(args -> {
      return eval(args.get(0), ENV);
    }));

    ENV.set(symbol("*ARGV*"), argv(arguments));

    if (arguments.length > 0) {
      rep("(load-file \"" + arguments[0] + "\")");
      return;
    }

    var reader = LineReaderBuilder.builder()
      .option(LineReader.Option.DISABLE_EVENT_EXPANSION, true)
      .build();
    while (true) {
      String line = null;
      try {
        line = reader.readLine(prompt);
      } catch (UserInterruptException e) {
        // Ignore
      } catch (EndOfFileException e) {
        return;
      }
      try {
        IO.println(rep(line));
      } catch (RuntimeException e) {
        System.err.println("ERROR: " + e.getMessage());
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
