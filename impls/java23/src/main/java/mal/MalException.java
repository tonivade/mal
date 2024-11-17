package mal;

import static java.util.Objects.requireNonNull;
import static mal.MalNode.string;
import static mal.Printer.print;

public class MalException extends RuntimeException {

  private final MalNode node;

  public MalException(MalNode node) {
    this.node = requireNonNull(node);
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
