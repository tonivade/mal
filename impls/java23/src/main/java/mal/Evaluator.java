package mal;

import java.util.Map;

public class Evaluator {

  private static final Map<String, Mal.MalFunction> ENV = Map.of(
      "+", args -> args.stream().map(Mal.MalNumber.class::cast).reduce(Mal.MalNumber::sum).orElse(Mal.ZERO),
      "-", args -> args.stream().map(Mal.MalNumber.class::cast).reduce(Mal.MalNumber::subs).orElse(Mal.ZERO),
      "*", args -> args.stream().map(Mal.MalNumber.class::cast).reduce(Mal.MalNumber::mul).orElse(Mal.ZERO),
      "/", args -> args.stream().map(Mal.MalNumber.class::cast).reduce(Mal.MalNumber::div).orElse(Mal.ZERO)
  );

  static Mal eval(Mal ast) {
    return switch (ast) {
      case Mal.MalSymbol(var name) -> {
        var function = ENV.get(name);
        if (function == null) {
          throw new UnsupportedOperationException("not supported: " + ast);
        }
        yield function;
      }
      case Mal.MalList(var values) when !values.isEmpty() -> {
        var evaluated = values.stream().map(Evaluator::eval).toList();
        yield switch (evaluated.getFirst()) {
          case Mal.MalFunction function -> function.apply(Mal.list(evaluated.stream().skip(1).toList()));
          default -> throw new UnsupportedOperationException();
        };
      }
      default -> ast;
    };
  }
}
