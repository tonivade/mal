/*
 * Copyright (c) 2024-2025, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package mal;

import static java.util.Comparator.comparingInt;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.joining;
import static mal.MalNode.EMPTY_LIST;
import static mal.MalNode.FALSE;
import static mal.MalNode.NIL;
import static mal.MalNode.TRUE;
import static mal.MalNode.cons;
import static mal.MalNode.lazy;
import static mal.MalNode.list;
import static mal.MalNode.map;
import static mal.MalNode.number;
import static mal.MalNode.string;
import static mal.MalNode.wrap;
import static mal.Trampoline.done;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import mal.MalNode.MalCollection;
import mal.MalNode.MalConstant;
import mal.MalNode.MalFiber;
import mal.MalNode.MalKey;
import mal.MalNode.MalKeyword;
import mal.MalNode.MalLambda;
import mal.MalNode.MalLazy;
import mal.MalNode.MalList;
import mal.MalNode.MalMap;
import mal.MalNode.MalNumber;
import mal.MalNode.MalSequence;
import mal.MalNode.MalString;
import mal.MalNode.MalSymbol;
import mal.MalNode.MalVector;
import mal.MalNode.MalWrapper;

class Interop {

  private static final Map<String, Map<String, Map<Integer, MalLambda>>> CACHE = new ConcurrentHashMap<>();

  static MalLambda method(String clazz, String name, int numberOfArgs) {
    return CACHE
        .computeIfAbsent(clazz, _ -> new ConcurrentHashMap<>())
        .computeIfAbsent(name, _ -> new ConcurrentHashMap<>())
        .computeIfAbsent(numberOfArgs, _ -> methodNonCached(clazz, name, numberOfArgs));
  }

  private static MalLambda methodNonCached(String clazz, String name, int numberOfArgs) {
    var methods = getClass(clazz).map(c -> getMethods(c, name, numberOfArgs)).orElse(List.of());
    if (methods.isEmpty()) {
      throw new MalException("method not found " + clazz + "." + name);
    }
    return args -> invoke(clazz + "." + name, methods, toArgs(args), Interop::toMal);
  }

  static MalLambda constructor(String clazz, int numberOfArgs) {
    return CACHE
        .computeIfAbsent(clazz, _ -> new ConcurrentHashMap<>())
        .computeIfAbsent("<init>", _ -> new ConcurrentHashMap<>())
        .computeIfAbsent(numberOfArgs, _ -> constructorNonCached(clazz, numberOfArgs));
  }

  private static MalLambda constructorNonCached(String clazz, int numberOfArgs) {
    var constructors = getClass(clazz).map(c -> getConstructors(c, numberOfArgs)).orElse(List.of());
    if (constructors.isEmpty()) {
      throw new MalException("constructor not found for class " + clazz);
    }
    // a new object is always wrapped, even a list or a string
    return args -> invoke(clazz + ".<init>", constructors, toArgs(args), MalNode::wrap);
  }

  // the overload is chosen when it's called, using the arguments: among the methods that
  // accept them, the one whose conversions lose the least, see select
  private static Trampoline<MalNode> invoke(String name, List<? extends Executable> candidates, Object[] arguments,
      Function<Object, MalNode> result) {
    var call = select(candidates, arguments)
        .orElseThrow(() -> new MalException("no matching overload of " + name + " for arguments: " + typeNames(arguments)));
    try {
      return done(result.apply(call.invoke()));
    } catch (InvocationTargetException e) {
      // the exception thrown by the java method itself, the reflection wrapper says nothing
      throw new MalException("error calling " + name + ": " + e.getCause(), e.getCause());
    } catch (ReflectiveOperationException e) {
      throw new MalException("error calling " + name + ": " + e, e);
    } catch (LinkageError e) {
      // in the native image a class not registered for reflection throws an error, not an
      // exception, that would end the repl; its message is very long, the class name is enough
      throw new MalException("error calling " + name + ": " + e.getClass().getSimpleName(), e);
    }
  }

  private static Optional<Call> select(List<? extends Executable> candidates, Object[] arguments) {
    // most methods have no overloads, then there is nothing to rank
    if (candidates.size() == 1) {
      return prepare(candidates.getFirst(), arguments);
    }
    // the cheapest conversion wins, see cost; a tie is broken by the signature, because the
    // order of getDeclaredMethods is not specified and the choice must not depend on the jvm
    return candidates.stream()
        .map(candidate -> prepare(candidate, arguments))
        .flatMap(Optional::stream)
        .min(comparingInt(Call::cost).thenComparing(call -> call.executable().toString()));
  }

  private record Call(Executable executable, Object target, Object[] arguments, int cost) {

    Object invoke() throws ReflectiveOperationException {
      if (executable instanceof Constructor<?> constructor) {
        return constructor.newInstance(arguments);
      }
      return ((Method) executable).invoke(target, arguments);
    }
  }

  // empty when the arguments can't be passed to this method or constructor
  private static Optional<Call> prepare(Executable executable, Object[] arguments) {
    Object target = null;
    var params = arguments;
    // for instance methods the first argument is the receiver
    if (executable instanceof Method method && !Modifier.isStatic(method.getModifiers())) {
      if (arguments.length == 0 || arguments[0] == null) {
        return Optional.empty();
      }
      // the receiver is converted like any argument, so a list or a lazy seq can be the
      // receiver of a stream method
      var receiver = convert(method.getDeclaringClass(), arguments[0]);
      if (receiver.isEmpty()) {
        return Optional.empty();
      }
      target = receiver.get();
      params = Arrays.copyOfRange(arguments, 1, arguments.length);
    }
    var types = executable.getParameterTypes();
    if (types.length != params.length) {
      return Optional.empty();
    }
    var converted = new Object[params.length];
    int cost = 0;
    for (int i = 0; i < params.length; i++) {
      // nil is passed as null, which no primitive parameter accepts
      if (params[i] == null) {
        if (types[i].isPrimitive()) {
          return Optional.empty();
        }
        continue;
      }
      var value = convert(types[i], params[i]);
      if (value.isEmpty()) {
        return Optional.empty();
      }
      cost += cost(types[i], params[i], value.get());
      converted[i] = value.get();
    }
    return Optional.of(new Call(executable, target, converted, cost));
  }

  // how much a conversion loses, close to the java rules: nothing when the value is passed as
  // it is, double before the smaller integer types and float last, because they can lose digits,
  // and an array or varargs last, like java tries varargs methods after the others
  private static int cost(Class<?> type, Object arg, Object converted) {
    if (converted == arg) {
      return 0;
    }
    if (type.isArray()) {
      return 2;
    }
    var boxed = BOXES.getOrDefault(type, type);
    if (boxed == Integer.class || boxed == Short.class || boxed == Byte.class) {
      return 2;
    }
    if (boxed == Float.class) {
      return 3;
    }
    return 1;
  }

  static MalNode toMal(Object value) {
    return switch (value) {
      case null -> NIL;
      case String s -> string(s);
      case Boolean b -> b ? TRUE : FALSE;
      case Integer i -> number(i);
      case Long l -> number(l);
      case Byte b -> number(b);
      case Short s -> number(s);
      case Float f -> number(f);
      case Double d -> number(d);
      case Collection<?> l -> listToMal(l);
      case Map<?, ?> m -> mapToMal(m);
      case Stream<?> s -> streamToMal(s.iterator());
      case Object[] a -> listToMal(List.of(a));
      case Character c -> string(c.toString());
      case Enum<?> e -> string(e.name());
      case CompletableFuture<?> f -> wrap(f.thenApply(Interop::toMal));
      case Object o -> wrap(o);
    };
  }

  private static Object toJava(MalNode node) {
    return switch (node) {
      case MalString(var value, _) -> value;
      case MalSymbol(var value, _) -> value;
      case MalKeyword(var value, _) -> value;
      case MalNumber(var value, _) -> value;
      case MalMap(var value, _) -> mapToJava(value);
      case MalList(var value, _) -> value.stream().map(Interop::toJava).toList();
      case MalVector(var value, _) -> value.stream().map(Interop::toJava).toList();
      case MalConstant(var value) when value.equals("true") -> true;
      case MalConstant(var value) when value.equals("false") -> false;
      case MalConstant(var value) when value.equals("nil") -> null;
      case MalWrapper(var value, _) -> value;
      case MalSequence seq -> toStream(seq);
      case MalFiber fiber -> fiber.future().thenApply(Interop::toJava);
      default -> throw new MalException("not supported " + node);
    };
  }

  private static List<Constructor<?>> getConstructors(Class<?> clazz, int numberOfArgs) {
    return Stream.of(clazz.getDeclaredConstructors())
        .filter(m -> m.getParameterCount() == numberOfArgs)
        .filter(m -> m.trySetAccessible())
        .toList();
  }

  // all the overloads with that number of parameters, the ones declared in the class first,
  // then the ones in its interfaces and superclasses
  private static List<Method> getMethods(Class<?> clazz, String method, int numberOfArgs) {
    var declared = Stream.of(clazz.getDeclaredMethods())
        .filter(m -> m.getParameterCount() == numberOfArgs)
        .filter(m -> m.getName().equals(method))
        .filter(m -> m.trySetAccessible());
    var interfaces = Stream.of(clazz.getInterfaces())
        .flatMap(interfaceRef -> getMethods(interfaceRef, method, numberOfArgs).stream());
    var superclass = Optional.ofNullable(clazz.getSuperclass()).stream()
        .flatMap(superRef -> getMethods(superRef, method, numberOfArgs).stream());
    return Stream.of(declared, interfaces, superclass).flatMap(identity()).toList();
  }

  private static Optional<Class<?>> getClass(String clazz) {
    try {
      return Optional.of(Class.forName(clazz));
    } catch (ClassNotFoundException e) {
      return Optional.empty();
    }
  }

  private static Object[] toArgs(MalList args) {
    var arguments = new Object[args.size()];
    for (int i = 0; i < args.size(); i++) {
      var node = args.get(i);
      if (node instanceof MalSequence seq && !(node instanceof MalCollection)) {
        // a lazy seq is converted later, when the type of the parameter is known
        arguments[i] = seq;
      } else {
        arguments[i] = toJava(node);
      }
    }
    return arguments;
  }

  private static final Map<Class<?>, Class<?>> BOXES = Map.of(
      boolean.class, Boolean.class,
      char.class, Character.class,
      byte.class, Byte.class,
      short.class, Short.class,
      int.class, Integer.class,
      long.class, Long.class,
      float.class, Float.class,
      double.class, Double.class);

  // the value to pass for a parameter of that type, empty if it can't be passed; mal numbers
  // are longs, so they are converted to the other number types, and a string of one char to a char;
  // the argument is never null, nil is handled by the callers
  private static Optional<Object> convert(Class<?> type, Object arg) {
    if (arg instanceof MalSequence seq) {
      return convertSequence(type, seq);
    }
    var boxed = BOXES.getOrDefault(type, type);
    if (boxed.isInstance(arg)) {
      return Optional.of(arg);
    }
    if (arg instanceof Number n) {
      return convertNumber(boxed, n);
    }
    if (arg instanceof String s && boxed == Character.class && s.length() == 1) {
      return Optional.of(s.charAt(0));
    }
    if (arg instanceof String s && type.isEnum()) {
      return Stream.<Object>of(type.getEnumConstants())
          .filter(e -> e.toString().equals(s))
          .findFirst();
    }
    if (arg instanceof List<?> list && type.isArray()) {
      return convertArray(type.getComponentType(), list);
    }
    if (arg instanceof List<?> list && type.isAssignableFrom(Stream.class)) {
      return Optional.of(list.stream());
    }
    return Optional.empty();
  }

  // a lazy seq stays lazy as a stream, unless the parameter needs the whole collection;
  // a new stream is created for each overload tried, a stream can only be used once
  private static Optional<Object> convertSequence(Class<?> type, MalSequence seq) {
    if (type.isArray()) {
      return convertArray(type.getComponentType(), realize(seq));
    }
    if (type == Object.class || type.isAssignableFrom(Stream.class)) {
      return Optional.of(toStream(seq));
    }
    if (type.isAssignableFrom(List.class)) {
      return Optional.of(realize(seq));
    }
    return Optional.empty();
  }

  private static Stream<Object> toStream(MalSequence seq) {
    return StreamSupport.stream(seq.spliterator(), false).map(Interop::toJava);
  }

  private static List<Object> realize(MalSequence seq) {
    return toStream(seq).toList();
  }

  private static Optional<Object> convertNumber(Class<?> boxed, Number n) {
    if (boxed == Integer.class) {
      return Optional.of(n.intValue());
    }
    if (boxed == Short.class) {
      return Optional.of(n.shortValue());
    }
    if (boxed == Byte.class) {
      return Optional.of(n.byteValue());
    }
    if (boxed == Double.class) {
      return Optional.of(n.doubleValue());
    }
    if (boxed == Float.class) {
      return Optional.of(n.floatValue());
    }
    return Optional.empty();
  }

  private static Optional<Object> convertArray(Class<?> componentType, List<?> list) {
    var array = Array.newInstance(componentType, list.size());
    for (int i = 0; i < list.size(); i++) {
      var item = list.get(i);
      // a null item stays null, unless the array is of a primitive type
      if (item == null) {
        if (componentType.isPrimitive()) {
          return Optional.empty();
        }
        continue;
      }
      var value = convert(componentType, item);
      if (value.isEmpty()) {
        return Optional.empty();
      }
      Array.set(array, i, value.get());
    }
    return Optional.of(array);
  }

  private static String typeNames(Object[] arguments) {
    return Stream.of(arguments)
        .map(arg -> arg == null ? "null" : arg.getClass().getSimpleName())
        .collect(joining(", "));
  }

  private static MalList listToMal(Collection<?> list) {
    return list(list.stream().map(Interop::toMal).toList());
  }

  private static MalLazy streamToMal(Iterator<?> i) {
    return lazy(() -> {
      if (i.hasNext()) {
        return cons(toMal(i.next()), streamToMal(i));
      }
      return EMPTY_LIST;
    });
  }

  // mal map keys must be strings, a java map with other keys stays as a java object,
  // and can still be used with interop, like (.get m 1)
  private static MalNode mapToMal(Map<?, ?> map) {
    if (!map.keySet().stream().allMatch(String.class::isInstance)) {
      return wrap(map);
    }
    var result = new LinkedHashMap<MalKey, MalNode>();
    map.forEach((key, value) -> result.put(string((String) key), toMal(value)));
    return map(result);
  }

  // a plain loop instead of a collector, so nil values are kept as null entries; keywords
  // become strings, so :a and "a" would be the same java key
  private static Map<Object, Object> mapToJava(Map<MalKey, MalNode> map) {
    var result = new LinkedHashMap<Object, Object>();
    for (var entry : map.entrySet()) {
      var key = toJava(entry.getKey());
      if (result.containsKey(key)) {
        throw new MalException("map key " + Printer.print(entry.getKey(), true)
            + " collides with another key, both are \"" + key + "\" in java");
      }
      result.put(key, toJava(entry.getValue()));
    }
    return result;
  }
}
