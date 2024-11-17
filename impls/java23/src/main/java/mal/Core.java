package mal;

import static java.util.Map.entry;
import static java.util.stream.Collectors.joining;
import static mal.Mal.FALSE;
import static mal.Mal.NIL;
import static mal.Mal.TRUE;
import static mal.Mal.atom;
import static mal.Mal.keyword;
import static mal.Mal.list;
import static mal.Mal.map;
import static mal.Mal.number;
import static mal.Mal.string;
import static mal.Mal.symbol;
import static mal.Mal.vector;
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

import mal.Mal.MalAtom;
import mal.Mal.MalFunction;
import mal.Mal.MalKey;
import mal.Mal.MalSequence;
import mal.Mal.MalKeyword;
import mal.Mal.MalList;
import mal.Mal.MalMacro;
import mal.Mal.MalMap;
import mal.Mal.MalNumber;
import mal.Mal.MalString;
import mal.Mal.MalSymbol;
import mal.Mal.MalVector;

public interface Core {

  MalFunction PRN = args -> {
    var result = args.stream().map(m -> print(m, true)).collect(joining(" "));
    System.out.println(result);
    return done(NIL);
  };

  MalFunction PRINTLN = args -> {
    var result = args.stream().map(m -> print(m, false)).collect(joining(" "));
    System.out.println(result);
    return done(NIL);
  };

  MalFunction LIST = args -> {
    return done(list(args.values()));
  };

  MalFunction LIST_Q = args -> {
    return args.get(0) instanceof MalList ? done(TRUE) : done(FALSE);
  };

  MalFunction EMPTY_Q = args -> {
    var list = (MalSequence) args.get(0);
    return list.isEmpty() ? done(TRUE) : done(FALSE);
  };

  MalFunction COUNT = args -> {
    var first = args.get(0);
    if (first == NIL) {
      return done(number(0));
    }
    return done(number(((MalSequence) first).size()));
  };

  MalFunction EQ = args -> {
    return Mal.equals(args.get(0), args.get(1)) ? done(TRUE) : done(FALSE);
  };

  MalFunction GT = args -> {
    var first = (MalNumber) args.get(0);
    var second = (MalNumber) args.get(1);
    return first.gt(second) ? done(TRUE) : done(FALSE);
  };

  MalFunction GTE = args -> {
    var first = (MalNumber) args.get(0);
    var second = (MalNumber) args.get(1);
    return first.gte(second) ? done(TRUE) : done(FALSE);
  };

  MalFunction LT = args -> {
    var first = (MalNumber) args.get(0);
    var second = (MalNumber) args.get(1);
    return first.lt(second) ? done(TRUE) : done(FALSE);
  };

  MalFunction LTE = args -> {
    var first = (MalNumber) args.get(0);
    var second = (MalNumber) args.get(1);
    return first.lte(second) ? done(TRUE) : done(FALSE);
  };

  MalFunction SUM = args -> {
    var arg1 = (MalNumber) args.get(0);
    var arg2 = (MalNumber) args.get(1);
    return done(arg1.sum(arg2));
  };

  MalFunction SUB = args -> {
    var arg1 = (MalNumber) args.get(0);
    var arg2 = (MalNumber) args.get(1);
    return done(arg1.sub(arg2));
  };

  MalFunction MUL = args -> {
    var arg1 = (MalNumber) args.get(0);
    var arg2 = (MalNumber) args.get(1);
    return done(arg1.mul(arg2));
  };

  MalFunction DIV = args -> {
    var arg1 = (MalNumber) args.get(0);
    var arg2 = (MalNumber) args.get(1);
    return done(arg1.div(arg2));
  };

  MalFunction PR_STR = args -> {
    var result = args.stream().map(m -> print(m, true)).collect(joining(" "));
    return done(string(result));
  };

  MalFunction STR = args -> {
    var result = args.stream().map(m -> print(m, false)).collect(joining(""));
    return done(string(result));
  };

  MalFunction READ_STRING = args -> {
    var string = (MalString) args.get(0);
    return done(read(string.value()));
  };

