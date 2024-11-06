package mal;

import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;

public class step1_read_print {
  
  static Mal read(String input) {
    return Reader.parse(input);
  }

  static Mal eval(Mal ast) {
    return ast;
  }

  static String print(Mal ast) {
    return Printer.print(ast);
  }

  static String repl(String input) {
    return print(eval(read(input)));
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
