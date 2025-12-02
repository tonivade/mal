/*
 * Copyright (c) 2024-2025, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package mal;

import static java.util.Map.entry;
import static java.util.stream.Collectors.joining;
import static mal.Interop.toMal;
import static mal.MalNode.FALSE;
import static mal.MalNode.NIL;
import static mal.MalNode.TRUE;
import static mal.MalNode.atom;
import static mal.MalNode.function;
import static mal.MalNode.keyword;
import static mal.MalNode.lambda;
import static mal.MalNode.list;
import static mal.MalNode.map;
import static mal.MalNode.number;
import static mal.MalNode.string;
import static mal.MalNode.symbol;
import static mal.MalNode.vector;
import static mal.Printer.print;
import static mal.Reader.read;
import static mal.Trampoline.sequence;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Map;
import java.util.stream.Stream;

import org.codehaus.commons.compiler.CompileException;
import org.codehaus.janino.ExpressionEvaluator;
import mal.MalNode.MalAtom;
import mal.MalNode.MalFunction;
import mal.MalNode.MalKey;
import mal.MalNode.MalKeyword;
import mal.MalNode.MalLambda;
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

  MalLambda PRN = lambda(args -> {
    var result = args.stream().map(m -> print(m, true)).collect(joining(" "));
    IO.println(result);
    return NIL;
  });

  MalLambda PRINTLN = lambda(args -> {
    var result = args.stream().map(m -> print(m, false)).collect(joining(" "));
    IO.println(result);
    return NIL;
  });

  MalLambda LIST = lambda(args -> list(args.values()));

  MalLambda LIST_Q = lambda(args -> args.get(0) instanceof MalList ? TRUE : FALSE);

  MalLambda EMPTY_Q = lambda(args -> {
    var list = (MalSequence) args.get(0);
    return list.isEmpty() ? TRUE : FALSE;
  });

  MalLambda COUNT = lambda(args -> {
    var first = args.get(0);
    if (first == NIL) {
      return number(0);
    }
    return number(((MalSequence) first).size());
  });

  MalLambda EQ = lambda(args -> MalNode.equals(args.get(0), args.get(1)) ? TRUE : FALSE);

  MalLambda GT = lambda(args -> {
    var first = (MalNumber) args.get(0);
    var second = (MalNumber) args.get(1);
    return first.gt(second) ? TRUE : FALSE;
  });

  MalLambda GTE = lambda(args -> {
    var first = (MalNumber) args.get(0);
    var second = (MalNumber) args.get(1);
    return first.gte(second) ? TRUE : FALSE;
  });

  MalLambda LT = lambda(args -> {
    var first = (MalNumber) args.get(0);
    var second = (MalNumber) args.get(1);
    return first.lt(second) ? TRUE : FALSE;
  });

  MalLambda LTE = lambda(args -> {
    var first = (MalNumber) args.get(0);
    var second = (MalNumber) args.get(1);
    return first.lte(second) ? TRUE : FALSE;
  });

  MalLambda SUM = lambda(args -> {
    var arg1 = (MalNumber) args.get(0);
    var arg2 = (MalNumber) args.get(1);
    return arg1.sum(arg2);
  });

  MalLambda SUB = lambda(args -> {
    var arg1 = (MalNumber) args.get(0);
    var arg2 = (MalNumber) args.get(1);
    return arg1.sub(arg2);
  });

  MalLambda MUL = lambda(args -> {
    var arg1 = (MalNumber) args.get(0);
    var arg2 = (MalNumber) args.get(1);
    return arg1.mul(arg2);
  });

  MalLambda DIV = lambda(args -> {
    var arg1 = (MalNumber) args.get(0);
    var arg2 = (MalNumber) args.get(1);
    return arg1.div(arg2);
  });

  MalLambda MOD = lambda(args -> {
    var arg1 = (MalNumber) args.get(0);
    var arg2 = (MalNumber) args.get(1);
    return arg1.mod(arg2);
  });

  MalLambda PR_STR = lambda(args -> {
    var result = args.stream().map(m -> print(m, true)).collect(joining(" "));
    return string(result);
  });

  MalLambda STR = lambda(args -> {
    var result = args.stream().map(m -> print(m, false)).collect(joining(""));
    return string(result);
  });

  MalLambda READ_STRING = lambda(args -> {
    var string = (MalString) args.get(0);
    return read(string.value());
  });

  MalLambda SLURP = lambda(args -> {
    try {
      var fileName = (MalString) args.get(0);
      var content = Files.readString(Paths.get(fileName.value()));
      return string(content);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  });

  MalLambda ATOM = lambda(args -> atom(args.get(0)));

  MalLambda ATOM_Q = lambda(args -> args.get(0) instanceof MalAtom ? TRUE : FALSE);

  MalLambda DEREF = lambda(args -> {
    var atom = (MalAtom) args.get(0);
    return atom.getValue();
  });

  MalLambda RESET = lambda(args -> {
    var atom = (MalAtom) args.get(0);
    var newValue = args.get(1);
    atom.setValue(newValue);
    return newValue;
  });

  MalLambda SWAP = args -> {
    var atom = (MalAtom) args.get(0);
    var function = (MalWithLambda) args.get(1);
    var newArgs = new ArrayList<MalNode>();
    newArgs.add(atom.getValue());
    newArgs.addAll(args.values().stream().skip(2).toList());
    return function.lambda().apply(list(newArgs)).map(newValue -> {
      atom.setValue(newValue);
      return newValue;
    });
  };

  MalLambda CONS = lambda(args -> {
    var item = args.get(0);
    var list = (MalSequence) args.get(1);
    var result = new ArrayList<MalNode>();
    result.add(item);
    result.addAll(list.values());
    return list(result);
  });

  MalLambda CONCAT = lambda(args -> {
    var result = args.stream().map(MalSequence.class::cast).flatMap(MalSequence::stream).toList();
    return list(result);
  });

  MalLambda VEC = lambda(args -> {
    var list = (MalSequence) args.get(0);
    return vector(list.stream());
  });

  MalLambda MACRO_Q = lambda(args -> args.get(0) instanceof MalMacro ? TRUE : FALSE);

  MalLambda NTH = lambda(args -> {
    var list = (MalSequence) args.get(0);
    var index = (MalNumber) args.get(1);
    if (index.value() < 0 || index.value() >= list.size()) {
      throw new MalException("index out of bounds: " + list.size());
    }
    return list.get(index.asInt());
  });

  MalLambda FIRST = lambda(args -> {
    if (args.get(0).equals(NIL)) {
      return NIL;
    }
    var list = (MalSequence) args.get(0);
    if (list.isEmpty()) {
      return NIL;
    }
    return list.get(0);
  });

  MalLambda REST = lambda(args -> {
    if (args.get(0).equals(NIL)) {
      return list();
    }
    var list = (MalSequence) args.get(0);
    if (list.isEmpty()) {
      return list();
    }
    return list(list.stream().skip(1).toList());
  });

  MalLambda THROW = lambda(args -> {
    throw new MalException(args.get(0));
  });

  MalLambda APPLY = args -> {
    var function = (MalWithLambda) args.get(0);
    var arguments = list(args.stream().skip(1).flatMap(m -> switch (m) {
      case MalList(var values, _) -> values.stream();
      case MalVector(var values, _) -> values.stream();
      default -> Stream.of(m);
    }).toList());
    return function.lambda().apply(arguments);
  };

  MalLambda MAP = args -> {
    var function = (MalWithLambda) args.get(0);
    var elements = (MalSequence) args.get(1);
    var result = elements.stream().map(m -> function.lambda().apply(list(m))).toList();
    return sequence(result).map(MalNode::list);
  };

  MalLambda NIL_Q = lambda(args -> args.get(0).equals(NIL) ? TRUE : FALSE);

  MalLambda TRUE_Q = lambda(args -> args.get(0).equals(TRUE) ? TRUE : FALSE);

  MalLambda FALSE_Q = lambda(args -> args.get(0).equals(FALSE) ? TRUE : FALSE);

  MalLambda SYMBOL_Q = lambda(args -> args.get(0) instanceof MalSymbol ? TRUE : FALSE);

  MalLambda KEYWORD_Q = lambda(args -> args.get(0) instanceof MalKeyword ? TRUE : FALSE);

  MalLambda VECTOR_Q = lambda(args -> args.get(0) instanceof MalVector ? TRUE : FALSE);

  MalLambda SEQUENTIAL_Q = lambda(args -> args.get(0) instanceof MalSequence ? TRUE : FALSE);

  MalLambda MAP_Q = lambda(args -> args.get(0) instanceof MalMap ? TRUE : FALSE);

  MalLambda GET = lambda(args -> {
    if (args.get(0).equals(NIL)) {
      return NIL;
    }
    var map = (MalMap) args.get(0);
    var key = (MalKey) args.get(1);
    return map.get(key);
  });

  MalLambda CONTAINS_Q = lambda(args -> {
    var map = (MalMap) args.get(0);
    var key = (MalKey) args.get(1);
    return map.contains(key) ? TRUE : FALSE;
  });

  MalLambda KEYS = lambda(args -> {
    var map = (MalMap) args.get(0);
    return list(map.keys());
  });

  MalLambda VALS = lambda(args -> {
    var map = (MalMap) args.get(0);
    return list(map.values());
  });

  MalLambda SYMBOL = lambda(args -> {
    var name = (MalString) args.get(0);
    return symbol(name.value());
  });

  MalLambda KEYWORD = lambda(args -> {
    var param = args.get(0);
    if (param instanceof MalKeyword keyword) {
      return keyword;
    }
    var name = (MalString) param;
    return keyword(name.value());
  });

  MalLambda VECTOR = lambda(args -> vector(args.values()));

  MalLambda HASH_MAP = lambda(args -> map(args.values()));

  MalLambda ASSOC = lambda(args -> {
    var map = (MalMap) args.get(0);
    var entries = map(args.stream().skip(1).toList());
    return map.addAll(entries.map());
  });

  MalLambda DISSOC = lambda(args -> {
    var map = (MalMap) args.get(0);
    var keys = args.stream().skip(1).map(MalKey.class::cast).toList();
    return map.removeAll(keys);
  });

  MalLambda READ_LINE = lambda(args -> {
    var prompt = (MalString) args.get(0);
    var line = Readline.readline(prompt.value());
    return line != null ? string(line) : NIL;
  });

  MalLambda TIME_MS = lambda(_ -> number(System.nanoTime()));

  MalLambda META = lambda(args -> {
    var meta = args.get(0).meta();
    return meta != null ? meta : NIL;
  });

  MalLambda WITH_META = lambda(args -> args.get(0).withMeta(args.get(1)));

  MalLambda FN_Q = lambda(args -> args.get(0) instanceof MalFunction ? TRUE : FALSE);

  MalLambda STRING_Q = lambda(args -> args.get(0) instanceof MalString ? TRUE : FALSE);

  MalLambda NUMBER_Q = lambda(args -> args.get(0) instanceof MalNumber ? TRUE : FALSE);

  MalLambda SEQ = lambda(args -> switch (args.get(0)) {
    case MalList(var values, _) when values.isEmpty() -> NIL;
    case MalList(var values, _) -> list(values);
    case MalVector(var values, _) when values.isEmpty() -> NIL;
    case MalVector(var values, _) -> list(values);
    case MalString(var value, _) when value.isEmpty() -> NIL;
    case MalString(var value, _) -> asList(value);
    default -> args.get(0);
  });

  MalLambda CONJ = lambda(args -> switch (args.get(0)) {
    case MalList(var values, _) -> {
      var newValues = new ArrayList<>(values);
      args.stream().skip(1).forEach(newValues::addFirst);
      yield list(newValues);
    }
    case MalVector(var values, _) -> {
      var newValues = new ArrayList<>(values);
      args.stream().skip(1).forEach(newValues::addLast);
      yield vector(newValues);
    }
    default -> throw new MalException("invalid definition");
  });

  MalLambda EVAL = lambda(args -> {
    try {
      if (args.get(0) instanceof MalString(var value, _)) {
        var evaluator = new ExpressionEvaluator();
        evaluator.setReturnType(Object.class);
        evaluator.cook(value);
        return toMal(evaluator.evaluate(new Object[] {}));
      }
      throw new MalException("invalid definition");
    } catch (CompileException e) {
      throw new MalException(e.getMessage());
    } catch (InvocationTargetException e) {
      throw new MalException(e.getMessage());
    } catch (RuntimeException e) {
      throw new MalException(e.getMessage());
    }
  });

  Map<String, MalNode> NS = Map.ofEntries(
    entry("prn", function(PRN)),
    entry("println", function(PRINTLN)),
    entry("list", function(LIST)),
    entry("list?", function(LIST_Q)),
    entry("empty?", function(EMPTY_Q)),
    entry("count", function(COUNT)),
    entry("+", function(SUM)),
    entry("-", function(SUB)),
    entry("*", function(MUL)),
    entry("/", function(DIV)),
    entry("%", function(MOD)),
    entry("=", function(EQ)),
    entry(">", function(GT)),
    entry(">=", function(GTE)),
    entry("<", function(LT)),
    entry("<=", function(LTE)),
    entry("pr-str", function(PR_STR)),
    entry("str", function(STR)),
    entry("read-string", function(READ_STRING)),
    entry("slurp", function(SLURP)),
    entry("atom", function(ATOM)),
    entry("atom?", function(ATOM_Q)),
    entry("deref", function(DEREF)),
    entry("reset!", function(RESET)),
    entry("swap!", function(SWAP)),
    entry("cons", function(CONS)),
    entry("concat", function(CONCAT)),
    entry("vec", function(VEC)),
    entry("macro?", function(MACRO_Q)),
    entry("nth", function(NTH)),
    entry("first", function(FIRST)),
    entry("rest", function(REST)),
    entry("throw", function(THROW)),
    entry("apply", function(APPLY)),
    entry("map", function(MAP)),
    entry("nil?", function(NIL_Q)),
    entry("true?", function(TRUE_Q)),
    entry("false?", function(FALSE_Q)),
    entry("symbol?", function(SYMBOL_Q)),
    entry("keyword?", function(KEYWORD_Q)),
    entry("vector?", function(VECTOR_Q)),
    entry("sequential?", function(SEQUENTIAL_Q)),
    entry("map?", function(MAP_Q)),
    entry("contains?", function(CONTAINS_Q)),
    entry("symbol", function(SYMBOL)),
    entry("keyword", function(KEYWORD)),
    entry("vector", function(VECTOR)),
    entry("get", function(GET)),
    entry("keys", function(KEYS)),
    entry("vals", function(VALS)),
    entry("hash-map", function(HASH_MAP)),
    entry("assoc", function(ASSOC)),
    entry("dissoc", function(DISSOC)),
    entry("readline", function(READ_LINE)),
    entry("time-ms", function(TIME_MS)),
    entry("meta", function(META)),
    entry("with-meta", function(WITH_META)),
    entry("fn?", function(FN_Q)),
    entry("string?", function(STRING_Q)),
    entry("number?", function(NUMBER_Q)),
    entry("seq", function(SEQ)),
    entry("conj", function(CONJ)),
    entry("java-eval", function(EVAL))
  );

  private static MalList asList(String string) {
    return list(string.chars().mapToObj(Character::toString).map(MalNode::string).toList());
  }
}
