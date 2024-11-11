package mal;

import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.toUnmodifiableList;
import static mal.Mal.DEREF;
import static mal.Mal.FALSE;
import static mal.Mal.NIL;
import static mal.Mal.QUASIQUOTE;
import static mal.Mal.QUOTE;
import static mal.Mal.SPLICE_UNQUOTE;
import static mal.Mal.TRUE;
import static mal.Mal.UNQUOTE;
import static mal.Mal.WITH_META;
import static mal.Mal.keyword;
import static mal.Mal.list;
import static mal.Mal.number;
import static mal.Mal.string;
import static mal.Mal.symbol;
import static mal.Trampoline.done;
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

  public static Mal read(String input) {
    return parse(tokenize(input)).run();
  }

  private static Trampoline<Mal> parse(Reader reader) {
    return switch (reader.peek()) {
      case null -> Trampoline.done(Mal.NIL);

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

      case Token(var value) when value.charAt(0) == ')' -> throw new IllegalArgumentException("unexpected token");
      case Token(var value) when value.charAt(0) == ']' -> throw new IllegalArgumentException("unexpected token");
      case Token(var value) when value.charAt(0) == '}' -> throw new IllegalArgumentException("unexpected token");

      default -> done(readAtom(reader));
    };
  }

  private static Trampoline<Mal> readList(Reader reader) {
    return readList(reader, '(', ')').map(Mal::list);
  }

  private static Trampoline<Mal> readVector(Reader reader) {
    return readList(reader, '[', ']').map(Mal::vector);
  }

  private static Trampoline<Mal> readMap(Reader reader) {
    return readList(reader, '{', '}').map(Mal::map);
  }

  private static Trampoline<List<Mal>> readList(Reader reader, char start, char end) {
    var list = new ArrayList<Trampoline<Mal>>();
    var token = reader.next();
    if (token.value().charAt(0) != start) {
      throw new IllegalStateException("expected '" + start + "'");
    }

    while ((token = reader.peek()) != null && token.value().charAt(0) != end) {
      list.add(parse(reader));
    }

    if (token == null) {
      throw new IllegalStateException("EOF");
    }
    reader.next();

    return traverse(list);
  }

  private static Mal readAtom(Reader reader) {
    return switch (reader.next()) {
      case null -> NIL;
      case Token(var value) when value.equals("nil") -> NIL;
      case Token(var value) when value.equals("true") -> TRUE;
      case Token(var value) when value.equals("false") -> FALSE;
      case Token(var value) when value.matches("-?\\d+") -> 
        number(Integer.parseInt(value));
      case Token(var value) when value.matches("\"(?:\\\\.|[^\\\\\"])*") -> 
        throw new IllegalStateException("EOF");
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
