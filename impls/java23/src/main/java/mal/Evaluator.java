package mal;

import static java.util.stream.Collectors.toUnmodifiableMap;
import static mal.Mal.FALSE;
import static mal.Mal.NIL;
import static mal.Mal.function;
import static mal.Mal.list;
import static mal.Mal.map;
import static mal.Mal.vector;
import static mal.Printer.print;

import java.util.Map;

import mal.Mal.MalFunction;
import mal.Mal.MalIterable;
import mal.Mal.MalList;
import mal.Mal.MalMap;
import mal.Mal.MalSymbol;
import mal.Mal.MalVector;

public class Evaluator {

  static Mal eval(Mal ast, Env env) {
    if (env.isDebugEval()) {
      System.out.println("EVAL: " + print(ast, true));
    }

    return switch (ast) {

      case MalSymbol(var name) -> {
        var value = env.get(name);
        if (value == null) {
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
            var bindings = (MalIterable) values.get(1);
            for (var iterator = bindings.iterator(); iterator.hasNext();) {
              var key = (MalSymbol) iterator.next();
              var value = eval(iterator.next(), newEnv);
              newEnv.set(key, value);
            }
            yield eval(values.get(2), newEnv);
          }

          case MalSymbol(var name) when name.equals("do") -> {
            var evaluated = values.stream().skip(1).map(m -> eval(m, env)).toList();
            yield evaluated.getLast();
          }

          case MalSymbol(var name) when name.equals("if") -> {
            var result = eval(values.get(1), env);
            if (result != NIL && result != FALSE) {
              yield eval(values.get(2), env);
            }
            yield values.size() > 3 ? eval(values.get(3), env) : NIL;
          }

          case MalSymbol(var name) when name.equals("fn*") -> {
            yield function(args -> {
              var newEnv = new Env(env, (MalIterable) values.get(1), args);
              return eval(values.get(2), newEnv);
            });
          }

          case MalFunction function -> {
            yield function.apply(list(values.stream().skip(1).toList()));
          }

          default -> {
            yield eval(list(values.stream().map(m -> eval(m, env)).toList()), env);
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
