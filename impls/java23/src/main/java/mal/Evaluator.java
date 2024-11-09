package mal;

import static java.util.stream.Collectors.toUnmodifiableMap;
import static mal.Mal.FALSE;
import static mal.Mal.NIL;
import static mal.Mal.function;
import static mal.Mal.list;
import static mal.Printer.print;
import static mal.Trampoline.done;
import static mal.Trampoline.traverse;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import mal.Mal.MalFunction;
import mal.Mal.MalIterable;
import mal.Mal.MalList;
import mal.Mal.MalMap;
import mal.Mal.MalSymbol;
import mal.Mal.MalVector;

public class Evaluator {

  static Mal eval(Mal ast, Env env) {
    return doEval(ast, env).run();
  }

  static Trampoline<Mal> doEval(Mal ast, Env env) {
    return Trampoline.more(() -> {
      if (env.isDebugEval()) {
        System.out.println("EVAL: " + print(ast, true));
      }

      return switch (ast) {

        case MalSymbol(var name) -> {
          var value = env.get(name);
          if (value == null) {
            throw new IllegalStateException(name + " not found");
          }
          yield done(value);
        }

        case MalList(var values) when !values.isEmpty() -> {
          yield switch (values.getFirst()) {

            case MalSymbol(var name) when name.equals("def!") -> {
              var key = (MalSymbol) values.get(1);
              yield doEval(values.get(2), env).map(value -> {
                env.set(key, value);
                return value;
              });
            }

            case MalSymbol(var name) when name.equals("let*") -> {
              var newEnv = new Env(env);
              var bindings = (MalIterable) values.get(1);
              List<Trampoline<Mal>> later = new ArrayList<>();
              for (var iterator = bindings.iterator(); iterator.hasNext();) {
                var key = (MalSymbol) iterator.next();
                later.add(doEval(iterator.next(), newEnv).map(value -> {
                  newEnv.set(key, value);
                  return value;
                }));
              }
              yield traverse(later).andThen(doEval(values.get(2), newEnv));
            }

            case MalSymbol(var name) when name.equals("do") -> {
              var later = values.stream().skip(1).map(m -> doEval(m, env)).toList();
              yield traverse(later).map(list -> list.getLast());
            }

            case MalSymbol(var name) when name.equals("if") -> {
              yield doEval(values.get(1), env).flatMap(result -> {
                if (result != NIL && result != FALSE) {
                  return doEval(values.get(2), env);
                }
                return values.size() > 3 ? doEval(values.get(3), env) : done(NIL);
              });
            }

            case MalSymbol(var name) when name.equals("fn*") -> {
              yield done(function(args -> {
                var newEnv = new Env(env, (MalIterable) values.get(1), args);
                return doEval(values.get(2), newEnv);
              }));
            }

            case MalFunction function -> {
              yield function.apply(list(values.stream().skip(1).toList()));
            }

            default -> {
              var later = values.stream().map(m -> doEval(m, env)).toList();
              yield traverse(later).flatMap(list -> doEval(list(list), env));
            }
          };
        }

        case MalVector(var values) when !values.isEmpty() -> {
          var later = values.stream().map(m -> doEval(m, env)).toList();
          yield traverse(later).map(Mal::vector);
        }

        case MalMap(var map) when !map.isEmpty() -> {
          var later = map.entrySet().stream()
            .map(entry -> doEval(entry.getValue(), env).map(value -> Map.entry(entry.getKey(), value)))
            .toList();
          yield traverse(later)
            .map(list -> list.stream().collect(toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue)))
            .map(Mal::map);
        }

        default -> done(ast);
      };
    });
  }
}
