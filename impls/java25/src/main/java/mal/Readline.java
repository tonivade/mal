/*
 * Copyright (c) 2024-2025, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package mal;

import static mal.MalNode.string;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
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
    .completer(new StringsCompleter(completions()))
    .highlighter(new DefaultHighlighter())
    .parser(new MalParser().lineCommentDelims(new String[] { ";" }))
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

  // jline only splits words on whitespace, so "(lo" would be completed as a single word;
  // brackets and reader macros are delimiters too, then "(lo" completes "lo"
  private static final class MalParser extends DefaultParser {

    private static final String DELIMITERS = "()[]{}'`~@^,";

    MalParser() {
      // in mal ' is the quote reader macro, only " starts a string
      quoteChars(new char[] { '"' });
    }

    @Override
    public boolean isDelimiterChar(CharSequence buffer, int pos) {
      return DELIMITERS.indexOf(buffer.charAt(pos)) >= 0 || super.isDelimiterChar(buffer, pos);
    }
  }

  // generated from the core namespace and the special forms, so it can't get out of sync
  private static List<String> completions() {
    var names = new ArrayList<String>();
    names.addAll(Core.NS.keySet());
    names.addAll(Evaluator.specialForms());
    // constants, the catch* part of try*, and what the steps define in mal or in their own env
    names.addAll(List.of("nil", "true", "false", "catch*", "eval", "not", "load-file", "cond",
      "*ARGV*", "*host-language*"));
    names.sort(null);
    return names;
  }

  static String readline(String prompt) {
    return reader.readLine(prompt);
  }

  static MalNode safeReadline(String prompt) {
    try {
      return string(reader.readLine(prompt));
    } catch (UserInterruptException | EndOfFileException e) {
      return MalNode.NIL;
    }
  }
}
