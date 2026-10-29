/*
 * Copyright (c) 2024-2025, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package mal;

import static mal.MalNode.string;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Set;

import org.jline.reader.Candidate;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.reader.impl.DefaultHighlighter;
import org.jline.reader.impl.DefaultParser;
import org.jline.reader.impl.history.DefaultHistory;
import org.jline.terminal.Terminal.Signal;

class Readline {

  // the env whose definitions are offered by tab completion, the core namespace until the
  // repl sets its global env
  private static Env env = Env.DEFAULT;

  private static final LineReader reader = LineReaderBuilder.builder()
    .variable(LineReader.HISTORY_FILE, System.getProperty("user.home") + "/.malhistory")
    .variable(LineReader.HISTORY_FILE_SIZE, 1000)
    .option(LineReader.Option.DISABLE_EVENT_EXPANSION, true)
    .history(new DefaultHistory())
    // the names are read on every tab, so new definitions are completed too
    .completer((_, _, candidates) -> completions().forEach(name -> candidates.add(new Candidate(name))))
    .highlighter(new DefaultHighlighter())
    .parser(new MalParser())
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
      lineCommentDelims(new String[] { ";" });
    }

    @Override
    public boolean isDelimiterChar(CharSequence buffer, int pos) {
      return DELIMITERS.indexOf(buffer.charAt(pos)) >= 0 || super.isDelimiterChar(buffer, pos);
    }
  }

  // everything defined in the env, including the core namespace, plus the special forms
  private static Set<String> completions() {
    var names = env.names();
    names.addAll(Evaluator.specialForms());
    // constants and the catch* part of try* are not defined anywhere
    names.addAll(List.of("nil", "true", "false", "catch*"));
    return names;
  }

  // ctrl-c while reading a line is handled by jline, this handler runs when ctrl-c
  // is pressed during an evaluation
  static void onInterrupt(Runnable handler) {
    reader.getTerminal().handle(Signal.INT, _ -> handler.run());
  }

  static void completeFrom(Env env) {
    Readline.env = env;
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
