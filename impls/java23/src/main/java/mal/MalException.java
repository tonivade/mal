package mal;

import static mal.Printer.print;

public class MalException extends RuntimeException {

  public MalException(MalNode value) {
    this(print(value, false));
  }

  public MalException(String message) {
    super(message);
  }
}
