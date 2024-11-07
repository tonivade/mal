package mal;

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
import static org.apache.commons.text.StringEscapeUtils.unescapeJava;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class Reader {

  private final List<String> tokens;
  private int position;

  public Reader(List<String> tokens) {
    this.tokens = tokens;
  }

  public String next() {
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

    if (token == null) {
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
        throw new IllegalStateException("expected '" + end + "', got EOF");
    }
    reader.next();

    return list;
  }

  private static Mal readAtom(Reader reader) {
    var token = reader.next();
    var pattern = Pattern.compile("(^-?[0-9]+$)|(^-?[0-9][0-9.]*$)|(^nil$)|(^true$)|(^false$)|^\"((?:[\\\\].|[^\\\\\"])*)\"$|^\"(.*)$|:(.*)|(^[^\"]*$)");
    var matcher = pattern.matcher(token);
    if (!matcher.find()) {
      throw new IllegalStateException("unrecognized token '" + token + "'");
    }
    if (matcher.group(1) != null) {
      return number(Integer.parseInt(matcher.group(1)));
    } else if (matcher.group(3) != null) {
      return NIL;
    } else if (matcher.group(4) != null) {
      return TRUE;
    } else if (matcher.group(5) != null) {
      return FALSE;
    } else if (matcher.group(6) != null) {
      return string(unescapeJava(matcher.group(6)));
    } else if (matcher.group(7) != null) {
      throw new IllegalStateException("expected '\"', got EOF");
    } else if (matcher.group(8) != null) {
      return keyword(matcher.group(8));
    } else if (matcher.group(9) != null) {
      return symbol(matcher.group(9));
    }
    throw new IllegalStateException("unrecognized '" + matcher.group(0) + "'");
  }

  private static Reader tokenize(String input) {
    var tokens = new ArrayList<String>();
    var pattern = Pattern.compile("[\\s,]*(~@|[\\[\\]{}()'`~^@]|\"(?:\\\\.|[^\\\\\"])*\"?|;.*|[^\\s\\[\\]{}('\"`,;)]*)");
    var matcher = pattern.matcher(input);
    while (matcher.find()) {
      var token = matcher.group(1);
      if (!isEmpty(token) && !isComment(token)) {
        tokens.add(token);
      }
    }
    return new Reader(tokens);
  }

  private static boolean isEmpty(String token) {
    return token == null || token.isEmpty();
  }

  private static boolean isComment(String token) {
    return token.charAt(0) == ';';
  }
}
