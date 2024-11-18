package mal;

import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;

public class Readline {
    
  private static final LineReader reader = LineReaderBuilder.builder()
    .option(LineReader.Option.DISABLE_EVENT_EXPANSION, true)
    .build();

  public static String readline(String prompt) {
    return reader.readLine(prompt);
  }
}
