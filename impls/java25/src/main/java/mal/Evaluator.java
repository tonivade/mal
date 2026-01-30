/*
 * Copyright (c) 2024-2025, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package mal;

import static java.util.Map.entry;
import static java.util.stream.Collectors.toUnmodifiableMap;
import static mal.MalNode.CONCAT;
import static mal.MalNode.CONS;
import static mal.MalNode.EMPTY_LIST;
import static mal.MalNode.FALSE;
import static mal.MalNode.NIL;
import static mal.MalNode.QUOTE;
import static mal.MalNode.SPLICE_UNQUOTE;
import static mal.MalNode.UNQUOTE;
import static mal.MalNode.VEC;
import static mal.MalNode.error;
import static mal.MalNode.fork;
import static mal.MalNode.function;
import static mal.MalNode.lazy;
import static mal.MalNode.list;
import static mal.MalNode.symbol;
import static mal.Printer.print;
import static mal.Trampoline.done;
import static mal.Trampoline.more;
import static mal.Trampoline.sequence;
import static mal.Trampoline.traverse;
import static mal.Trampoline.zip;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.pcollections.PVector;

import mal.MalNode.MalFunction;
import mal.MalNode.MalKey;
import mal.MalNode.MalMacro;
import mal.MalNode.MalMap;
import mal.MalNode.MalNumber;
import mal.MalNode.MalSequence;
import mal.MalNode.MalSymbol;
import mal.MalNode.MalVector;
import mal.MalNode.MalWrapper;

class Evaluator {

  private static final String NEW = "new";
  private static final String SPAWN = "spawn";
  private static final String DO = "do";
  private static final String LAZY_SEQ = "lazy-seq";
  private static final String IMPORT = "import";
  private static final String QUASIQUOTE = "quasiquote";
  private static final String QUOTE_ = "quote";
  private static final String FN = "fn*";
  private static final String IF = "if";
  private static final String TRY = "try*";
  private static final String LET = "let*";
  private static final String DEFMACRO = "defmacro!";
  private static final String DEF = "def!";

  private static final Executor executor = Executors.newVirtualThreadPerTaskExecutor();

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
        case MalVector(var values, _) when !values.isEmpty() -> evalVector(env, values);
        case MalMap(var map, _) when !map.isEmpty() -> evalMap(env, map);
        case MalSequence seq when !seq.isEmpty() -> evalList(env, seq);
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

  private static Trampoline<MalNode> evalList(Env env, MalSequence values) {
    return switch (values.get(0)) {

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

      case MalSymbol(var name, _) when name.equals(DO) -> {
        yield traverse(values.tail(), m -> safeEval(m, env))
          .map(PVector::getLast);
      }

      case MalSymbol(var name, _) when name.equals(TRY) -> {
        var body = values.get(1);
        try {
          yield done(eval(body, env));
        } catch (RuntimeException e) {
          if (values.size() < 3) {
            throw new MalException(e.getMessage());
          }
          var catch_ = (MalSequence) values.get(2);
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

      case MalSymbol(var name, _) when name.equals(IMPORT) -> {
        var clazz = (MalSymbol) values.get(1);
        var method = (MalSymbol) values.get(2);
        int numberOfArgs = getNumberOfArguments(values);
        var function = function(Interop.method(clazz.name(), method.name(), numberOfArgs));
        env.set(numberOfArgs > 0 ? symbol(method.name() + "/" + numberOfArgs) : method, function);
        yield done(function);
      }

      case MalSymbol(var name, _) when name.equals(NEW) -> {
        var clazz = (MalSymbol) values.get(1);
        var args = values.tail().tail();
        var lambda = Interop.constructorNonCached(clazz.name(), args.size());
        yield lambda.apply(list(args));
      }

      case MalSymbol(var name, _) when name.equals(LAZY_SEQ) -> {
        var body = values.get(1);
        if (!(body instanceof MalSequence)) {
          throw new MalException("invalid lazy-seq");
        }
        yield done(lazy(() -> eval(body, env)));
      }

      case MalSymbol(var name, _) when name.equals(SPAWN) -> {
        var body = values.get(1);
        var newEnv = new Env(env);
        yield done(fork(CompletableFuture.supplyAsync(() -> ((MalFunction) eval(body, newEnv)).run(EMPTY_LIST), executor)));
      }

      case MalMacro(var lambda, _) -> {
        yield lambda.apply(list(values.tail()))
          .flatMap(next -> safeEval(next, env));
      }

      case MalFunction(var lambda, _) -> {
        yield traverse(values.tail(), m -> safeEval(m, env))
          .flatMap(args -> lambda.apply(list(args)));
      }

      case MalWrapper wrapper -> {
        var name = (MalSymbol) values.get(1);
        var args = values.tail().tail();
        yield wrapper.call(name.name(), args);
      }

      default -> {
        yield safeEval(values.get(0), env)
          .<MalNode>map(callable -> values.tail().cons(callable))
          .flatMap(node -> safeEval(node, env));
      }
    };
  }

  private static int getNumberOfArguments(MalSequence values) {
    if (values.size() > 3) {
      var args = (MalNumber) values.get(3);
      return (int) args.value();
    }
    return 0;
  }

  private static Trampoline<MalNode> evalVector(Env env, PVector<MalNode> values) {
    return traverse(values, m -> safeEval(m, env)).map(MalNode::vector);
  }

  private static Trampoline<MalNode> evalMap(Env env, Map<MalKey, MalNode> map) {
    var result =
      traverse(map.entrySet(), entry -> safeEval(entry.getValue(), env).<Map.Entry<MalKey, MalNode>>map(value -> entry(entry.getKey(), value)));
    return result
      .map(list -> list.stream().collect(toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue)))
      .map(MalNode::map);
  }

  private static Trampoline<MalNode> evalQuasiquote(MalNode value) {
    return switch (value) {
      case MalSymbol _ -> done(list(QUOTE, value));
      case MalMap _ -> done(list(QUOTE, value));
      case MalVector vec -> recursiveQuasiquote(vec).map(next -> list(VEC, next));
      case MalSequence seq when seq.isEmpty() -> done(value);
      case MalSequence seq when seq.get(0).equals(UNQUOTE) -> done(seq.get(1));
      case MalSequence seq -> recursiveQuasiquote(seq);
      default -> done(value);
    };
  }

  private static Trampoline<MalNode> recursiveQuasiquote(MalSequence values) {
    if (values.isEmpty()) {
      return done(EMPTY_LIST);
    }
    return more(() -> {
      var element = values.get(0);
      if (element instanceof MalSequence seq && seq.get(0).equals(SPLICE_UNQUOTE)) {
        return recursiveQuasiquote(values.tail())
          .map(result -> list(CONCAT, seq.get(1), result));
      }
      return zip(recursiveQuasiquote(values.tail()), evalQuasiquote(element),
        (next, eval) -> list(CONS, eval, next));
    });
  }
}
