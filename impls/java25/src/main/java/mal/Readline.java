/*
 * Copyright (c) 2024-2025, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package mal;

import java.io.IOException;
import java.io.UncheckedIOException;

import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.impl.DefaultHighlighter;
import org.jline.reader.impl.DefaultParser;
import org.jline.reader.impl.completer.StringsCompleter;
import org.jline.reader.impl.history.DefaultHistory;

class Readline {

  private static final LineReader reader = LineReaderBuilder.builder()
    .variable(LineReader.HISTORY_FILE, System.getProperty("user.home") + "/.malhistory")
    .variable(LineReader.HISTORY_FILE_SIZE, 1000)
    .option(LineReader.Option.DISABLE_EVENT_EXPANSION, true)
    .history(new DefaultHistory())
    .completer(new StringsCompleter("println", "prn", "list", "list?", "empty?",
      "count", "pr-str", "str", "read-string", "slurp", "atom", "atom?", "deref", "reset!",
      "swap!", "cons", "concat", "vec", "macro?", "nth", "first", "rest", "throw", "apply",
      "map", "nil", "nil?", "true", "true?", "false", "false?", "symbol?", "keyword?",
      "vector?", "sequential?", "map?", "contains?", "symbol", "keyword", "vector", "get",
      "keys", "vals", "hash-map", "assoc", "dissoc", "readline", "time-ms", "meta", "with-meta",
      "fn?", "string?", "number?", "seq", "conj", "def!", "defmacro!", "let*", "do", "try*",
      "if", "fn", "quote", "quasiquote", "catch*", "eval", "import", "java-eval", "lazy-seq"))
    .highlighter(new DefaultHighlighter())
    .parser(new DefaultParser().lineCommentDelims(new String[] { ";" }))
    .build();

  static {
    Runtime.getRuntime().addShutdownHook(Thread.ofPlatform().unstarted(() -> {
      try {
        reader.getHistory().save();
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }));
  }

  static String readline(String prompt) {
    return reader.readLine(prompt);
  }
}
