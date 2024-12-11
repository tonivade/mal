/*
 * Copyright (c) 2024, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
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
import static mal.MalNode.list;
import static mal.MalNode.number;
import static mal.MalNode.string;
import static mal.MalNode.symbol;
import static mal.Trampoline.done;
import static mal.Trampoline.more;
import static mal.Trampoline.traverse;
import static org.apache.commons.text.StringEscapeUtils.unescapeJava;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class Reader {

  record Token(String value) { }

  private static final Pattern PATTERN =
    Pattern.compile("[\\s,]*(~@|[\\[\\]{}()'`~^@]|\"(?:\\\\.|[^\\\\\"])*\"?|;.*|[^\\s\\[\\]{}('\"`,;)]*)");

  private final List<Token> tokens;
  private int position;

  public Reader(List<Token> tokens) {
    this.tokens = tokens;
  }

  public Token next() {
    if (position >= tokens.size()) {
      return null;
    }
    return tokens.get(position++);
  }

  public Token peek() {
    if (position >= tokens.size()) {
      return null;
    }
    return tokens.get(position);
  }

  public boolean isEmpty() {
    return tokens.isEmpty();
  }

  public static MalNode read(String input) {
    return parse(tokenize(input)).run();
  }

  private static Trampoline<MalNode> parse(Reader reader) {
    return switch (reader.peek()) {
      case null -> done(NIL);

      case Token(var value) when value.charAt(0) == '\'' -> {
        reader.next();
        yield parse(reader).map(next -> list(QUOTE, next));
      }

      case Token(var value) when value.charAt(0) == '`' -> {
        reader.next();
        yield parse(reader).map(next -> list(QUASIQUOTE, next));
      }

      case Token(var value) when value.equals("~") -> {
        reader.next();
        yield parse(reader).map(next -> list(UNQUOTE, next));
      }

      case Token(var value) when value.charAt(0) == '~' -> {
        reader.next();
        yield parse(reader).map(next -> list(SPLICE_UNQUOTE, next));
      }

      case Token(var value) when value.charAt(0) == '^' -> {
        reader.next();
        yield Trampoline.map2(parse(reader), parse(reader), (meta, next) -> {
          return list(WITH_META, next, meta);
        });
      }

      case Token(var value) when value.charAt(0) == '@' -> {
        reader.next();
        yield parse(reader).map(next -> list(DEREF, next));
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
    return more(() -> {
      var list = new ArrayList<Trampoline<MalNode>>();
      var token = reader.next();
      if (token.value().charAt(0) != start) {
        throw new MalException("expected '" + start + "'");
      }

      while ((token = reader.peek()) != null && token.value().charAt(0) != end) {
        list.add(parse(reader));
      }

      if (token == null) {
        throw new MalException("EOF");
      }
      reader.next();

      return traverse(list);
    });
  }

  private static MalNode readAtom(Reader reader) {
    return switch (reader.next()) {
      case null -> NIL;
      case Token(var value) when value.equals("nil") -> NIL;
      case Token(var value) when value.equals("true") -> TRUE;
      case Token(var value) when value.equals("false") -> FALSE;
      case Token(var value) when value.matches("-?\\d+") ->
        number(Integer.parseInt(value));
      case Token(var value) when value.matches("\"(?:\\\\.|[^\\\\\"])*") ->
        throw new MalException("EOF");
      case Token(var value) when value.matches("\"(?:\\\\.|[^\\\\\"])*\"") ->
        string(unescapeJava(value.substring(1, value.length() - 1)));
      case Token(var value) when value.startsWith(":") ->
        keyword(value.substring(1));
      case Token(var value) -> symbol(value);
      default -> null;
    };
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
