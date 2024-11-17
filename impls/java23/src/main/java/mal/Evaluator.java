package mal;

import static java.util.stream.Collectors.toUnmodifiableMap;
import static mal.MalNode.CONCAT;
import static mal.MalNode.CONS;
import static mal.MalNode.FALSE;
import static mal.MalNode.NIL;
import static mal.MalNode.QUOTE;
import static mal.MalNode.SPLICE_UNQUOTE;
import static mal.MalNode.UNQUOTE;
import static mal.MalNode.error;
import static mal.MalNode.function;
import static mal.MalNode.list;
import static mal.MalNode.symbol;
import static mal.Printer.print;
import static mal.Trampoline.done;
import static mal.Trampoline.map2;
import static mal.Trampoline.more;
import static mal.Trampoline.traverse;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import mal.MalNode.MalError;
import mal.MalNode.MalFunction;
import mal.MalNode.MalKey;
import mal.MalNode.MalSequence;
import mal.MalNode.MalList;
import mal.MalNode.MalMacro;
import mal.MalNode.MalMap;
import mal.MalNode.MalSymbol;
import mal.MalNode.MalVector;

public class Evaluator {

  static MalNode eval(MalNode ast, Env env) {
    return safeEval(ast, env).run();
  }

  static MalNode tryEval(MalNode ast, Env env) {
    try {
      return eval(ast, env);
    } catch (RuntimeException e) {
      return error(e);
    }
  }

  static Trampoline<MalNode> safeEval(MalNode ast, Env env) {
    return more(() -> {
      if (env.isDebugEval()) {
        System.out.println("EVAL: " + print(ast, true));
      }

      return switch (ast) {
        case MalSymbol(var name) -> evalSymbol(env,name);
        case MalList(var values) when !values.isEmpty() -> evalList(env, values);
        case MalVector(var values) when !values.isEmpty() -> evalVector(env,values);
        case MalMap(var map) when !map.isEmpty() -> evalMap(env, map);
        default -> done(ast);
      };
    });
  }

  private static Trampoline<MalNode> evalSymbol(Env env, String name) {
    var value = env.get(name);
    if (value == null) {
      throw new MalException(name + " not found");
    }
    return done(value);
  }

  private static Trampoline<MalNode> evalList(Env env, List<MalNode> values) {
    return switch (values.getFirst()) {

      case MalSymbol(var name) when name.equals("def!") -> {
        var key = (MalSymbol) values.get(1);
        yield safeEval(values.get(2), env).map(value -> {
          env.set(key, value);
          return value;
        });
      }

      case MalSymbol(var name) when name.equals("defmacro!") -> {
        var key = (MalSymbol) values.get(1);
        yield safeEval(values.get(2), env).map(value -> {
          var macro = ((MalFunction) value).toMacro();
          env.set(key, macro);
          return macro;
        });
      }

      case MalSymbol(var name) when name.equals("let*") -> {
        var newEnv = new Env(env);
        var bindings = (MalSequence) values.get(1);
        List<Trampoline<MalNode>> later = new ArrayList<>();
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
        yield traverse(later).map(List::getLast);
      }

      case MalSymbol(var name) when name.equals("try*") -> {
        var body = values.get(1);
        var result = tryEval(body, env);
        yield switch (result) {
          case MalError error when values.size() < 3 -> {
            throw new MalException(error);
          }
          case MalError error -> {
            var catch_ = (MalList) values.get(2);
            var symbol = (MalSymbol) catch_.get(1);
            var recover = catch_.get(2);
            var newEnv = new Env(env, Map.of(symbol.name(), error));
            yield safeEval(recover, newEnv);
          }
          default -> done(result);
        };
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
          var newEnv = new Env(env, (MalSequence) values.get(1), args);
          return safeEval(values.get(2), newEnv);
        }));
      }

      case MalSymbol(var name) when name.equals("quote") -> {
        yield done(values.get(1));
      }

      case MalSymbol(var name) when name.equals("quasiquote") -> {
        yield evalQuasiquote(values.get(1)).flatMap(result -> safeEval(result, env));
      }

      case MalMacro macro -> {
        yield macro.apply(list(skipFirst(values)))
          .flatMap(next -> safeEval(next, env));
      }

      case MalFunction function -> {
        yield traverse(skipFirst(values).stream().map(m -> safeEval(m, env)).toList())
          .flatMap(args -> function.apply(list(args)));
      }

      default -> {
        yield safeEval(values.getFirst(), env).<MalNode>map(callable -> {
          List<MalNode> next = new ArrayList<>();
          next.add(callable);
          next.addAll(skipFirst(values));
          return list(next);
        }).flatMap(list -> safeEval(list, env));
      }
    };
  }

  private static Trampoline<MalNode> evalVector(Env env, List<MalNode> values) {
    var later = values.stream().map(m -> safeEval(m, env)).toList();
    return traverse(later).map(MalNode::vector);
  }

  private static Trampoline<MalNode> evalMap(Env env, Map<MalKey, MalNode> map) {
    var later = map.entrySet().stream()
      .map(entry -> safeEval(entry.getValue(), env).map(value -> Map.entry(entry.getKey(), value)))
      .toList();
    return traverse(later)
      .map(list -> list.stream().collect(toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue)))
      .map(MalNode::map);
  }

  private static Trampoline<MalNode> evalQuasiquote(MalNode value) {
    return switch (value) {
      case MalSymbol _ -> done(list(QUOTE, value));
      case MalMap _ -> done(list(QUOTE, value));
      case MalList(var values) when values.isEmpty() -> done(value);
      case MalList(var values) when values.getFirst().equals(UNQUOTE) -> done(values.get(1));
      case MalList(var values) -> recursiveQuasiquote(values);
      case MalVector(var values) -> recursiveQuasiquote(values).map(next -> list(symbol("vec"), next));
      default -> done(value);
    }; 
  }

  private static Trampoline<MalNode> recursiveQuasiquote(List<MalNode> values) {
    if (values.isEmpty()) {
      return done(list());
    }
    return more(() -> {
      var element = values.getFirst();
      if (element instanceof MalList list && list.get(0).equals(SPLICE_UNQUOTE)) {
        return recursiveQuasiquote(skipFirst(values))
          .map(result -> list(CONCAT, list.get(1), result));
      }
      return map2(recursiveQuasiquote(skipFirst(values)), evalQuasiquote(element), 
        (next, eval) -> list(CONS, eval, next));
    });
  }

  private static List<MalNode> skipFirst(List<MalNode> values) {
    return values.stream().skip(1).toList();
  }
}