  MalFunction SLURP = args -> {
    try {
      var fileName = (MalString) args.get(0);
      var content = Files.readString(Paths.get(fileName.value()));
      return done(string(content));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  };

  MalFunction ATOM = args -> {
    return done(atom(args.get(0)));
  };

  MalFunction ATOM_Q = args -> {
    return args.get(0) instanceof MalAtom ? done(TRUE) : done(FALSE);
  };

  MalFunction DEREF = args -> {
    var atom = (MalAtom) args.get(0);
    return done(atom.getValue());
  };

  MalFunction RESET = args -> {
    var atom = (MalAtom) args.get(0);
    var newValue = args.get(1);
    atom.setValue(newValue);
    return done(newValue);
  };

  MalFunction SWAP = args -> {
    var atom = (MalAtom) args.get(0);
    var function = (MalFunction) args.get(1);
    var newArgs = new ArrayList<Mal>();
    newArgs.add(atom.getValue());
    newArgs.addAll(args.values().stream().skip(2).toList());
    return function.apply(list(newArgs)).map(newValue -> {
      atom.setValue(newValue);
      return newValue;
    });
  };

  MalFunction CONS = args -> {
    var item = args.get(0);
    var list = (MalSequence) args.get(1);
    var result = new ArrayList<Mal>();
    result.add(item);
    result.addAll(list.stream().toList());
    return done(list(result));
  };

  MalFunction CONCAT = args -> {
    var result = args.stream().map(MalSequence.class::cast).flatMap(MalSequence::stream).toList();
    return done(list(result));
  };

  MalFunction VEC = args -> {
    var list = (MalSequence) args.get(0);
    return done(vector(list.stream()));
  };

  MalFunction MACRO_Q = args -> {
    var first = args.get(0);
    return first instanceof MalMacro ? done(TRUE) : done(FALSE);
  };

  MalFunction NTH = args -> {
    var list = (MalSequence) args.get(0);
    var index = (MalNumber) args.get(1);
    if (index.value() < 0 || index.value() >= list.size()) {
      throw new MalException("index out of bounds: " + list.size());
    }
    return done(list.get(index.value()));
  };

  MalFunction FIRST = args -> {
    if (args.get(0).equals(NIL)) {
      return done(NIL);
    }
    var list = (MalSequence) args.get(0);
    if (list.isEmpty()) {
      return done(NIL);
    }
    return done(list.get(0));
  };

  MalFunction REST = args -> {
    if (args.get(0).equals(NIL)) {
      return done(list());
    }
    var list = (MalSequence) args.get(0);
    if (list.isEmpty()) {
      return done(list());
    }
    return done(list(list.stream().skip(1).toList()));
  };

  MalFunction THROW = args -> {
    throw new MalException(args.get(0));
  };

  MalFunction APPLY = args -> {
    var function = (MalFunction) args.get(0);
    var arguments = list(args.stream().skip(1).flatMap(m -> switch (m) {
      case MalList(var values) -> values.stream();
      case MalVector(var values) -> values.stream();
      default -> Stream.of(m);
    }).toList());
    return function.apply(arguments);
  };

  MalFunction MAP = args -> {
    var function = (MalFunction) args.get(0);
    var elements = (MalSequence) args.get(1);
    var result = elements.stream().map(m -> function.apply(list(m))).toList();
    return traverse(result).map(Mal::list);
  };

  MalFunction NIL_Q = args -> {
    return args.get(0).equals(NIL) ? done(TRUE) : done(FALSE);
  };

  MalFunction TRUE_Q = args -> {
    return args.get(0).equals(TRUE) ? done(TRUE) : done(FALSE);
  };

  MalFunction FALSE_Q = args -> {
    return args.get(0).equals(FALSE) ? done(TRUE) : done(FALSE);
  };

  MalFunction SYMBOL_Q = args -> {
    return args.get(0) instanceof MalSymbol ? done(TRUE) : done(FALSE);
  };

  MalFunction KEYWORD_Q = args -> {
    return args.get(0) instanceof MalKeyword ? done(TRUE) : done(FALSE);
  };

  MalFunction VECTOR_Q = args -> {
    return args.get(0) instanceof MalVector ? done(TRUE) : done(FALSE);
  };

  MalFunction SEQUENTIAL_Q = args -> {
    return args.get(0) instanceof MalSequence ? done(TRUE) : done(FALSE);
  };

  MalFunction MAP_Q = args -> {
    return args.get(0) instanceof MalMap ? done(TRUE) : done(FALSE);
  };

  MalFunction GET = args -> {
    if (args.get(0).equals(NIL)) {
      return done(NIL);
    }
    var map = (MalMap) args.get(0);
    var key = (MalKey) args.get(1);
    return done(map.get(key));
  };

  MalFunction CONTAINS_Q = args -> {
    var map = (MalMap) args.get(0);
    var key = (MalKey) args.get(1);
    return map.contains(key) ? done(TRUE) : done(FALSE);
  };

  MalFunction KEYS = args -> {
    var map = (MalMap) args.get(0);
    return done(list(map.keys()));
  };

  MalFunction VALS = args -> {
    var map = (MalMap) args.get(0);
    return done(list(map.values()));
  };

  MalFunction SYMBOL = args -> {
    var name = (MalString) args.get(0);
    return done(symbol(name.value()));
  };

  MalFunction KEYWORD = args -> {
    var param = args.get(0);
    if (param instanceof MalKeyword keyword) {
      return done(keyword);
    }
    var name = (MalString) param;
    return done(keyword(name.value()));
  };

  MalFunction VECTOR = args -> {
    return done(vector(args.values()));
  };

  MalFunction HASH_MAP = args -> {
    return done(map(args.values()));
  };

  MalFunction ASSOC = args -> {
    var map = (MalMap) args.get(0);
    var entries = map(args.stream().skip(1).toList());
    return done(map.addAll(entries.map()));
  };

  MalFunction DISSOC = args -> {
    var map = (MalMap) args.get(0);
    var keys = args.stream().skip(1).map(MalKey.class::cast).toList();
    return done(map.removeAll(keys));
  };

  Map<String, Mal> NS = Map.ofEntries(
    entry("prn", PRN),
    entry("println", PRINTLN),
    entry("list", LIST),
    entry("list?", LIST_Q),
    entry("empty?", EMPTY_Q),
    entry("count", COUNT),
    entry("+", SUM),
    entry("-", SUB),
    entry("*", MUL),
    entry("/", DIV),
    entry("=", EQ),
    entry(">", GT),
    entry(">=", GTE),
    entry("<", LT),
    entry("<=", LTE),
    entry("pr-str", PR_STR),
    entry("str", STR),
    entry("read-string", READ_STRING),
    entry("slurp", SLURP),
    entry("atom", ATOM),
    entry("atom?", ATOM_Q),
    entry("deref", DEREF),
    entry("reset!", RESET),
    entry("swap!", SWAP),
    entry("cons", CONS),
    entry("concat", CONCAT),
    entry("vec", VEC),
    entry("macro?", MACRO_Q),
    entry("nth", NTH),
    entry("first", FIRST),
    entry("rest", REST),
    entry("throw", THROW),
    entry("apply", APPLY),
    entry("map", MAP),
    entry("nil?", NIL_Q),
    entry("true?", TRUE_Q),
    entry("false?", FALSE_Q),
    entry("symbol?", SYMBOL_Q),
    entry("keyword?", KEYWORD_Q),
    entry("vector?", VECTOR_Q),
    entry("sequential?", SEQUENTIAL_Q),
    entry("map?", MAP_Q),
    entry("contains?", CONTAINS_Q),
    entry("symbol", SYMBOL),
    entry("keyword", KEYWORD),
    entry("vector", VECTOR),
    entry("get", GET),
    entry("keys", KEYS),
    entry("vals", VALS),
    entry("hash-map", HASH_MAP),
    entry("assoc", ASSOC),
    entry("dissoc", DISSOC)
  );
}
