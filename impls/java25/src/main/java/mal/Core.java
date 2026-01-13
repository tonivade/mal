/*
 * Copyright (c) 2024-2025, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package mal;

import static java.util.Map.entry;
import static mal.Interop.toMal;
import static mal.MalNode.EMPTY_LIST;
import static mal.MalNode.FALSE;
import static mal.MalNode.NIL;
import static mal.MalNode.TRUE;
import static mal.MalNode.function;
import static mal.MalNode.lambda;
import static mal.MalNode.list;
import static mal.MalNode.number;
import static mal.MalNode.string;
import static mal.MalNode.vector;
import static mal.Printer.print;
import static mal.Reader.read;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.stream.Stream;

import org.codehaus.commons.compiler.CompileException;
import org.codehaus.janino.ExpressionEvaluator;

import mal.MalNode.MalAtom;
import mal.MalNode.MalFunction;
import mal.MalNode.MalKey;
import mal.MalNode.MalKeyword;
import mal.MalNode.MalList;
import mal.MalNode.MalMacro;
import mal.MalNode.MalMap;
import mal.MalNode.MalNumber;
import mal.MalNode.MalSequence;
import mal.MalNode.MalString;
import mal.MalNode.MalSymbol;
import mal.MalNode.MalVector;
import mal.MalNode.MalWithLambda;

interface Core {

  static MalNode prn(MalList args) {
    var result = new StringBuilder();
    for (var m : args) {
      result.append(print(m, true)).append(" ");
    }
    IO.println(result.toString().trim());
    return NIL;
  }

  static MalNode println(MalList args) {
    var result = new StringBuilder();
    for (var m : args) {
      result.append(print(m, false)).append(" ");
    }
    IO.println(result.toString().trim());
    return NIL;
  }

  static MalNode isList(MalList args) {
    return args.get(0) instanceof MalList ? TRUE : FALSE;
  }

  static MalNode isEmpty(MalList args) {
    var list = (MalSequence) args.get(0);
    return list.isEmpty() ? TRUE : FALSE;
  }

  static MalNode count(MalList args) {
    var first = args.get(0);
    if (first == NIL) {
      return number(0);
    }
    return number(((MalSequence) first).size());
  }

  static MalNode eq(MalList args) {
    return MalNode.equals(args.get(0), args.get(1)) ? TRUE : FALSE;
  }

  static MalNode gt(MalList args) {
    var first = (MalNumber) args.get(0);
    var second = (MalNumber) args.get(1);
    return first.gt(second) ? TRUE : FALSE;
  }

  static MalNode gte(MalList args) {
    var first = (MalNumber) args.get(0);
    var second = (MalNumber) args.get(1);
    return first.gte(second) ? TRUE : FALSE;
  }

  static MalNode lt(MalList args) {
    var first = (MalNumber) args.get(0);
    var second = (MalNumber) args.get(1);
    return first.lt(second) ? TRUE : FALSE;
  }

  static MalNode lte(MalList args) {
    var first = (MalNumber) args.get(0);
    var second = (MalNumber) args.get(1);
    return first.lte(second) ? TRUE : FALSE;
  }

  static MalNode sum(MalList args) {
    var arg1 = (MalNumber) args.get(0);
    var arg2 = (MalNumber) args.get(1);
    return arg1.sum(arg2);
  }

  static MalNode sub(MalList args) {
    var arg1 = (MalNumber) args.get(0);
    var arg2 = (MalNumber) args.get(1);
    return arg1.sub(arg2);
  }

  static MalNode mul(MalList args) {
    var arg1 = (MalNumber) args.get(0);
    var arg2 = (MalNumber) args.get(1);
    return arg1.mul(arg2);
  }

  static MalNode div(MalList args) {
    var arg1 = (MalNumber) args.get(0);
    var arg2 = (MalNumber) args.get(1);
    return arg1.div(arg2);
  }

  static MalNode mod(MalList args) {
    var arg1 = (MalNumber) args.get(0);
    var arg2 = (MalNumber) args.get(1);
    return arg1.mod(arg2);
  }

  static MalNode prStr(MalList args) {
    var result = new StringBuilder();
    for (var m : args) {
      result.append(print(m, true)).append(" ");
    }
    return string(result.toString().trim());
  }

  static MalNode str(MalList args) {
    var result = new StringBuilder();
    for (var m : args) {
      result.append(print(m, false));
    }
    return string(result.toString());
  }

  static MalNode readString(MalList args) {
    var string = (MalString) args.get(0);
    return read(string.value());
  }

  static MalNode slurp(MalList args) {
    try {
      var fileName = (MalString) args.get(0);
      var content = Files.readString(Paths.get(fileName.value()));
      return string(content);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  static MalAtom atom(MalList args) {
    return MalNode.atom(args.get(0));
  }

  static MalNode isAtom(MalList args) {
    return args.get(0) instanceof MalAtom ? TRUE : FALSE;
  }

  static MalNode deref(MalList args) {
    var atom = (MalAtom) args.get(0);
    return atom.getValue();
  }

  static MalNode reset(MalList args) {
    var atom = (MalAtom) args.get(0);
    var newValue = args.get(1);
    atom.setValue(newValue);
    return newValue;
  }

  static Trampoline<MalNode> swap(MalList args) {
    var atom = (MalAtom) args.get(0);
    var function = (MalWithLambda) args.get(1);
    var newArgs = args.values().dropFirst().dropFirst().prepend(atom.getValue());
    return function.lambda().apply(list(newArgs)).map(newValue -> {
      atom.setValue(newValue);
      return newValue;
    });
  }

  static MalNode cons(MalList args) {
    var item = args.get(0);
    var list = (MalSequence) args.get(1);
    return MalNode.cons(item, list);
  }

  static MalNode concat(MalList args) {
    MalSequence concat = MalNode.EMPTY_LIST;
    for (var current : args) {
      concat = MalNode.concat(concat, (MalSequence) current);
    }
    return concat;
  }

  static MalNode vec(MalList args) {
    var list = (MalSequence) args.get(0);
    return vector(list);
  }

  static MalNode isMacro(MalList args) {
    return args.get(0) instanceof MalMacro ? TRUE : FALSE;
  }

  static MalNode nth(MalList args) {
    var list = (MalSequence) args.get(0);
    var index = (MalNumber) args.get(1);
    if (index.value() < 0 || index.value() >= list.size()) {
      throw new MalException("index out of bounds: " + list.size());
    }
    return list.get(index.asInt());
  }

  static MalNode first(MalList args) {
    if (args.get(0).equals(NIL)) {
      return NIL;
    }
    var list = (MalSequence) args.get(0);
    if (list.isEmpty()) {
      return NIL;
    }
    return list.get(0);
  }

  static MalNode rest(MalList args) {
    if (args.get(0).equals(NIL)) {
      return EMPTY_LIST;
    }
    var list = (MalSequence) args.get(0);
    if (list.isEmpty()) {
      return EMPTY_LIST;
    }
    return list.tail();
  }

  static MalNode raise(MalList args) {
    throw new MalException(args.get(0));
  }

  static Trampoline<MalNode> apply(MalList args) {
    var function = (MalWithLambda) args.get(0);
    var arguments = list(args.values().dropFirst().stream().flatMap(m -> switch (m) {
      case MalList(var values, _) -> values.stream();
      case MalVector(var values, _) -> values.stream();
      default -> Stream.of(m);
    }).toList());
    return function.lambda().apply(arguments);
  }

  static MalNode map(MalList args) {
    var function = (MalWithLambda) args.get(0);
    var elements = (MalSequence) args.get(1);
    return MalNode.mapped(function.lambda(), elements);
  }

  static MalNode isNil(MalList args) {
    return args.get(0).equals(NIL) ? TRUE : FALSE;
  }

  static MalNode isTrue(MalList args) {
    return args.get(0).equals(TRUE) ? TRUE : FALSE;
  }

  static MalNode isFalse(MalList args) {
    return args.get(0).equals(FALSE) ? TRUE : FALSE;
  }

  static MalNode isSymbol(MalList args) {
    return args.get(0) instanceof MalSymbol ? TRUE : FALSE;
  }

  static MalNode isKeyword(MalList args) {
    return args.get(0) instanceof MalKeyword ? TRUE : FALSE;
  }

  static MalNode isVector(MalList args) {
    return args.get(0) instanceof MalVector ? TRUE : FALSE;
  }

  static MalNode isSequential(MalList args) {
    return args.get(0) instanceof MalSequence ? TRUE : FALSE;
  }

  static MalNode isMap(MalList args) {
    return args.get(0) instanceof MalMap ? TRUE : FALSE;
  }

  static MalNode get(MalList args) {
    if (args.get(0).equals(NIL)) {
      return NIL;
    }
    var map = (MalMap) args.get(0);
    var key = (MalKey) args.get(1);
    return map.get(key);
  }

  static MalNode contains(MalList args) {
    var map = (MalMap) args.get(0);
    var key = (MalKey) args.get(1);
    return map.contains(key) ? TRUE : FALSE;
  }

  static MalNode keys(MalList args) {
    var map = (MalMap) args.get(0);
    return list(map.keys());
  }

  static MalNode vals(MalList args) {
    var map = (MalMap) args.get(0);
    return list(map.values());
  }

  static MalNode symbol(MalList args) {
    var name = (MalString) args.get(0);
    return MalNode.symbol(name.value());
  }

  static MalNode keyword(MalList args) {
    var param = args.get(0);
    if (param instanceof MalKeyword keyword) {
      return keyword;
    }
    var name = (MalString) param;
    return MalNode.keyword(name.value());
  }

  static MalNode assoc(MalList args) {
    var map = (MalMap) args.get(0);
    var entries = MalNode.map(args.values().dropFirst());
    return map.addAll(entries.map());
  }

  static MalNode dissoc(MalList args) {
    var map = (MalMap) args.get(0);
    var keys = args.values().dropFirst().stream().map(MalKey.class::cast).toList();
    return map.removeAll(keys);
  }

  static MalNode readLine(MalList args) {
    var prompt = (MalString) args.get(0);
    var line = Readline.readline(prompt.value());
    return line != null ? string(line) : NIL;
  }

  static MalNumber timeMs() {
    return number(System.currentTimeMillis());
  }

  static MalNode meta(MalList args) {
    var meta = args.get(0).meta();
    return meta != null ? meta : NIL;
  }

  static MalNode withMeta(MalList args) {
    return args.get(0).withMeta(args.get(1));
  }

  static MalNode isFunction(MalList args) {
    return args.get(0) instanceof MalFunction ? TRUE : FALSE;
  }

  static MalNode isString(MalList args) {
    return args.get(0) instanceof MalString ? TRUE : FALSE;
  }

  static MalNode isNumber(MalList args) {
    return args.get(0) instanceof MalNumber ? TRUE : FALSE;
  }

  static MalNode seq(MalList args) {
    var arg = args.get(0);
    return switch (arg) {
      case MalSequence seq when seq.isEmpty() -> NIL;
      case MalVector(var values, _) -> list(values);
      case MalString(var value, _) when value.isEmpty() -> NIL;
      case MalString(var value, _) -> asList(value);
      default -> arg;
    };
  }

  static MalNode conj(MalList args) {
    return switch (args.get(0)) {
      case MalList(var values, _) -> {
        var newValues = values.toBuilder().prependAll(args.tail()).build();
        yield list(newValues);
      }
      case MalVector(var values, _) -> {
        var newValues = values.toBuilder().appendAll(args.tail()).build();
        yield vector(newValues);
      }
      default -> throw new MalException("invalid definition");
    };
  }

  static MalNode eval(MalList args) {
    try {
      if (args.get(0) instanceof MalString(var value, _)) {
        var evaluator = new ExpressionEvaluator();
        evaluator.setReturnType(Object.class);
        evaluator.cook(value);
        return toMal(evaluator.evaluate(new Object[] {}));
      }
      throw new MalException("invalid definition");
    } catch (CompileException e) {
      throw new MalException("eval error", e);
    } catch (InvocationTargetException e) {
      throw new MalException("eval error", e);
    } catch (RuntimeException e) {
      throw new MalException("eval error", e);
    }
  }

  Map<String, MalNode> NS = Map.ofEntries(
    entry("prn", function(lambda(Core::prn))),
    entry("println", function(lambda(Core::println))),
    entry("list", function(lambda(MalNode::list))),
    entry("list?", function(lambda(Core::isList))),
    entry("empty?", function(lambda(Core::isEmpty))),
    entry("count", function(lambda(Core::count))),
    entry("+", function(lambda(Core::sum))),
    entry("-", function(lambda(Core::sub))),
    entry("*", function(lambda(Core::mul))),
    entry("/", function(lambda(Core::div))),
    entry("%", function(lambda(Core::mod))),
    entry("=", function(lambda(Core::eq))),
    entry(">", function(lambda(Core::gt))),
    entry(">=", function(lambda(Core::gte))),
    entry("<", function(lambda(Core::lt))),
    entry("<=", function(lambda(Core::lte))),
    entry("pr-str", function(lambda(Core::prStr))),
    entry("str", function(lambda(Core::str))),
    entry("read-string", function(lambda(Core::readString))),
    entry("slurp", function(lambda(Core::slurp))),
    entry("atom", function(lambda(Core::atom))),
    entry("atom?", function(lambda(Core::isAtom))),
    entry("deref", function(lambda(Core::deref))),
    entry("reset!", function(lambda(Core::reset))),
    entry("swap!", function(Core::swap)),
    entry("cons", function(lambda(Core::cons))),
    entry("concat", function(lambda(Core::concat))),
    entry("vec", function(lambda(Core::vec))),
    entry("macro?", function(lambda(Core::isMacro))),
    entry("nth", function(lambda(Core::nth))),
    entry("first", function(lambda(Core::first))),
    entry("rest", function(lambda(Core::rest))),
    entry("throw", function(lambda(Core::raise))),
    entry("apply", function(Core::apply)),
    entry("map", function(lambda(Core::map))),
    entry("nil?", function(lambda(Core::isNil))),
    entry("true?", function(lambda(Core::isTrue))),
    entry("false?", function(lambda(Core::isFalse))),
    entry("symbol?", function(lambda(Core::isSymbol))),
    entry("keyword?", function(lambda(Core::isKeyword))),
    entry("vector?", function(lambda(Core::isVector))),
    entry("sequential?", function(lambda(Core::isSequential))),
    entry("map?", function(lambda(Core::isMap))),
    entry("contains?", function(lambda(Core::contains))),
    entry("symbol", function(lambda(Core::symbol))),
    entry("keyword", function(lambda(Core::keyword))),
    entry("vector", function(lambda(MalNode::vector))),
    entry("get", function(lambda(Core::get))),
    entry("keys", function(lambda(Core::keys))),
    entry("vals", function(lambda(Core::vals))),
    entry("hash-map", function(lambda(MalNode::map))),
    entry("assoc", function(lambda(Core::assoc))),
    entry("dissoc", function(lambda(Core::dissoc))),
    entry("readline", function(lambda(Core::readLine))),
    entry("time-ms", function(lambda(_ -> timeMs()))),
    entry("meta", function(lambda(Core::meta))),
    entry("with-meta", function(lambda(Core::withMeta))),
    entry("fn?", function(lambda(Core::isFunction))),
    entry("string?", function(lambda(Core::isString))),
    entry("number?", function(lambda(Core::isNumber))),
    entry("seq", function(lambda(Core::seq))),
    entry("conj", function(lambda(Core::conj))),
    entry("java-eval", function(lambda(Core::eval)))
  );

  private static MalList asList(String string) {
    return list(string.chars().mapToObj(Character::toString).map(MalNode::string).toList());
  }
}
