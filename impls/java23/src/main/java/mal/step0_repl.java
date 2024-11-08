package mal;

import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;

public class step0_repl {
  
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

  public static void main(String[] args) {
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
      System.out.println(rep(line));
    }
  }
}
