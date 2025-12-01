/*
 * Copyright (c) 2024-2025, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
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
import static mal.MalNode.lambda;
import static mal.MalNode.list;
import static mal.MalNode.symbol;
import static mal.Printer.print;
import static mal.Trampoline.done;
import static mal.Trampoline.more;
import static mal.Trampoline.sequence;
import static mal.Trampoline.zip;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import mal.MalNode.MalFunction;
import mal.MalNode.MalKey;
import mal.MalNode.MalList;
import mal.MalNode.MalMacro;
import mal.MalNode.MalMap;
import mal.MalNode.MalNumber;
import mal.MalNode.MalSequence;
import mal.MalNode.MalSymbol;
import mal.MalNode.MalVector;
import mal.lib.Strings;

class Evaluator {

  private static final String IMPORT = "import";
  private static final String REQUIRE = "require";
  private static final String QUASIQUOTE = "quasiquote";
  private static final String QUOTE_ = "quote";
  private static final String FN = "fn*";
  private static final String IF = "if";
  private static final String TRY = "try*";
  private static final String LET = "let*";
  private static final String DEFMACRO = "defmacro!";
  private static final String DEF = "def!";

  private static final Map<MalSymbol, Map<String, MalNode>> LIBS = Map.of(symbol("str"), Strings.NS);

  static MalNode eval(MalNode ast, Env env) {
    return safeEval(ast, env).run();
  }

  static Trampoline<MalNode> safeEval(MalNode ast, Env env) {
    return more(() -> {
      if (env.isDebugEval()) {
        IO.println("EVAL: " + print(ast, true));
      }

      return switch (ast) {
        case MalSymbol(var name, _) -> evalSymbol(env, name);
        case MalList(var values, _) when !values.isEmpty() -> evalList(env, values);
        case MalVector(var values, _) when !values.isEmpty() -> evalVector(env,values);
        case MalMap(var map, _) when !map.isEmpty() -> evalMap(env, map);
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

      case MalSymbol(var name, _) when name.equals(DEF) -> {
        var key = (MalSymbol) values.get(1);
        yield safeEval(values.get(2), env).map(value -> {
          env.set(key, value);
          return value;
        });
      }

      case MalSymbol(var name, _) when name.equals(DEFMACRO) -> {
        var key = (MalSymbol) values.get(1);
        yield safeEval(values.get(2), env).map(value -> {
          var macro = ((MalFunction) value).toMacro();
          env.set(key, macro);
          return macro;
        });
      }

      case MalSymbol(var name, _) when name.equals(LET) -> {
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
        yield sequence(later).andThen(safeEval(values.get(2), newEnv));
      }

      case MalSymbol(var name, _) when name.equals("do") -> {
        var later = values.stream().skip(1).map(m -> safeEval(m, env)).toList();
        yield sequence(later).map(List::getLast);
      }

      case MalSymbol(var name, _) when name.equals(TRY) -> {
        var body = values.get(1);
        try {
          yield done(eval(body, env));
        } catch (RuntimeException e) {
          if (values.size() < 3) {
            throw new MalException(e.getMessage());
          }
          var catch_ = (MalList) values.get(2);
          var symbol = (MalSymbol) catch_.get(1);
          var recover = catch_.get(2);
          var newEnv = new Env(env, Map.of(symbol.name(), error(e)));
          yield safeEval(recover, newEnv);
        }
      }

      case MalSymbol(var name, _) when name.equals(IF) -> {
        yield safeEval(values.get(1), env).flatMap(result -> {
          if (result != NIL && result != FALSE) {
            return safeEval(values.get(2), env);
          }
          return values.size() > 3 ? safeEval(values.get(3), env) : done(NIL);
        });
      }

      case MalSymbol(var name, _) when name.equals(FN) -> {
        yield done(function(args -> {
          var newEnv = new Env(env, (MalSequence) values.get(1), args);
          return safeEval(values.get(2), newEnv);
        }));
      }

      case MalSymbol(var name, _) when name.equals(QUOTE_) -> {
        yield done(values.get(1));
      }

      case MalSymbol(var name, _) when name.equals(QUASIQUOTE) -> {
        yield evalQuasiquote(values.get(1)).flatMap(result -> safeEval(result, env));
      }

      case MalSymbol(var name, _) when name.equals(REQUIRE) -> {
        var lib = (MalSymbol) values.get(1);
        if (LIBS.containsKey(lib)) {
          var method = (MalSymbol) values.get(2);
          var function = LIBS.get(lib).get(method.name());
          if (function == null) {
            throw new MalException("function not found: " + method.name());
          }
          env.set(method, function);
          yield done(function);
        }
        throw new MalException("namespace not found: " + lib.name());
      }

      case MalSymbol(var name, _) when name.equals(IMPORT) -> {
        var clazz = (MalSymbol) values.get(1);
        var method = (MalSymbol) values.get(2);
        int numberOfArgs = getNumberOfArguments(values);
        try {
          var methodRef = getMethod(clazz, method, numberOfArgs)
              .orElseThrow(() -> new MalException("method not found " + method.name()));
          var function = function(lambda(methodRef));
          env.set(method, function);
          yield done(function);
        } catch (ClassNotFoundException e) {
          throw new MalException("class not found: " + clazz.name());
        }
      }

      case MalMacro(var lambda, _) -> {
        yield lambda.apply(list(skipFirst(values)))
          .flatMap(next -> safeEval(next, env));
      }

      case MalFunction(var lambda, _) -> {
        yield sequence(skipFirst(values).stream().map(m -> safeEval(m, env)).toList())
          .flatMap(args -> lambda.apply(list(args)));
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

  private static Optional<Method> getMethod(MalSymbol clazz, MalSymbol method, int numberOfArgs) throws ClassNotFoundException {
    var classRef = Class.forName(clazz.name());
    return Stream.of(classRef.getDeclaredMethods())
        .filter(m -> m.getParameterCount() == numberOfArgs)
        .filter(m -> m.getName().equals(method.name()))
        .filter(m -> m.trySetAccessible())
        .findFirst();
  }

  private static int getNumberOfArguments(List<MalNode> values) {
    if (values.size() > 3) {
      var args = (MalNumber) values.get(3);
      return (int) args.value();
    }
    return 0;
  }

  private static Trampoline<MalNode> evalVector(Env env, List<MalNode> values) {
    var later = values.stream().map(m -> safeEval(m, env)).toList();
    return sequence(later).map(MalNode::vector);
  }

  private static Trampoline<MalNode> evalMap(Env env, Map<MalKey, MalNode> map) {
    var later = map.entrySet().stream()
      .map(entry -> safeEval(entry.getValue(), env).map(value -> Map.entry(entry.getKey(), value)))
      .toList();
    return sequence(later)
      .map(list -> list.stream().collect(toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue)))
      .map(MalNode::map);
  }

  private static Trampoline<MalNode> evalQuasiquote(MalNode value) {
    return switch (value) {
      case MalSymbol _ -> done(list(QUOTE, value));
      case MalMap _ -> done(list(QUOTE, value));
      case MalList(var values, _) when values.isEmpty() -> done(value);
      case MalList(var values, _) when values.getFirst().equals(UNQUOTE) -> done(values.get(1));
      case MalList(var values, _) -> recursiveQuasiquote(values);
      case MalVector(var values, _) -> recursiveQuasiquote(values).map(next -> list(symbol("vec"), next));
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
      return zip(recursiveQuasiquote(skipFirst(values)), evalQuasiquote(element),
        (next, eval) -> list(CONS, eval, next));
    });
  }

  private static List<MalNode> skipFirst(List<MalNode> values) {
    return values.stream().skip(1).toList();
  }
}
