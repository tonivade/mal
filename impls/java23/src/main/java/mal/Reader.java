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
import static mal.Mal.map;
import static mal.Mal.number;
import static mal.Mal.string;
import static mal.Mal.symbol;
import static mal.Mal.vector;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class Reader {

  private static final Pattern PATTERN = 
    Pattern.compile("[\\s,]*(~@|[\\[\\]{}()'`~^@]|\"(?:\\\\.|[^\\\\\"])*\"?|;.*|[^\\s\\[\\]{}('\"`,;)]*)");

  private final List<String> tokens;
  private int position;

  public Reader(List<String> tokens) {
    this.tokens = tokens;
  }

  public String next() {
    if (position >= tokens.size()) {
      return null;
    }
    return tokens.get(position++);
  }

  public String peek() {
    if (position >= tokens.size()) {
      return null;
    }
    return tokens.get(position);
  }

  public boolean isEmpty() {
    return tokens.isEmpty();
  }

  public static Mal read(String input) {
    return parse(tokenize(input));
  }

  private static Mal parse(Reader reader) {
    String token = reader.peek();

    if (token == null || token.isBlank()) {
      return Mal.NIL;
    }

    return switch (token.charAt(0)) {

      case '\'' -> {
        reader.next();
        yield list(QUOTE, parse(reader));
      }

      case '`' -> {
        reader.next();
        yield list(QUASIQUOTE, parse(reader));
      }

      case '~' -> {
        reader.next();
        if (token.equals("~")) {
          yield list(UNQUOTE, parse(reader));
        }
        yield list(SPLICE_UNQUOTE, parse(reader));
      }

      case '^' -> {
        reader.next();
        var meta = parse(reader);
        yield list(WITH_META, parse(reader), meta);
      }

      case '@' -> {
        reader.next();
        yield list(DEREF, parse(reader));
      }

      case '(' -> readList(reader);
      case '[' -> readVector(reader);
      case '{' -> readMap(reader);

      case ')' -> throw new IllegalArgumentException("unexpected token");
      case ']' -> throw new IllegalArgumentException("unexpected token");
      case '}' -> throw new IllegalArgumentException("unexpected token");

      default -> readAtom(reader);
    };
  }

  private static Mal readList(Reader reader) {
    return list(readList(reader, '(', ')'));
  }

  private static Mal readVector(Reader reader) {
    return vector(readList(reader, '[', ']'));
  }

  private static Mal readMap(Reader reader) {
    return map(readList(reader, '{', '}'));
  }

  private static List<Mal> readList(Reader reader, char start, char end) {
    var list = new ArrayList<Mal>();
    String token = reader.next();
    if (token.charAt(0) != start) {
        throw new IllegalStateException("expected '" + start + "'");
    }

    while ((token = reader.peek()) != null && token.charAt(0) != end) {
        list.add(parse(reader));
    }

    if (token == null) {
        throw new IllegalStateException("EOF");
    }
    reader.next();

    return list;
  }

  private static Mal readAtom(Reader reader) {
    var token = reader.next();
    if (token == null) {
      return NIL;
    }
    if (Character.isDigit(token.charAt(0))) {
      return number(Integer.parseInt(token));
    }
    if (token.length() > 1 && token.charAt(0) == '-' && Character.isDigit(token.charAt(1))) {
      return number(Integer.parseInt(token));
    }
    if (token.equals("nil")) {
      return NIL;
    } 
    if (token.equals("true")) {
      return TRUE;
    } 
    if (token.equals("false")) {
      return FALSE;
    } 
    if (token.equals("\"")) {
      throw new IllegalStateException("EOF");
    } 
    if (token.startsWith("\"") && !token.endsWith("\"")) {
      throw new IllegalStateException("EOF");
    } 
    if (token.startsWith("\"") && token.endsWith("\"")) {
      return string(token.substring(1, token.length() - 1));
    } 
    if (token.charAt(0) == ':') {
      return keyword(token.substring(1));
    } 
    return symbol(token);
  }

  private static Reader tokenize(String input) {
    return PATTERN.matcher(input).results()
      .map(r -> r.group(1))
      .filter(not(Reader::isEmpty))
      .filter(not(Reader::isComment))
      .collect(collectingAndThen(toUnmodifiableList(), Reader::new));
  }

  private static boolean isEmpty(String token) {
    return token == null || token.isEmpty();
  }

  private static boolean isComment(String token) {
    return token.charAt(0) == ';';
  }
}
