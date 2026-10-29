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
import static mal.MalNode.listOf;
import static mal.MalNode.symbol;
import static mal.MalNode.toList;
import static mal.Printer.print;
import static mal.Trampoline.done;
import static mal.Trampoline.more;
import static mal.Trampoline.sequence;
import static mal.Trampoline.traverse;
import static mal.Trampoline.zip;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.pcollections.PVector;
import mal.MalNode.MalConstant;
import mal.MalNode.MalFunction;
import mal.MalNode.MalKey;
import mal.MalNode.MalMacro;
import mal.MalNode.MalMap;
import mal.MalNode.MalNumber;
import mal.MalNode.MalSequence;
import mal.MalNode.MalSymbol;
import mal.MalNode.MalValue;
import mal.MalNode.MalVector;

class Evaluator {

  private static final String DOT = ".";

  private static final Executor executor = Executors.newVirtualThreadPerTaskExecutor();

  @FunctionalInterface
  private interface SpecialForm {
    Trampoline<MalNode> eval(MalSequence form, Env env);
  }

  private static final Map<String, SpecialForm> SPECIAL_FORMS = Map.ofEntries(
    entry("def!", Evaluator::evalDef),
    entry("defmacro!", Evaluator::evalDefMacro),
    entry("let*", Evaluator::evalLet),
    entry("do", Evaluator::evalDo),
    entry("try*", Evaluator::evalTry),
    entry("if", Evaluator::evalIf),
    entry("fn*", Evaluator::evalFn),
    entry("quote", Evaluator::evalQuote),
    entry("quasiquote", Evaluator::evalQuasiquoteForm),
    entry("import", Evaluator::evalImport),
    entry("new", Evaluator::evalNew),
    entry("lazy-seq", Evaluator::evalLazySeq),
    entry("spawn", Evaluator::evalSpawn)
  );

