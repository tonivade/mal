package mal;

import static java.util.stream.Collectors.toUnmodifiableMap;
import static mal.Mal.CONCAT;
import static mal.Mal.CONS;
import static mal.Mal.FALSE;
import static mal.Mal.NIL;
import static mal.Mal.QUOTE;
import static mal.Mal.SPLICE_UNQUOTE;
import static mal.Mal.UNQUOTE;
import static mal.Mal.function;
import static mal.Mal.list;
import static mal.Mal.symbol;
import static mal.Printer.print;
import static mal.Trampoline.done;
import static mal.Trampoline.more;
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
    return safeEval(ast, env).run();
  }

  static Trampoline<Mal> safeEval(Mal ast, Env env) {
    return more(() -> doEval(ast, env));
  }

  private static Trampoline<Mal> doEval(Mal ast, Env env) {
    if (env.isDebugEval()) {
      System.out.println("EVAL: " + print(ast, true));
    }

    return switch (ast) {
      case MalSymbol(var name) -> evalSymbol(env,name);
      case MalList(var values) when !values.isEmpty() -> evalList(env, values);
      case MalVector(var values) when !values.isEmpty() -> evalVector(env,values);
      case MalMap(var map) when !map.isEmpty() -> evalMap(env,map);
      default -> done(ast);
    };
  }

  private static Trampoline<Mal> evalSymbol(Env env, String name) {
    var value = env.get(name);
    if (value == null) {
      throw new IllegalStateException(name + " not found");
    }
    return done(value);
  }

  private static Trampoline<Mal> evalList(Env env, List<Mal> values) {
    return switch (values.getFirst()) {

      case MalSymbol(var name) when name.equals("def!") -> {
        var key = (MalSymbol) values.get(1);
        yield safeEval(values.get(2), env).map(value -> {
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
          later.add(safeEval(iterator.next(), newEnv).map(value -> {
            newEnv.set(key, value);
            return value;
          }));
        }
        yield traverse(later).andThen(safeEval(values.get(2), newEnv));
      }

      case MalSymbol(var name) when name.equals("do") -> {
        var later = values.stream().skip(1).map(m -> safeEval(m, env)).toList();
        yield traverse(later).map(list -> list.getLast());
      }

      case MalSymbol(var name) when name.equals("if") -> {
        yield safeEval(values.get(1), env).flatMap(result -> {
          if (result != NIL && result != FALSE) {
            return safeEval(values.get(2), env);
          }
          return values.size() > 3 ? safeEval(values.get(3), env) : done(NIL);
        });
      }

      case MalSymbol(var name) when name.equals("fn*") -> {
        yield done(function(args -> {
          var newEnv = new Env(env, (MalIterable) values.get(1), args);
          return safeEval(values.get(2), newEnv);
        }));
      }

      case MalSymbol(var name) when name.equals("quote") -> {
        yield done(values.get(1));
      }

      case MalSymbol(var name) when name.equals("quasiquote") -> {
        var result = evalQuasiquote(values.get(1));
        yield safeEval(result, env);
      }

      case MalFunction function -> {
        yield function.apply(list(values.stream().skip(1).toList()));
      }

      default -> {
        var later = values.stream().map(m -> safeEval(m, env)).toList();
        yield traverse(later).flatMap(list -> safeEval(list(list), env));
      }
    };
  }

  private static Trampoline<Mal> evalVector(Env env, List<Mal> values) {
    var later = values.stream().map(m -> safeEval(m, env)).toList();
    return traverse(later).map(Mal::vector);
  }

  private static Trampoline<Mal> evalMap(Env env, Map<String, Mal> map) {
    var later = map.entrySet().stream()
      .map(entry -> safeEval(entry.getValue(), env).map(value -> Map.entry(entry.getKey(), value)))
      .toList();
    return traverse(later)
      .map(list -> list.stream().collect(toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue)))
      .map(Mal::map);
  }

  private static Mal evalQuasiquote(Mal value) {
    return switch (value) {
      case MalSymbol _ -> list(QUOTE, value);
      case MalMap _ -> list(QUOTE, value);
      case MalList(var values) when values.isEmpty() -> value;
      case MalList(var values) when values.getFirst().equals(UNQUOTE) -> values.get(1);
      case MalList(var values) -> getResult(values);
      case MalVector(var values) -> list(symbol("vec"), getResult(values));
      default -> value;
    }; 
  }

  private static MalList getResult(List<Mal> values) {
    if (values.isEmpty()) {
      return list(values);
    }
    var element = values.get(0);
    if (element instanceof MalList list && list.get(0).equals(SPLICE_UNQUOTE)) {
      return list(CONCAT, list.get(1), getResult(values.stream().skip(1).toList()));
    }
    return list(CONS, evalQuasiquote(element), getResult(values.stream().skip(1).toList()));
  }
}
