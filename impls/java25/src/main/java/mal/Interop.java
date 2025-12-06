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
import static mal.Trampoline.done;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collection;
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

class Interop {

  static MalLambda toLambda(String clazz, String method, int numberOfArgs) {
    try {
      var methodRef = getMethod(clazz, method, numberOfArgs)
          .orElseThrow(() -> new MalException("method not found " + method));
      return args -> {
        try {
          var arguments = args.stream().map(Interop::toJava).toArray();
          if (Modifier.isStatic(methodRef.getModifiers())) {
            var result = methodRef.invoke(null, convertArgs(methodRef, arguments));
            return done(toMal(result));
          } else if (arguments.length > 0) {
            var result = methodRef.invoke(arguments[0], convertArgs(methodRef, Arrays.copyOfRange(arguments, 1, arguments.length)));
            return done(toMal(result));
          }
          throw new MalException("expected argument for method: " + methodRef.getName());
        } catch (IllegalAccessException e) {
          throw new MalException("error calling method: " + methodRef.getName());
        } catch (InvocationTargetException e) {
          throw new MalException("error calling method: " + methodRef.getName());
        }
      };
    } catch (ClassNotFoundException e) {
      throw new MalException("class not found: " + clazz);
    }
  }

  static MalNode toMal(Object value) {
    return switch (value) {
      case null -> NIL;
      case String s -> string(s);
      case Boolean b -> b ? TRUE : FALSE;
      case Integer i -> number(i);
      case Long l -> number(l);
      case Collection<?> l -> listToMal(l);
      case Map<?, ?> m -> mapToMal(m);
      case Stream<?> s -> listToMal(s.toList());
      case Object[] a -> listToMal(Stream.of(a).toList());
      case Character c -> string(c.toString());
      default -> throw new MalException("unknown value " + value);
    };
  }

  static Object toJava(MalNode node) {
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
      default -> throw new MalException("not supported " + node);
    };
  }

  private static Object[] convertArgs(Method method, Object[] arguments) {
    var params = method.getParameterTypes();
    if (params.length != arguments.length) {
      throw new MalException("expected " + params.length + " arguments but got " + arguments.length);
    }
    for (int i = 0; i < params.length; i++) {
      if (params[i].isPrimitive()) {
        if (params[i] == int.class && arguments[i] instanceof Long l) {
          arguments[i] = l.intValue();
        } else if (params[i] == long.class && arguments[i] instanceof Integer n) {
          arguments[i] = n.longValue();
        }
      }
    }
    return arguments;
  }

  private static Optional<Method> getMethod(String clazz, String method, int numberOfArgs) throws ClassNotFoundException {
    var classRef = Class.forName(clazz);
    return Stream.of(classRef.getDeclaredMethods())
        .filter(m -> m.getParameterCount() == numberOfArgs)
        .filter(m -> m.getName().equals(method))
        .filter(m -> m.trySetAccessible())
        .findFirst();
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
