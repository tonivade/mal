package mal;

import static java.util.Map.entry;
import static java.util.stream.Collectors.joining;
import static mal.MalNode.FALSE;
import static mal.MalNode.NIL;
import static mal.MalNode.TRUE;
import static mal.MalNode.atom;
import static mal.MalNode.function;
import static mal.MalNode.keyword;
import static mal.MalNode.list;
import static mal.MalNode.map;
import static mal.MalNode.number;
import static mal.MalNode.string;
import static mal.MalNode.symbol;
import static mal.MalNode.vector;
import static mal.Printer.print;
import static mal.Reader.read;
import static mal.Trampoline.done;
import static mal.Trampoline.traverse;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Map;
import java.util.stream.Stream;

import mal.MalNode.MalAtom;
import mal.MalNode.MalFunction;
import mal.MalNode.MalKey;
import mal.MalNode.MalSequence;
import mal.MalNode.MalKeyword;
import mal.MalNode.MalLambda;
import mal.MalNode.MalList;
import mal.MalNode.MalMacro;
import mal.MalNode.MalMap;
import mal.MalNode.MalNumber;
import mal.MalNode.MalString;
import mal.MalNode.MalSymbol;
import mal.MalNode.MalVector;
import mal.MalNode.MalWithLambda;

public interface Core {

  MalLambda PRN = args -> {
    var result = args.stream().map(m -> print(m, true)).collect(joining(" "));
    System.out.println(result);
    return done(NIL);
  };

  MalLambda PRINTLN = args -> {
    var result = args.stream().map(m -> print(m, false)).collect(joining(" "));
    System.out.println(result);
    return done(NIL);
  };

  MalLambda LIST = args -> {
    return done(list(args.values()));
  };

  MalLambda LIST_Q = args -> {
    return args.get(0) instanceof MalList ? done(TRUE) : done(FALSE);
  };

  MalLambda EMPTY_Q = args -> {
    var list = (MalSequence) args.get(0);
    return list.isEmpty() ? done(TRUE) : done(FALSE);
  };

  MalLambda COUNT = args -> {
    var first = args.get(0);
    if (first == NIL) {
      return done(number(0));
    }
    return done(number(((MalSequence) first).size()));
  };

  MalLambda EQ = args -> {
    return MalNode.equals(args.get(0), args.get(1)) ? done(TRUE) : done(FALSE);
  };

  MalLambda GT = args -> {
    var first = (MalNumber) args.get(0);
    var second = (MalNumber) args.get(1);
    return first.gt(second) ? done(TRUE) : done(FALSE);
  };

  MalLambda GTE = args -> {
    var first = (MalNumber) args.get(0);
    var second = (MalNumber) args.get(1);
    return first.gte(second) ? done(TRUE) : done(FALSE);
  };

  MalLambda LT = args -> {
    var first = (MalNumber) args.get(0);
    var second = (MalNumber) args.get(1);
    return first.lt(second) ? done(TRUE) : done(FALSE);
  };

  MalLambda LTE = args -> {
    var first = (MalNumber) args.get(0);
    var second = (MalNumber) args.get(1);
    return first.lte(second) ? done(TRUE) : done(FALSE);
  };

  MalLambda SUM = args -> {
    var arg1 = (MalNumber) args.get(0);
    var arg2 = (MalNumber) args.get(1);
    return done(arg1.sum(arg2));
  };

  MalLambda SUB = args -> {
    var arg1 = (MalNumber) args.get(0);
    var arg2 = (MalNumber) args.get(1);
    return done(arg1.sub(arg2));
  };

  MalLambda MUL = args -> {
    var arg1 = (MalNumber) args.get(0);
    var arg2 = (MalNumber) args.get(1);
    return done(arg1.mul(arg2));
  };

  MalLambda DIV = args -> {
    var arg1 = (MalNumber) args.get(0);
    var arg2 = (MalNumber) args.get(1);
    return done(arg1.div(arg2));
  };

  MalLambda PR_STR = args -> {
    var result = args.stream().map(m -> print(m, true)).collect(joining(" "));
    return done(string(result));
  };

