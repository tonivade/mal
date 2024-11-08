package mal;

import static java.util.stream.Collectors.toUnmodifiableMap;
import static mal.Mal.NIL;
import static mal.Mal.list;
import static mal.Mal.map;
import static mal.Mal.vector;
import static mal.Printer.print;

import java.util.Map;

import mal.Mal.MalFunction;
import mal.Mal.MalList;
import mal.Mal.MalMap;
import mal.Mal.MalSymbol;
import mal.Mal.MalVector;

public class Evaluator {

  static Mal eval(Mal ast, Env env) {
    if (env.isDebugEval()) {
      System.out.println("EVAL: " + print(ast));
    }

    return switch (ast) {

      case MalSymbol(var name) -> {
        var value = env.get(name);
        if (value == NIL) {
          throw new IllegalStateException(name + " not found");
        }
        yield value;
      }

      case MalList(var values) when !values.isEmpty() -> {
        yield switch (values.getFirst()) {
          case MalSymbol(var name) when name.equals("def!") -> {
            var key = (MalSymbol) values.get(1);
            var value = eval(values.get(2), env);
            env.set(key, value);
            yield value;
          }
          case MalSymbol(var name) when name.equals("let*") -> {
            var newEnv = new Env(env);
            @SuppressWarnings("unchecked")
            var bindings = (Iterable<Mal>) values.get(1);
            for (var iterator = bindings.iterator(); iterator.hasNext();) {
              var key = (MalSymbol) iterator.next();
              var value = eval(iterator.next(), newEnv);
              newEnv.set(key, value);
            }
            yield eval(values.get(2), newEnv);
          }
          default -> {
            var first = eval(values.getFirst(), env);
            yield switch (first) {
              case MalFunction function -> function.apply(list(values.stream().skip(1).map(m -> eval(m, env)).toList()));
              default -> throw new UnsupportedOperationException();
            };
          }
        };
      }

      case MalVector(var values) when !values.isEmpty() -> {
        var evaluated = values.stream().map(m -> eval(m, env)).toList();
        yield vector(evaluated);
      }

      case MalMap(var map) when !map.isEmpty() -> {
        var evaluated = map.entrySet().stream()
          .collect(toUnmodifiableMap(Map.Entry::getKey, entry -> eval(entry.getValue(), env)));
        yield map(evaluated);
      }

      default -> ast;
    };
  }
}
