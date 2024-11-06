package mal;

import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;

/*
 * repl
 */
public class step0_repl {
  
  static String read(String string) {
    return string;
  }

  static String eval(String ast, String env) {
    return ast;
  }

  static String print(String exp) {
    return exp;
  }

  static String repl(String line) {
    return print(eval(read(line), null));
  }

  public static void main(String[] args) {
    String prompt = "user> ";

    LineReader reader = LineReaderBuilder.builder().build();
    while (true) {
      String line = null;
      try {
          line = reader.readLine(prompt);
      } catch (UserInterruptException e) {
        // Ignore
      } catch (EndOfFileException e) {
        return;
      }
      System.out.println(repl(line));
    }
  }
}