  MalLambda STR = args -> {
    var result = args.stream().map(m -> print(m, false)).collect(joining(""));
    return done(string(result));
  };

  MalLambda READ_STRING = args -> {
    var string = (MalString) args.get(0);
    return done(read(string.value()));
  };

  MalLambda SLURP = args -> {
    try {
      var fileName = (MalString) args.get(0);
      var content = Files.readString(Paths.get(fileName.value()));
      return done(string(content));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  };

  MalLambda ATOM = args -> {
    return done(atom(args.get(0)));
  };

  MalLambda ATOM_Q = args -> {
    return args.get(0) instanceof MalAtom ? done(TRUE) : done(FALSE);
  };

  MalLambda DEREF = args -> {
    var atom = (MalAtom) args.get(0);
    return done(atom.getValue());
  };

  MalLambda RESET = args -> {
    var atom = (MalAtom) args.get(0);
    var newValue = args.get(1);
    atom.setValue(newValue);
    return done(newValue);
  };

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

  MalLambda CONS = args -> {
    var item = args.get(0);
    var list = (MalSequence) args.get(1);
    var result = new ArrayList<MalNode>();
    result.add(item);
    result.addAll(list.stream().toList());
    return done(list(result));
  };

  MalLambda CONCAT = args -> {
    var result = args.stream().map(MalSequence.class::cast).flatMap(MalSequence::stream).toList();
    return done(list(result));
  };

  MalLambda VEC = args -> {
    var list = (MalSequence) args.get(0);
    return done(vector(list.stream()));
  };

  MalLambda MACRO_Q = args -> {
    var first = args.get(0);
    return first instanceof MalMacro ? done(TRUE) : done(FALSE);
  };

  MalLambda NTH = args -> {
    var list = (MalSequence) args.get(0);
    var index = (MalNumber) args.get(1);
    if (index.value() < 0 || index.value() >= list.size()) {
      throw new MalException("index out of bounds: " + list.size());
    }
    return done(list.get(index.asInt()));
  };

  MalLambda FIRST = args -> {
    if (args.get(0).equals(NIL)) {
      return done(NIL);
    }
    var list = (MalSequence) args.get(0);
    if (list.isEmpty()) {
      return done(NIL);
    }
    return done(list.get(0));
  };

  MalLambda REST = args -> {
    if (args.get(0).equals(NIL)) {
      return done(list());
    }
    var list = (MalSequence) args.get(0);
    if (list.isEmpty()) {
      return done(list());
    }
    return done(list(list.stream().skip(1).toList()));
  };

  MalLambda THROW = args -> {
    throw new MalException(args.get(0));
  };

  MalLambda APPLY = args -> {
    var function = (MalWithLambda) args.get(0);
    var arguments = list(args.stream().skip(1).flatMap(m -> switch (m) {
      case MalList(var values, var _) -> values.stream();
      case MalVector(var values, var _) -> values.stream();
      default -> Stream.of(m);
    }).toList());
    return function.lambda().apply(arguments);
  };

  MalLambda MAP = args -> {
    var function = (MalWithLambda) args.get(0);
    var elements = (MalSequence) args.get(1);
    var result = elements.stream().map(m -> function.lambda().apply(list(m))).toList();
    return traverse(result).map(MalNode::list);
  };

  MalLambda NIL_Q = args -> {
    return args.get(0).equals(NIL) ? done(TRUE) : done(FALSE);
  };

  MalLambda TRUE_Q = args -> {
    return args.get(0).equals(TRUE) ? done(TRUE) : done(FALSE);
  };

  MalLambda FALSE_Q = args -> {
    return args.get(0).equals(FALSE) ? done(TRUE) : done(FALSE);
  };

  MalLambda SYMBOL_Q = args -> {
    return args.get(0) instanceof MalSymbol ? done(TRUE) : done(FALSE);
  };

  MalLambda KEYWORD_Q = args -> {
    return args.get(0) instanceof MalKeyword ? done(TRUE) : done(FALSE);
  };

  MalLambda VECTOR_Q = args -> {
    return args.get(0) instanceof MalVector ? done(TRUE) : done(FALSE);
  };

  MalLambda SEQUENTIAL_Q = args -> {
    return args.get(0) instanceof MalSequence ? done(TRUE) : done(FALSE);
  };

  MalLambda MAP_Q = args -> {
    return args.get(0) instanceof MalMap ? done(TRUE) : done(FALSE);
  };

  MalLambda GET = args -> {
    if (args.get(0).equals(NIL)) {
      return done(NIL);
    }
    var map = (MalMap) args.get(0);
    var key = (MalKey) args.get(1);
    return done(map.get(key));
  };

  MalLambda CONTAINS_Q = args -> {
    var map = (MalMap) args.get(0);
    var key = (MalKey) args.get(1);
    return map.contains(key) ? done(TRUE) : done(FALSE);
  };

  MalLambda KEYS = args -> {
    var map = (MalMap) args.get(0);
    return done(list(map.keys()));
  };

  MalLambda VALS = args -> {
    var map = (MalMap) args.get(0);
    return done(list(map.values()));
  };

  MalLambda SYMBOL = args -> {
    var name = (MalString) args.get(0);
    return done(symbol(name.value()));
  };

  MalLambda KEYWORD = args -> {
    var param = args.get(0);
    if (param instanceof MalKeyword keyword) {
      return done(keyword);
    }
    var name = (MalString) param;
    return done(keyword(name.value()));
  };

  MalLambda VECTOR = args -> {
    return done(vector(args.values()));
  };

  MalLambda HASH_MAP = args -> {
    return done(map(args.values()));
  };

  MalLambda ASSOC = args -> {
    var map = (MalMap) args.get(0);
    var entries = map(args.stream().skip(1).toList());
    return done(map.addAll(entries.map()));
  };

  MalLambda DISSOC = args -> {
    var map = (MalMap) args.get(0);
    var keys = args.stream().skip(1).map(MalKey.class::cast).toList();
    return done(map.removeAll(keys));
  };

  MalLambda READ_LINE = args -> {
    var prompt = (MalString) args.get(0);
    System.out.print(prompt.value());
    var line = System.console().readLine();
    return line != null ? done(string(line)) : done(NIL);
  };

  MalLambda TIME_MS = _ -> {
    return done(number(System.nanoTime()));
  };

  MalLambda META = args -> {
    var meta = args.get(0).meta();
    return meta != null ? done(meta) : done(NIL);
  };

  MalLambda WITH_META = args -> {
    return done(args.get(0).withMeta(args.get(1)));
  };

  MalLambda FN_Q = args -> {
    return args.get(0) instanceof MalFunction ? done(TRUE) : done(FALSE);
  };

  MalLambda STRING_Q = args -> {
    return args.get(0) instanceof MalString ? done(TRUE) : done(FALSE);
  };

  MalLambda NUMBER_Q = args -> {
    return args.get(0) instanceof MalNumber ? done(TRUE) : done(FALSE);
  };

  MalLambda SEQ = args -> {
    return switch (args.get(0)) {
      case MalList(var values, var _) when values.isEmpty() -> done(NIL);
      case MalList(var values, var _) -> done(list(values));
      case MalVector(var values, var _) when values.isEmpty() -> done(NIL);
      case MalVector(var values, var _) -> done(list(values));
      case MalString(var value, var _) when value.isEmpty() -> done(NIL);
      case MalString(var value, var _) -> done(asList(value));
      default -> done(args.get(0));
    };
  };

  MalLambda CONJ = args -> {
    return switch (args.get(0)) {
      case MalList(var values, var _) -> {
        var newValues = new ArrayList<>(values);
        args.stream().skip(1).forEach(newValues::addFirst);
        yield done(list(newValues));
      }
      case MalVector(var values, var _) -> {
        var newValues = new ArrayList<>(values);
        args.stream().skip(1).forEach(newValues::addLast);
        yield done(vector(newValues));
      }
      default -> throw new MalException("invalid definition");
    };
  };

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
    entry("conj", function(CONJ))
  );

  private static MalList asList(String string) {
    return list(string.chars().mapToObj(Character::toString).map(MalNode::string).toList());
  }
}
