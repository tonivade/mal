/*
 * Copyright (c) 2024, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package mal.lib;

import static java.util.Map.entry;
import static mal.MalNode.NIL;
import static mal.MalNode.function;
import static mal.MalNode.list;
import static mal.MalNode.number;
import static mal.MalNode.string;
import static mal.Trampoline.done;

import java.util.ArrayList;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import mal.MalNode;
import mal.MalNode.MalLambda;
import mal.MalNode.MalString;

public interface Strings {

  MalLambda SPLIT_LINES = args -> {
    var input = (MalString) args.get(0);
    return done(list(input.value().lines().map(MalNode::string)));
  };

  MalLambda SPLIT = args -> {
    var input = (MalString) args.get(0);
    var regex = (MalString) args.get(1);
    var split = input.value().split(regex.value());
    return done(list(Stream.of(split).map(MalNode::string)));
  };

  MalLambda FIND_MATCHES = args -> {
    var input = (MalString) args.get(0);
    var regex = (MalString) args.get(1);

    var matcher = Pattern.compile(regex.value()).matcher(input.value());

    var fullResult = matcher.results().map(result -> {
      var list = new ArrayList<MalNode>();
      list.add(string(result.group(0)));
      for (int i = 1; i <= result.groupCount(); i++) {
        var group = result.group(i);
        if (group != null) {
          list.add(string(group));
        } else {
          list.add(NIL);
        }
      }
      return list(list);
    }).toList();

    return done(list(fullResult));
  };

  MalLambda TO_NUMBER = args -> {
    var input = (MalString) args.get(0);
    return done(number(Integer.parseInt(input.value())));
  };

  Map<String, MalNode> NS = Map.ofEntries(
    entry("split-lines", function(SPLIT_LINES)),
    entry("split", function(SPLIT)),
    entry("to-number", function(TO_NUMBER)),
    entry("find-matches", function(FIND_MATCHES))
  );
}
