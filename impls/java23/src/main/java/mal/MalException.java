package mal;

public class MalException extends RuntimeException {

  public MalException(Mal value) {
    super(value.toString());
  }
}
