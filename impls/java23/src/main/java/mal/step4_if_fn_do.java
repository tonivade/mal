package mal;

import static mal.Evaluator.eval;
import static mal.Printer.print;
import static mal.Reader.read;

import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;

public class step4_if_fn_do {

  private static final Env ENV = new Env();

  static String rep(String input) {
    return print(eval(read(input), ENV));
  }

  public static void main(String[] args) {
    String prompt = "user> ";

    // NOT function
    rep("(def! not (fn* (a) (if a false true)))");

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
      } catch (IllegalStateException | UnsupportedOperationException e) {
        System.err.println(e.getMessage());
      }
    }
  }
}
