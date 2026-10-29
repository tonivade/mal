/*
 * Copyright (c) 2024-2025, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package mal;

import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.toUnmodifiableList;
import static mal.MalNode.DEREF;
import static mal.MalNode.FALSE;
import static mal.MalNode.NIL;
import static mal.MalNode.QUASIQUOTE;
import static mal.MalNode.QUOTE;
import static mal.MalNode.SPLICE_UNQUOTE;
import static mal.MalNode.TRUE;
import static mal.MalNode.UNQUOTE;
import static mal.MalNode.WITH_META;
import static mal.MalNode.keyword;
import static mal.MalNode.listOf;
import static mal.MalNode.number;
import static mal.MalNode.string;
import static mal.MalNode.symbol;
import static mal.Trampoline.done;
import static mal.Trampoline.more;
import static mal.Trampoline.zip;
import static org.apache.commons.text.StringEscapeUtils.unescapeJava;

import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import org.pcollections.PVector;
import org.pcollections.TreePVector;

class Reader {

  private static final Pattern PATTERN =
      Pattern.compile("[\\s,]*(~@|[\\[\\]{}()'`~^@]|\"(?:\\\\.|[^\\\\\"])*\"?|;.*|[^\\s\\[\\]{}('\"`,;)]*)");

  private static final Predicate<String> NUMBER_PATTERN = Pattern.compile("-?\\d+").asMatchPredicate();
  private static final Predicate<String> EOF_PATTERN = Pattern.compile("\"(?:\\\\.|[^\\\\\"])*").asMatchPredicate();
  private static final Predicate<String> STRING_PATTERN = Pattern.compile("\"(?:\\\\.|[^\\\\\"])*\"").asMatchPredicate();

  record Token(String value) { }

  private final List<Token> tokens;
  private int position;

  Reader(List<Token> tokens) {
    this.tokens = tokens;
  }

  Token next() {
    if (position >= tokens.size()) {
      return null;
    }
    return tokens.get(position++);
  }

  Token peek() {
    if (position >= tokens.size()) {
      return null;
    }
    return tokens.get(position);
  }

  boolean isEmpty() {
    return tokens.isEmpty();
  }

  static MalNode read(String input) {
    return parse(tokenize(input)).run();
  }

  private static Trampoline<MalNode> parse(Reader reader) {
    return switch (reader.peek()) {
      case null -> done(NIL);

      case Token(var value) when value.charAt(0) == '\'' -> {
        yield nextAndParse(reader).map(next -> listOf(QUOTE, next));
      }

      case Token(var value) when value.charAt(0) == '`' -> {
        yield nextAndParse(reader).map(next -> listOf(QUASIQUOTE, next));
      }

      case Token(var value) when value.equals("~") -> {
        yield nextAndParse(reader).map(next -> listOf(UNQUOTE, next));
      }

      case Token(var value) when value.charAt(0) == '~' -> {
        yield nextAndParse(reader).map(next -> listOf(SPLICE_UNQUOTE, next));
      }

      case Token(var value) when value.charAt(0) == '^' -> {
        yield zip(nextAndParse(reader), more(() -> parse(reader)), (meta, next) -> {
          return listOf(WITH_META, next, meta);
        });
      }

      case Token(var value) when value.charAt(0) == '@' -> {
        yield nextAndParse(reader).map(next -> listOf(DEREF, next));
      }

      case Token(var value) when value.charAt(0) == '(' -> readList(reader);
      case Token(var value) when value.charAt(0) == '[' -> readVector(reader);
      case Token(var value) when value.charAt(0) == '{' -> readMap(reader);

      case Token(var value) when value.charAt(0) == ')' -> throw new MalException("unexpected token");
      case Token(var value) when value.charAt(0) == ']' -> throw new MalException("unexpected token");
      case Token(var value) when value.charAt(0) == '}' -> throw new MalException("unexpected token");

      default -> done(readAtom(reader));
    };
  }

  private static Trampoline<MalNode> nextAndParse(Reader reader) {
    return more(() -> {
      reader.next();
      return parse(reader);
    });
  }

  private static Trampoline<MalNode> readList(Reader reader) {
    return readList(reader, '(', ')').map(MalNode::list);
  }

  private static Trampoline<MalNode> readVector(Reader reader) {
    return readList(reader, '[', ']').map(MalNode::vector);
  }

  private static Trampoline<MalNode> readMap(Reader reader) {
    return readList(reader, '{', '}').map(MalNode::map);
  }

  private static Trampoline<List<MalNode>> readList(Reader reader, char start, char end) {
    Token t = reader.next();
    if (t == null || t.value().charAt(0) != start) {
      throw new MalException("expected '" + start + "'");
    }

    return readElements(reader, end, TreePVector.empty());
  }

  // the elements are accumulated in a persistent vector, so each step returns a new one
  // instead of mutating a list shared by all the steps
  private static Trampoline<List<MalNode>> readElements(Reader reader, char end, PVector<MalNode> acc) {
    Token peek = reader.peek();
    if (peek == null) {
      throw new MalException("EOF");
    }
    if (peek.value().charAt(0) == end) {
      reader.next(); // consume end
      return done(acc);
    }

    // parse next element lazily, then continue with it appended to acc
    return more(() -> parse(reader))
      .flatMap(elem -> readElements(reader, end, acc.plus(elem)));
  }

  private static MalNode readAtom(Reader reader) {
    return switch (reader.next()) {
      case null -> NIL;
      case Token(var value) when value.equals("nil") -> NIL;
      case Token(var value) when value.equals("true") -> TRUE;
      case Token(var value) when value.equals("false") -> FALSE;
      case Token(var value) when NUMBER_PATTERN.test(value) -> readNumber(value);
      case Token(var value) when EOF_PATTERN.test(value) ->
        throw new MalException("EOF");
      case Token(var value) when STRING_PATTERN.test(value) -> readString(value);
      case Token(var value) when value.startsWith(":") ->
        keyword(value.substring(1));
      case Token(var value) -> symbol(value);
    };
  }

  private static MalNode readNumber(String value) {
    try {
      return number(Long.parseLong(value));
    } catch (NumberFormatException e) {
      throw new MalException("number out of range: " + value, e);
    }
  }

  private static MalNode readString(String value) {
    try {
      return string(unescapeJava(value.substring(1, value.length() - 1)));
    } catch (IllegalArgumentException e) {
      throw new MalException("invalid escape in string: " + value, e);
    }
  }

  private static Reader tokenize(String input) {
    return PATTERN.matcher(input).results()
      .map(r -> r.group(1))
      .filter(not(Reader::isEmpty))
      .filter(not(Reader::isComment))
      .map(Token::new)
      .collect(collectingAndThen(toUnmodifiableList(), Reader::new));
  }

  private static boolean isEmpty(String token) {
    return token == null || token.isEmpty();
  }

  private static boolean isComment(String token) {
    return token.charAt(0) == ';';
  }
}