  static Set<String> specialForms() {
    return SPECIAL_FORMS.keySet();
  }

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
    if (name.startsWith(DOT) && name.length() > 1) {
      return done(function(args -> {
        var value = (MalValue<?>) args.get(0);
        return value.call(name.substring(1), toList(args.tail()));
      }));
    }
    var value = env.get(name);
    if (value == null) {
      throw new MalException(name + " not found");
    }
    return done(value);
  }

  private static Trampoline<MalNode> evalList(Env env, MalSequence form) {
    if (form.get(0) instanceof MalSymbol(var name, _)) {
      var special = SPECIAL_FORMS.get(name);
      if (special != null) {
        return special.eval(form, env);
      }
      if (name.startsWith(DOT) && name.length() > 1) {
        return evalMethodCall(name.substring(1), form, env);
      }
    }
    return evalApply(form, env);
  }

  private static Trampoline<MalNode> evalApply(MalSequence form, Env env) {
    return safeEval(form.get(0), env).<MalNode>flatMap(callable -> switch (callable) {
      case MalMacro(var lambda, _) ->
        lambda.apply(toList(form.tail())).flatMap(next -> safeEval(next, env));
      case MalFunction(var lambda, _) ->
        traverse(form.tail(), m -> safeEval(m, env)).flatMap(args -> lambda.apply(list(args)));
      default -> throw new MalException("not a function: " + print(callable, true));
    });
  }

  private static Trampoline<MalNode> evalMethodCall(String method, MalSequence form, Env env) {
    return traverse(form.tail(), m -> safeEval(m, env))
      .flatMap(args -> {
        if (args.isEmpty() || !(args.get(0) instanceof MalValue<?> value)) {
          throw new MalException("expected an object to call method: " + method);
        }
        return value.call(method, list(args.minus(0)));
      });
  }

  private static Trampoline<MalNode> evalDef(MalSequence form, Env env) {
    arity(form, 2, 2);
    var key = symbolAt(form, 1);
    return safeEval(form.get(2), env).map(value -> {
      env.set(key, value);
      return value;
    });
  }

  private static Trampoline<MalNode> evalDefMacro(MalSequence form, Env env) {
    arity(form, 2, 2);
    var key = symbolAt(form, 1);
    return safeEval(form.get(2), env).<MalNode>map(value -> {
      if (!(value instanceof MalFunction function)) {
        throw new MalException("defmacro!: expected a function");
      }
      var macro = function.toMacro();
      env.set(key, macro);
      return macro;
    });
  }

  private static Trampoline<MalNode> evalLet(MalSequence form, Env env) {
    arity(form, 2, 2);
    var bindings = sequenceAt(form, 1);
    if (bindings.size() % 2 != 0) {
      throw new MalException("let*: odd number of binding forms");
    }
    var newEnv = new Env(env);
    List<Trampoline<MalNode>> later = new ArrayList<>();
    for (var iterator = bindings.iterator(); iterator.hasNext();) {
      var next = iterator.next();
      if (!(next instanceof MalSymbol key)) {
        throw new MalException("let*: binding name must be a symbol");
      }
      later.add(safeEval(iterator.next(), newEnv).map(value -> {
        newEnv.set(key, value);
        return value;
      }));
    }
    return sequence(later).andThen(safeEval(form.get(2), newEnv));
  }

  private static Trampoline<MalNode> evalDo(MalSequence form, Env env) {
    List<MalNode> forms = new ArrayList<>();
    for (var node : form.tail()) {
      forms.add(node);
    }
    if (forms.isEmpty()) {
      return done(NIL);
    }
    var init = forms.subList(0, forms.size() - 1);
    var last = forms.get(forms.size() - 1);
    return traverse(init, m -> safeEval(m, env)).andThen(safeEval(last, env));
  }

  private static Trampoline<MalNode> evalTry(MalSequence form, Env env) {
    arity(form, 1, 2);
    var body = form.get(1);
    if (form.size() < 3) {
      return more(() -> safeEval(body, env));
    }
    // the catch* form is checked before running the body, not only when something is thrown
    var catchForm = sequenceAt(form, 2);
    if (catchForm.size() != 3 || !catchForm.get(0).equals(symbol("catch*"))) {
      throw new MalException("try*: expected (catch* symbol form)");
    }
    var binding = symbolAt(catchForm, 1);
    return more(() -> safeEval(body, env)).recover(e -> {
      MalNode thrown = e instanceof MalException me ? me.getNode() : error(e);
      var newEnv = new Env(env, new HashMap<>(Map.of(binding.name(), thrown)));
      return safeEval(catchForm.get(2), newEnv);
    });
  }

  private static Trampoline<MalNode> evalIf(MalSequence form, Env env) {
    arity(form, 2, 3);
    return safeEval(form.get(1), env).flatMap(result -> {
      if (isTruthy(result)) {
        return safeEval(form.get(2), env);
      }
      return form.size() > 3 ? safeEval(form.get(3), env) : done(NIL);
    });
  }

  private static Trampoline<MalNode> evalFn(MalSequence form, Env env) {
    arity(form, 2, 2);
    var params = sequenceAt(form, 1);
    var body = form.get(2);
    return done(function(args -> safeEval(body, new Env(env, params, args))));
  }

  private static Trampoline<MalNode> evalQuote(MalSequence form, Env env) {
    arity(form, 1, 1);
    return done(form.get(1));
  }

  private static Trampoline<MalNode> evalQuasiquoteForm(MalSequence form, Env env) {
    arity(form, 1, 1);
    return evalQuasiquote(form.get(1)).flatMap(result -> safeEval(result, env));
  }

  private static Trampoline<MalNode> evalImport(MalSequence form, Env env) {
    arity(form, 2, 3);
    var clazz = symbolAt(form, 1);
    var method = symbolAt(form, 2);
    int numberOfArgs = getNumberOfArguments(form);
    var function = function(Interop.method(clazz.name(), method.name(), numberOfArgs));
    env.set(methodName(clazz.name(), method.name(), numberOfArgs), function);
    return done(function);
  }

  private static Trampoline<MalNode> evalNew(MalSequence form, Env env) {
    var clazz = symbolAt(form, 1);
    return traverse(form.tail().tail(), m -> safeEval(m, env))
      .flatMap(args -> {
        var lambda = Interop.constructor(clazz.name(), args.size());
        return lambda.apply(list(args));
      });
  }

  private static Trampoline<MalNode> evalLazySeq(MalSequence form, Env env) {
    arity(form, 1, 1);
    var body = form.get(1);
    if (!(body instanceof MalSequence)) {
      throw new MalException("invalid lazy-seq");
    }
    return done(lazy(() -> eval(body, env)));
  }

  private static Trampoline<MalNode> evalSpawn(MalSequence form, Env env) {
    arity(form, 1, 1);
    var body = form.get(1);
    var newEnv = new Env(env);
    return done(fork(() -> ((MalFunction) eval(body, newEnv)).run(EMPTY_LIST), executor));
  }

  static boolean isTruthy(MalNode node) {
    return !(node instanceof MalConstant(var name)
      && (name.equals("nil") || name.equals("false")));
  }

  private static void arity(MalSequence form, int min, int max) {
    int n = form.size() - 1;
    if (n < min || n > max) {
      var name = form.get(0) instanceof MalSymbol(var s, _) ? s : "form";
      var expected = min == max ? String.valueOf(min) : min + ".." + max;
      throw new MalException(name + ": expected " + expected + " argument(s) but got " + n);
    }
  }

  private static MalSymbol symbolAt(MalSequence form, int index) {
    if (form.get(index) instanceof MalSymbol symbol) {
      return symbol;
    }
    throw new MalException("expected a symbol at position " + index);
  }

  private static MalSequence sequenceAt(MalSequence form, int index) {
    if (form.get(index) instanceof MalSequence seq) {
      return seq;
    }
    throw new MalException("expected a list or vector at position " + index);
  }

  private static MalSymbol methodName(String clazz, String method, int numberOfArgs) {
    int lastDotIndex = clazz.lastIndexOf(DOT);
    return numberOfArgs > 0 ?
        symbol(clazz.substring(lastDotIndex + 1) + DOT + method + "/" + numberOfArgs) :
          symbol(clazz.substring(lastDotIndex + 1) + DOT + method);
  }

  private static int getNumberOfArguments(MalSequence values) {
    if (values.size() > 3) {
      var args = (MalNumber) values.get(3);
      return args.asInt();
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
      case MalSymbol _ -> done(listOf(QUOTE, value));
      case MalMap _ -> done(listOf(QUOTE, value));
      case MalVector vec -> recursiveQuasiquote(vec).map(next -> listOf(VEC, next));
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
      if (element instanceof MalSequence seq && !seq.isEmpty() && seq.get(0).equals(SPLICE_UNQUOTE)) {
        return recursiveQuasiquote(values.tail())
          .map(result -> listOf(CONCAT, seq.get(1), result));
      }
      return zip(recursiveQuasiquote(values.tail()), evalQuasiquote(element),
        (next, eval) -> listOf(CONS, eval, next));
    });
  }
}