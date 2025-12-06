/*
 * Copyright (c) 2024-2025, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package mal.lib;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public interface Strings {

  static List<List<String>> findMatches(String input, String regex) {
    var matcher = Pattern.compile(regex).matcher(input);

    var fullResult = matcher.results().map(result -> {
      List<String> list = new ArrayList<>();
      list.add(result.group(0));
      for (int i = 1; i <= result.groupCount(); i++) {
        var group = result.group(i);
        list.add(group != null ? group : null);
      }
      return list;
    }).toList();
    return fullResult;
  }
}
