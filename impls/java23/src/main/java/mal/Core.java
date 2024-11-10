package mal;

import static java.util.Map.entry;
import static java.util.stream.Collectors.joining;
import static mal.Mal.FALSE;
import static mal.Mal.NIL;
import static mal.Mal.TRUE;
import static mal.Mal.ZERO;
import static mal.Mal.list;
import static mal.Mal.number;
import static mal.Mal.string;
import static mal.Printer.print;
import static mal.Trampoline.done;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Map;

import mal.Mal.MalAtom;
import mal.Mal.MalFunction;
import mal.Mal.MalIterable;
import mal.Mal.MalList;
import mal.Mal.MalNumber;
import mal.Mal.MalString;

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
    var list = (MalIterable) args.get(0);
    return list.isEmpty() ? done(TRUE) : done(FALSE);
  };

  MalFunction COUNT = args -> {
    var first = args.get(0);
    if (first == NIL) {
      return done(number(0));
    }
    return done(number(((MalIterable) first).size()));
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
    return done(args.stream().map(MalNumber.class::cast).reduce(MalNumber::sum).orElse(ZERO));
  };

  MalFunction SUBS = args -> {
    return done(args.stream().map(MalNumber.class::cast).reduce(MalNumber::subs).orElse(ZERO));
  };

  MalFunction MUL = args -> {
    return done(args.stream().map(MalNumber.class::cast).reduce(MalNumber::mul).orElse(ZERO));
  };

  MalFunction DIV = args -> {
    return done(args.stream().map(MalNumber.class::cast).reduce(MalNumber::div).orElse(ZERO));
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
    return done(Reader.read(string.value()));
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
    return done(Mal.atom(args.get(0)));
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

  Map<String, Mal> NS = Map.ofEntries(
    entry("prn", PRN),
    entry("println", PRINTLN),
    entry("list", LIST),
    entry("list?", LIST_Q),
    entry("empty?", EMPTY_Q),
    entry("count", COUNT),
    entry("+", SUM),
    entry("-", SUBS),
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
    entry("swap!", SWAP)
  );
}
