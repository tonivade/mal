package mal;

import static mal.Evaluator.eval;
import static mal.Printer.print;
import static mal.Reader.read;

import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;

public class step2_eval {

  static String rep(String input) {
    return print(eval(read(input), new Env()), true);
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
      try {
        System.out.println(rep(line));
      } catch (RuntimeException e) {
        System.err.println(e.getMessage());
      }
    }
  }
}
