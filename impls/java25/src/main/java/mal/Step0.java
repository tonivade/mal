/*
 * Copyright (c) 2024-2025, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package mal;

import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;

class Step0 {

  static String read(String input) {
    return input;
  }

  static String eval(String ast) {
    return ast;
  }

  static String print(String exp) {
    return exp;
  }

  static String rep(String input) {
    return print(eval(read(input)));
  }

  static void main(String... args) {
    String prompt = "user> ";

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
      IO.println(rep(line));
    }
  }
}
