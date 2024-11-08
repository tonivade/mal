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

import java.util.Map;

import mal.Mal.MalFunction;
import mal.Mal.MalIterable;
import mal.Mal.MalList;
import mal.Mal.MalNumber;

public interface Core {

  MalFunction PRN = args -> {
    var result = args.stream().map(m -> print(m, true)).collect(joining(" "));
    System.out.println(result);
    return NIL;
  };

  MalFunction PRINTLN = args -> {
    var result = args.stream().map(m -> print(m, false)).collect(joining(" "));
    System.out.println(result);
    return NIL;
  };

  MalFunction LIST = args -> {
    return list(args.values());
  };

  MalFunction LIST_Q = args -> {
    return args.get(0) instanceof MalList ? TRUE : FALSE;
  };

  MalFunction EMPTY_Q = args -> {
    var list = (MalIterable) args.get(0);
    return list.isEmpty() ? TRUE : FALSE;
  };

  MalFunction COUNT = args -> {
    var first = args.get(0);
    if (first == NIL) {
      return number(0);
    }
    return number(((MalIterable) first).size());
  };

  MalFunction EQ = args -> {
    return equals(args.get(0), args.get(1)) ? TRUE : FALSE;
  };

  MalFunction GT = args -> {
    var first = (MalNumber) args.get(0);
    var second = (MalNumber) args.get(1);
    return first.gt(second) ? TRUE : FALSE;
  };

  MalFunction GTE = args -> {
    var first = (MalNumber) args.get(0);
    var second = (MalNumber) args.get(1);
    return first.gte(second) ? TRUE : FALSE;
  };

  MalFunction LT = args -> {
    var first = (MalNumber) args.get(0);
    var second = (MalNumber) args.get(1);
    return first.lt(second) ? TRUE : FALSE;
  };

  MalFunction LTE = args -> {
    var first = (MalNumber) args.get(0);
    var second = (MalNumber) args.get(1);
    return first.lte(second) ? TRUE : FALSE;
  };
      
  MalFunction SUM = args -> {
    return args.stream().map(MalNumber.class::cast).reduce(MalNumber::sum).orElse(ZERO);
  };

  MalFunction SUBS = args -> {
    return args.stream().map(MalNumber.class::cast).reduce(MalNumber::subs).orElse(ZERO);
  };
  
  MalFunction MUL = args -> {
    return args.stream().map(MalNumber.class::cast).reduce(MalNumber::mul).orElse(ZERO);
  };

  MalFunction DIV = args -> {
    return args.stream().map(MalNumber.class::cast).reduce(MalNumber::div).orElse(ZERO);
  };

  MalFunction PR_STR = args -> {
    var result = args.stream().map(m -> print(m, true)).collect(joining(" "));
    return string(result);
  };

  MalFunction STR = args -> {
    var result = args.stream().map(m -> print(m, false)).collect(joining(""));
    return string(result);
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
    entry("str", STR));

  private static boolean equals(Mal first, Mal second) {
    if (first instanceof MalIterable a && second instanceof MalIterable b) {
      var i = a.iterator();
      var j = b.iterator();
      while (i.hasNext() && j.hasNext()) {
        if (!equals(i.next(), j.next())) {
          return false;
        }
      }
      return !(i.hasNext() || j.hasNext());
    }
    return first.equals(second);
  }
}
