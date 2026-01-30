/*
 * Copyright (c) 2024-2025, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package mal;

import static java.util.Map.entry;
import static java.util.stream.Collectors.toUnmodifiableMap;
import static mal.MalNode.FALSE;
import static mal.MalNode.NIL;
import static mal.MalNode.TRUE;
import static mal.MalNode.list;
import static mal.MalNode.map;
import static mal.MalNode.number;
import static mal.MalNode.string;
import static mal.MalNode.wrap;
import static mal.Trampoline.done;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import mal.MalNode.MalConstant;
import mal.MalNode.MalKey;
import mal.MalNode.MalKeyword;
import mal.MalNode.MalLambda;
import mal.MalNode.MalList;
import mal.MalNode.MalMap;
import mal.MalNode.MalNumber;
import mal.MalNode.MalSequence;
import mal.MalNode.MalString;
import mal.MalNode.MalSymbol;
import mal.MalNode.MalVector;
import mal.MalNode.MalWrapper;

class Interop {

  private static final Map<String, Map<String, Map<Integer, MalLambda>>> CACHE = new HashMap<>();

  static MalLambda method(String clazz, String name, int numberOfArgs) {
    return CACHE
        .computeIfAbsent(clazz, _ -> new HashMap<>())
        .computeIfAbsent(name, _ -> new HashMap<>())
        .computeIfAbsent(numberOfArgs, _ -> methodNonCached(clazz, name, numberOfArgs));
  }

  private static MalLambda methodNonCached(String clazz, String name, int numberOfArgs) {
    var method = getMethod(clazz, name, numberOfArgs)
        .orElseThrow(() -> new MalException("method not found " + name));
    if (Modifier.isStatic(method.getModifiers())) {
      return args -> {
        try {
          var arguments = toArgs(args);
          var result = method.invoke(null, convertArgs(method, arguments));
          return done(toMal(result));
        } catch (IllegalAccessException e) {
          throw new MalException("error calling method: " + method.getName(), e);
        } catch (InvocationTargetException e) {
          throw new MalException("error calling method: " + method.getName(), e);
        }
      };
    }
    return args -> {
      try {
        var arguments = toArgs(args);
        if (arguments.length > 0) {
          var result = method.invoke(arguments[0], convertArgs(method, Arrays.copyOfRange(arguments, 1, arguments.length)));
          return done(toMal(result));
        }
        throw new MalException("expected argument for method: " + method.getName());
      } catch (IllegalAccessException e) {
        throw new MalException("error calling method: " + method.getName(), e);
      } catch (InvocationTargetException e) {
        throw new MalException("error calling method: " + method.getName(), e);
      }
    };
  }

  static MalLambda constructor(String clazz, int numberOfArgs) {
    return CACHE
        .computeIfAbsent(clazz, _ -> new HashMap<>())
        .computeIfAbsent("<init>", _ -> new HashMap<>())
        .computeIfAbsent(numberOfArgs, _ -> constructorNonCached(clazz, numberOfArgs));
  }

  static MalLambda constructorNonCached(String clazz, int numberOfArgs) {
    var constructor = getConstructor(clazz, numberOfArgs)
        .orElseThrow(() -> new MalException("constructor not found for class " + clazz));
    return args -> {
      try {
        var arguments = toArgs(args);
        var result = constructor.newInstance(convertArgs(constructor, arguments));
        return done(wrap(result));
      } catch (IllegalAccessException e) {
        throw new MalException("error calling method: " + constructor.getName(), e);
      } catch (InvocationTargetException e) {
        throw new MalException("error calling method: " + constructor.getName(), e);
      } catch (InstantiationException e) {
        throw new MalException("error calling method: " + constructor.getName(), e);
      }
    };
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
      case Stream<?> s -> listToMal(s.toList());
      case Object[] a -> listToMal(List.of(a));
      case Character c -> string(c.toString());
      case Enum<?> e -> string(e.name());
      case Object o -> wrap(o);
    };
  }

  private static Object toJava(MalNode node) {
    return switch (node) {
      case MalString(var value, _) -> value;
      case MalSymbol(var value, _) -> value;
      case MalKeyword(var value, _) -> value;
      case MalNumber(var value, _) -> value;
      case MalMap(var value, _) -> value;
      case MalList(var value, _) -> value;
      case MalVector(var value, _) -> value;
      case MalConstant(var value, _) when value.equals("true") -> true;
      case MalConstant(var value, _) when value.equals("false") -> false;
      case MalConstant(var value, _) when value.equals("nil") -> null;
      case MalWrapper(var value, _) -> value;
      default -> throw new MalException("not supported " + node);
    };
  }

  private static Optional<Constructor<?>> getConstructor(String clazz, int numberOfArgs) {
    try {
      var classRef = Class.forName(clazz);
      return Stream.of(classRef.getDeclaredConstructors())
          .filter(m -> m.getParameterCount() == numberOfArgs)
          .filter(m -> m.trySetAccessible())
          .findFirst();
    } catch (ClassNotFoundException e) {
      return Optional.empty();
    }
  }

  private static Optional<Method> getMethod(String clazz, String method, int numberOfArgs) {
    try {
      var classRef = Class.forName(clazz);
      return Stream.of(classRef.getDeclaredMethods())
          .filter(m -> m.getParameterCount() == numberOfArgs)
          .filter(m -> m.getName().equals(method))
          .filter(m -> m.trySetAccessible())
          .findFirst();
    } catch (ClassNotFoundException e) {
      return Optional.empty();
    }
  }

  private static Object[] toArgs(MalList args) {
    var arguments = new Object[args.size()];
    for (int i = 0; i < args.size(); i++) {
      arguments[i] = toJava(args.get(i));
    }
    return arguments;
  }

  private static Object[] convertArgs(Executable executable, Object[] arguments) {
    var params = executable.getParameterTypes();
    if (params.length != arguments.length) {
      throw new MalException("expected " + params.length + " arguments but got " + arguments.length);
    }
    for (int i = 0; i < params.length; i++) {
      if (params[i].isPrimitive() && arguments[i] instanceof Number n) {
        if (params[i] == int.class) {
          arguments[i] = n.intValue();
        } else if (params[i] == short.class) {
          arguments[i] = n.shortValue();
        } else if (params[i] == byte.class) {
          arguments[i] = n.byteValue();
        }
      }
    }
    return arguments;
  }

  private static MalSequence listToMal(Collection<?> list) {
    return list(list.stream().map(Interop::toMal).toList());
  }

  private static MalMap mapToMal(Map<?, ?> map) {
    return map(map.entrySet().stream()
        .map(entry -> entry(convertKey(entry.getKey()), toMal(entry.getValue())))
        .collect(toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue)));
  }

  private static MalKey convertKey(Object value) {
    return switch (value) {
      case String s -> string(s);
      case null, default -> throw new MalException("invalid key" + value);
    };
  }
}
