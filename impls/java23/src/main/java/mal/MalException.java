package mal;

import static mal.MalNode.string;
import static mal.Printer.print;

import java.util.Objects;

public class MalException extends RuntimeException {

  private final MalNode node;

  public MalException(MalNode node) {
    this.node = Objects.requireNonNull(node);
  }

  public MalException(String message) {
    this(string(message));
  }

  @Override
  public String getMessage() {
    return getMessage(false);
  }

  public String getMessage(boolean pretty) {
    return print(node, pretty);
  }
}
