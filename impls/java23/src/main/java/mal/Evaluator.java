package mal;

import static mal.Mal.ZERO;
import static mal.Mal.list;

import java.util.Map;

import mal.Mal.MalFunction;
import mal.Mal.MalList;
import mal.Mal.MalNumber;
import mal.Mal.MalSymbol;

public class Evaluator {

  private static final Map<String, MalFunction> ENV = Map.of(
      "+", args -> args.stream().map(MalNumber.class::cast).reduce(MalNumber::sum).orElse(ZERO),
      "-", args -> args.stream().map(MalNumber.class::cast).reduce(MalNumber::subs).orElse(ZERO),
      "*", args -> args.stream().map(MalNumber.class::cast).reduce(MalNumber::mul).orElse(ZERO),
      "/", args -> args.stream().map(MalNumber.class::cast).reduce(MalNumber::div).orElse(ZERO)
  );

  static Mal eval(Mal ast) {
    return switch (ast) {
      case MalSymbol(var name) -> {
        var function = ENV.get(name);
        if (function == null) {
          throw new UnsupportedOperationException("not supported: " + ast);
        }
        yield function;
      }
      case MalList(var values) when !values.isEmpty() -> {
        var evaluated = values.stream().map(Evaluator::eval).toList();
        yield switch (evaluated.getFirst()) {
          case MalFunction function -> function.apply(list(evaluated.stream().skip(1).toList()));
          default -> throw new UnsupportedOperationException();
        };
      }
      default -> ast;
    };
  }
}
