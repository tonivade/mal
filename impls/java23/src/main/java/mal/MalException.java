package mal;

import static mal.Printer.print;

public class MalException extends RuntimeException {

  public MalException(Mal value) {
    this(print(value, false));
  }

  public MalException(String message) {
    super(message);
  }
}
