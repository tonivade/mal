package mal;

import static mal.Mal.ZERO;
import static mal.Mal.list;
import static mal.Mal.map;
import static mal.Mal.vector;

import java.util.Map;
import java.util.stream.Collectors;

import mal.Mal.MalFunction;
import mal.Mal.MalList;
import mal.Mal.MalMap;
import mal.Mal.MalNumber;
import mal.Mal.MalSymbol;
import mal.Mal.MalVector;

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

      case MalVector(var values) when !values.isEmpty() -> {
        var evaluated = values.stream().map(Evaluator::eval).toList();
        yield vector(evaluated);
      }

      case MalMap(var map) when !map.isEmpty() -> {
        var evaluated = map.entrySet().stream()
          .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> eval(entry.getValue())));
        yield map(evaluated);
      }

      default -> ast;
    };
  }
}
