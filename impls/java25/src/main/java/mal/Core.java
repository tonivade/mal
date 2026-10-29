/*
 * Copyright (c) 2024-2025, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package mal;

import static java.util.Map.entry;
import static java.util.stream.Collectors.joining;
import static mal.MalNode.EMPTY_LIST;
import static mal.MalNode.FALSE;
import static mal.MalNode.NIL;
import static mal.MalNode.TRUE;
import static mal.MalNode.bool;
import static mal.MalNode.function;
import static mal.MalNode.lambda;
import static mal.MalNode.lazy;
import static mal.MalNode.list;
import static mal.MalNode.listOf;
import static mal.MalNode.number;
import static mal.MalNode.string;
import static mal.MalNode.toVector;
import static mal.MalNode.vector;
import static mal.MalNode.vectorOf;
import static mal.Printer.print;
import static mal.Reader.read;
import static mal.Trampoline.done;
import static mal.Trampoline.traverse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Map;
import org.pcollections.PMap;
import org.pcollections.PVector;
import org.pcollections.TreePVector;

import mal.MalNode.MalAtom;
import mal.MalNode.MalCollection;
import mal.MalNode.MalConstant;
import mal.MalNode.MalError;
import mal.MalNode.MalFiber;
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
import mal.MalNode.MalWrapper;

interface Core {

  static MalNode prn(MalList args) {
    IO.println(args.values().stream().map(m -> print(m, true)).collect(joining(" ")));
    return NIL;
  }

  static MalNode println(MalList args) {
    IO.println(args.values().stream().map(m -> print(m, false)).collect(joining(" ")));
    return NIL;
  }

  static MalNode isList(MalList args) {
    return bool(args.get(0) instanceof MalList);
  }

  static MalNode isEmpty(MalList args) {
    if (args.get(0) == NIL) {
      return TRUE;
    }
    if (args.get(0) instanceof MalString(var value, _)) {
      return bool(value.isEmpty());
    }
    if (args.get(0) instanceof MalMap(var values, _)) {
      return bool(values.isEmpty());
    }
    var seq = (MalSequence) args.get(0);
    return bool(seq.seq() == null);
  }

  static MalNode count(MalList args) {
    var first = args.get(0);
    if (first == NIL) {
      return number(0);
    }
    if (first instanceof MalString(var value, _)) {
      return number(value.length());
    }
    if (first instanceof MalMap(var values, _)) {
      return number(values.size());
    }
    return number(((MalSequence) first).size());
  }

  static MalNode eq(MalList args) {
    return bool(MalNode.equals(args.get(0), args.get(1)));
  }

  static MalNode gt(MalList args) {
    var first = (MalNumber) args.get(0);
    var second = (MalNumber) args.get(1);
    return bool(first.gt(second));
  }

  static MalNode gte(MalList args) {
    var first = (MalNumber) args.get(0);
    var second = (MalNumber) args.get(1);
    return bool(first.gte(second));
  }

  static MalNode lt(MalList args) {
    var first = (MalNumber) args.get(0);
    var second = (MalNumber) args.get(1);
    return bool(first.lt(second));
  }

  static MalNode lte(MalList args) {
    var first = (MalNumber) args.get(0);
    var second = (MalNumber) args.get(1);
    return bool(first.lte(second));
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
    var fileName = (MalString) args.get(0);
    try {
      var content = Files.readString(Paths.get(fileName.value()));
      return string(content);
    } catch (NoSuchFileException e) {
      throw new MalException("slurp: file not found: " + fileName.value(), e);
    } catch (IOException e) {
      // some IOExceptions only carry the path as message, then the class name says what went wrong
      var reason = fileName.value().equals(e.getMessage()) ? e.getClass().getSimpleName() : e.getMessage();
      throw new MalException("slurp: cannot read " + fileName.value() + ": " + reason, e);
    }
  }

  static MalAtom atom(MalList args) {
    return MalNode.atom(args.get(0));
  }

  static MalNode isAtom(MalList args) {
    return bool(args.get(0) instanceof MalAtom);
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
    return swapLoop(atom, function, args.values().minus(0).minus(0));
  }

  // compare-and-set loop: if another fiber changed the atom while the function was running,
  // the function is applied again to the new value
  private static Trampoline<MalNode> swapLoop(MalAtom atom, MalWithLambda function, PVector<MalNode> extraArgs) {
    var current = atom.getValue();
    return function.lambda().apply(list(extraArgs.plus(0, current))).flatMap(newValue -> {
      if (atom.compareAndSet(current, newValue)) {
        return done(newValue);
      }
      return swapLoop(atom, function, extraArgs);
    });
  }

  static MalNode cons(MalList args) {
    var item = args.get(0);
    var seq = (MalSequence) args.get(1);
    return seq.cons(item);
  }

  static MalNode concat(MalList args) {
    // nil is concatenated as an empty sequence
    if (args.values().contains(NIL)) {
      return concat(list(args.values().stream().filter(arg -> arg != NIL).toList()));
    }

    if (args.isEmpty()) {
      return EMPTY_LIST;
    }

    if (args.values().stream().allMatch(MalCollection.class::isInstance)) {
      var result = args.values().stream()
          .map(MalCollection.class::cast)
          .map(MalCollection::values)
          .reduce(PVector::plusAll)
          .orElseThrow();
      return list(result);
    }

    if (args.values().stream().allMatch(MalString.class::isInstance)) {
      var result = args.values().stream()
          .map(MalString.class::cast)
          .map(MalString::value)
          .reduce(String::concat)
          .orElseThrow();
      return asList(result);
    }

    if (args.values().stream().allMatch(MalMap.class::isInstance)) {
      var result = args.values().stream()
          .map(MalMap.class::cast)
          .map(MalMap::map)
          .map(Map::entrySet)
          .reduce(TreePVector.<MalVector>empty(), (acc, entries) -> acc.plusAll(entries.stream().map(Core::entryToVector).toList()), TreePVector::plusAll);
      return list(result);
    }

    // the lazy path would only fail when the bad argument is reached, so check the types first
    if (!args.values().stream().allMatch(Core::isConcatenable)) {
      throw invalidArgumentTypes("concat", args, null);
    }

    return lazy(() -> concatStep(args));
  }

  private static boolean isConcatenable(MalNode node) {
    return node instanceof MalSequence || node instanceof MalString || node instanceof MalMap;
  }

  private static MalNode concatStep(MalList seqs) {
    if (seqs.size() == 1) {
      return seqs.get(0);
    }

    if (seqs.size() == 2) {
      // here in case of a lazy-seq this will unwrap only one level
      var seq = ((MalSequence) seqs.head()).seq();
      var tail = seqs.tail();

      // if is null means the list is empty
      if (seq == null) {
        return concat(tail);
      }

      return MalNode.cons(
          seq.head(),
          (MalSequence) concat(
              list(tail.values().plus(0, seq.tail()))
          )
      );
    }

    var first = ((MalSequence) seqs.head());
    var second = ((MalSequence) seqs.tail().head());
    var rest = seqs.tail().tail();

    return concat(listOf(first, concat(listOf(second, concat(rest)))));
  }

  static MalNode vec(MalList args) {
    var list = (MalSequence) args.get(0);
    return toVector(list);
  }

  static MalNode isMacro(MalList args) {
    return bool(args.get(0) instanceof MalMacro);
  }

  static MalNode nth(MalList args) {
    var item = args.get(0);
    var index = (MalNumber) args.get(1);
    // same error for strings, lists, vectors and lazy seqs, reporting the requested index
    try {
      if (item instanceof MalString(var value, _)) {
        var ch = value.charAt(index.asInt());
        return string(Character.toString(ch));
      }
      if (item instanceof MalCollection col && (index.value() < 0 || index.value() >= col.size())) {
        throw new IndexOutOfBoundsException();
      }
      return ((MalSequence) item).get(index.asInt());
    } catch (IndexOutOfBoundsException e) {
      throw new MalException("nth: index out of bounds: " + index.value());
    }
  }

  static MalNode first(MalList args) {
    if (args.get(0).equals(NIL)) {
      return NIL;
    }
    if (args.get(0) instanceof MalString(var value, _) && value.isEmpty()) {
      return NIL;
    }
    if (args.get(0) instanceof MalString(var value, _)) {
      var ch = value.charAt(0);
      return string(Character.toString(ch));
    }
    if (args.get(0) instanceof MalMap(var values, _) && values.isEmpty()) {
      return NIL;
    }
    if (args.get(0) instanceof MalMap(var values, _)) {
      var firstKey = values.keySet().iterator().next();
      return vectorOf(firstKey, values.get(firstKey));
    }
    // seq() is null for empty sequences, including lazy ones
    var seq = ((MalSequence) args.get(0)).seq();
    return seq != null ? seq.head() : NIL;
  }

  static MalNode rest(MalList args) {
    if (args.get(0).equals(NIL)) {
      return EMPTY_LIST;
    }
    if (args.get(0) instanceof MalString(var value, _) && value.isEmpty()) {
      return EMPTY_LIST;
    }
    if (args.get(0) instanceof MalString(var value, _)) {
      return asList(value.substring(1));
    }
    if (args.get(0) instanceof MalMap(var values, _) && values.isEmpty()) {
      return EMPTY_LIST;
    }
    if (args.get(0) instanceof MalMap(var values, _)) {
      var firstKey = values.keySet().iterator().next();
      return asList(values.minus(firstKey));
    }
    var seq = ((MalSequence) args.get(0)).seq();
    return seq != null ? seq.tail() : EMPTY_LIST;
  }

  static MalNode raise(MalList args) {
    throw new MalException(args.get(0));
  }

  static Trampoline<MalNode> apply(MalList args) {
    var function = (MalWithLambda) args.get(0);
    var remaining = args.tail();
    var arguments = TreePVector.<MalNode>empty();

    for (var i = 0; i < remaining.size() - 1; i++) {
      arguments = arguments.plus(remaining.get(i));
    }

    var last = remaining.get(remaining.size() - 1);
    if (last instanceof MalSequence seq) {
      for (var value : seq) {
        arguments = arguments.plus(value);
      }
    } else {
      arguments = arguments.plus(last);
    }

    return function.apply(list(arguments));
  }

  static Trampoline<MalNode> map(MalList args) {
    var function = (MalWithLambda) args.get(0);
    var seq = toSeq(args.get(1));
    if (seq instanceof MalCollection col) {
      return traverse(col.values(), current -> function.apply(listOf(current))).map(MalNode::list);
    }
    return done(mapLazy(function, seq));
  }

  private static MalNode mapLazy(MalWithLambda function, MalSequence elements) {
    return lazy(() -> mapStep(function, elements.seq()));
  }

  private static MalNode mapStep(MalWithLambda function, MalSequence seq) {
    if (seq == null) {
      return EMPTY_LIST;
    }
    return MalNode.cons(
        function.apply(listOf(seq.head())).run(),
        (MalSequence) mapLazy(function, seq.tail()));
  }

  static MalNode isNil(MalList args) {
    return bool(args.get(0).equals(NIL));
  }

  static MalNode isTrue(MalList args) {
    return bool(args.get(0).equals(TRUE));
  }

  static MalNode isFalse(MalList args) {
    return bool(args.get(0).equals(FALSE));
  }

  static MalNode isSymbol(MalList args) {
    return bool(args.get(0) instanceof MalSymbol);
  }

  static MalNode isKeyword(MalList args) {
    return bool(args.get(0) instanceof MalKeyword);
  }

  static MalNode isVector(MalList args) {
    return bool(args.get(0) instanceof MalVector);
  }

  static MalNode isSequential(MalList args) {
    return bool(args.get(0) instanceof MalSequence);
  }

  static MalNode isMap(MalList args) {
    return bool(args.get(0) instanceof MalMap);
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
    return bool(map.contains(key));
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
    var entries = MalNode.map(args.values().minus(0));
    return map.addAll(entries.map());
  }

  static MalNode dissoc(MalList args) {
    var map = (MalMap) args.get(0);
    var keys = args.values().minus(0).stream().map(MalKey.class::cast).toList();
    return map.removeAll(keys);
  }

  static MalNode readLine(MalList args) {
    var prompt = (MalString) args.get(0);
    return Readline.safeReadline(prompt.value());
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
    return bool(args.get(0) instanceof MalFunction);
  }

  static MalNode isString(MalList args) {
    return bool(args.get(0) instanceof MalString);
  }

  static MalNode isNumber(MalList args) {
    return bool(args.get(0) instanceof MalNumber);
  }

  static MalNode seq(MalList args) {
    return switch (args.get(0)) {
      case MalList(var values, _) when values.isEmpty() -> NIL;
      case MalVector(var values, _) when values.isEmpty() -> NIL;
      case MalString(var value, _) when value.isEmpty() -> NIL;
      case MalMap(var values, _) when values.isEmpty() -> NIL;
      case MalVector(var values, _) -> list(values);
      case MalString(var value, _) -> asList(value);
      case MalMap(var values, _) -> asList(values);
      case MalSequence seq when seq.seq() == null -> NIL;
      default -> args.get(0);
    };
  }

  static MalNode conj(MalList args) {
    return switch (args.get(0)) {
      case MalList(var values, _) -> {
        var newValues = values;
        for (var i : args.tail()) {
          newValues = newValues.plus(0, i);
        }
        yield list(newValues);
      }
      case MalVector(var values, _) -> {
        var newValues = values;
        for (var i : args.tail()) {
          newValues = newValues.plus(i);
        }
        yield vector(newValues);
      }
      default -> throw invalidArgumentTypes("conj", args, null);
    };
  }

  static MalNode typeOf(MalList args) {
    if (args.isEmpty()) {
      return NIL;
    }
    return string(args.get(0).getClass().getSimpleName());
  }

  static MalNode join(MalList args) {
    var fiber = (MalFiber) args.get(0);
    return fiber.join();
  }

  static MalNode cancel(MalList args) {
    var fiber = (MalFiber) args.get(0);
    return bool(fiber.cancel());
  }

  static MalNode sleep(MalList args) {
    var seconds = (MalNumber) args.get(0);
    try {
      Thread.sleep(Duration.ofSeconds(seconds.asInt()));
    } catch (InterruptedException e) {
      throw new Trampoline.Interrupted();
    }
    return NIL;
  }

  static MalNode isInstance(MalList args) {
    var obj = (MalWrapper) args.get(0);
    var className = (MalString) args.get(1);
    try {
      var clazz = Class.forName(className.value());
      return bool(clazz.isInstance(obj.value()));
    } catch (ClassNotFoundException e) {
      throw new MalException("class not found: " + className.value(), e);
    }
  }

  // max number of arguments for functions that accept any number of them
  int MANY = Integer.MAX_VALUE;

  Map<String, MalNode> NS = Map.ofEntries(
    def("prn", 0, MANY, lambda(Core::prn)),
    def("println", 0, MANY, lambda(Core::println)),
    def("list", 0, MANY, lambda(MalNode::toList)),
    def("list?", 1, 1, lambda(Core::isList)),
    def("empty?", 1, 1, lambda(Core::isEmpty)),
    def("count", 1, 1, lambda(Core::count)),
    def("+", 2, 2, lambda(Core::sum)),
    def("-", 2, 2, lambda(Core::sub)),
    def("*", 2, 2, lambda(Core::mul)),
    def("/", 2, 2, lambda(Core::div)),
    def("%", 2, 2, lambda(Core::mod)),
    def("=", 2, 2, lambda(Core::eq)),
    def(">", 2, 2, lambda(Core::gt)),
    def(">=", 2, 2, lambda(Core::gte)),
    def("<", 2, 2, lambda(Core::lt)),
    def("<=", 2, 2, lambda(Core::lte)),
    def("pr-str", 0, MANY, lambda(Core::prStr)),
    def("str", 0, MANY, lambda(Core::str)),
    def("read-string", 1, 1, lambda(Core::readString)),
    def("slurp", 1, 1, lambda(Core::slurp)),
    def("atom", 1, 1, lambda(Core::atom)),
    def("atom?", 1, 1, lambda(Core::isAtom)),
    def("deref", 1, 1, lambda(Core::deref)),
    def("reset!", 2, 2, lambda(Core::reset)),
    def("swap!", 2, MANY, Core::swap),
    def("cons", 2, 2, lambda(Core::cons)),
    def("concat", 0, MANY, lambda(Core::concat)),
    def("vec", 1, 1, lambda(Core::vec)),
    def("macro?", 1, 1, lambda(Core::isMacro)),
    def("nth", 2, 2, lambda(Core::nth)),
    def("first", 1, 1, lambda(Core::first)),
    def("rest", 1, 1, lambda(Core::rest)),
    def("throw", 1, 1, lambda(Core::raise)),
    def("apply", 2, MANY, Core::apply),
    def("map", 2, 2, Core::map),
    def("nil?", 1, 1, lambda(Core::isNil)),
    def("true?", 1, 1, lambda(Core::isTrue)),
    def("false?", 1, 1, lambda(Core::isFalse)),
    def("symbol?", 1, 1, lambda(Core::isSymbol)),
    def("keyword?", 1, 1, lambda(Core::isKeyword)),
    def("vector?", 1, 1, lambda(Core::isVector)),
    def("sequential?", 1, 1, lambda(Core::isSequential)),
    def("map?", 1, 1, lambda(Core::isMap)),
    def("contains?", 2, 2, lambda(Core::contains)),
    def("symbol", 1, 1, lambda(Core::symbol)),
    def("keyword", 1, 1, lambda(Core::keyword)),
    def("vector", 0, MANY, lambda(MalNode::toVector)),
    def("get", 1, 2, lambda(Core::get)),
    def("keys", 1, 1, lambda(Core::keys)),
    def("vals", 1, 1, lambda(Core::vals)),
    def("hash-map", 0, MANY, lambda(MalNode::map)),
    def("assoc", 1, MANY, lambda(Core::assoc)),
    def("dissoc", 1, MANY, lambda(Core::dissoc)),
    def("readline", 1, 1, lambda(Core::readLine)),
    def("time-ms", 0, 0, lambda(_ -> timeMs())),
    def("meta", 1, 1, lambda(Core::meta)),
    def("with-meta", 2, 2, lambda(Core::withMeta)),
    def("fn?", 1, 1, lambda(Core::isFunction)),
    def("string?", 1, 1, lambda(Core::isString)),
    def("number?", 1, 1, lambda(Core::isNumber)),
    def("seq", 1, 1, lambda(Core::seq)),
    def("conj", 1, MANY, lambda(Core::conj)),
    def("type-of", 1, 1, lambda(Core::typeOf)),
    def("join", 1, 1, lambda(Core::join)),
    def("cancel", 1, 1, lambda(Core::cancel)),
    def("sleep", 1, 1, lambda(Core::sleep)),
    def("instance?", 2, 2, lambda(Core::isInstance))
  );

  // checks the number of arguments, and reports wrong argument types as a mal error
  // instead of the java ClassCastException thrown by the casts in the functions above
  private static Map.Entry<String, MalNode> def(String name, int min, int max, MalLambda lambda) {
    return entry(name, function(args -> {
      int n = args.size();
      if (n < min || n > max) {
        throw new MalException(name + ": expected " + expectedArgs(min, max) + " argument(s) but got " + n);
      }
      try {
        return lambda.apply(args);
      } catch (ClassCastException e) {
        throw invalidArgumentTypes(name, args, e);
      }
    }));
  }

  private static MalException invalidArgumentTypes(String name, MalList args, Throwable cause) {
    var types = args.values().stream().map(Core::typeName).collect(joining(", "));
    return new MalException(name + ": invalid argument types: " + types, cause);
  }

  private static String expectedArgs(int min, int max) {
    if (min == max) {
      return String.valueOf(min);
    }
    if (max == MANY) {
      return "at least " + min;
    }
    return min + ".." + max;
  }

  private static String typeName(MalNode node) {
    return switch (node) {
      case MalConstant(var name, _) when name.equals("nil") -> "nil";
      case MalConstant _ -> "boolean";
      case MalNumber _ -> "number";
      case MalString _ -> "string";
      case MalKeyword _ -> "keyword";
      case MalSymbol _ -> "symbol";
      case MalList _ -> "list";
      case MalVector _ -> "vector";
      case MalSequence _ -> "seq";
      case MalMap _ -> "map";
      case MalAtom _ -> "atom";
      case MalMacro _ -> "macro";
      case MalFunction _ -> "function";
      case MalFiber _ -> "fiber";
      case MalWrapper _ -> "object";
      case MalError _ -> "error";
    };
  }

  // nil, strings and maps can be used where a sequence is expected, like in seq
  private static MalSequence toSeq(MalNode node) {
    return switch (node) {
      case MalConstant _ when node == NIL -> EMPTY_LIST;
      case MalString(var value, _) -> asList(value);
      case MalMap(var values, _) -> asList(values);
      default -> (MalSequence) node;
    };
  }

  private static MalList asList(String string) {
    return list(string.chars().mapToObj(Character::toString).map(MalNode::string).toList());
  }

  private static MalList asList(PMap<MalKey, MalNode> map) {
    return list(map.entrySet().stream().map(Core::entryToVector).toList());
  }

  private static MalVector entryToVector(Map.Entry<MalKey, MalNode> entry) {
    return vectorOf(entry.getKey(), entry.getValue());
  }
}
