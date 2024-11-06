package mal;

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

  public static Mal parse(String input) {
    return read(tokenize(input));
  }

  private static Mal read(Reader reader) {
    String token = reader.peek();

    return switch (token.charAt(0)) {

      case '\'' -> {
        reader.next();
        yield Mal.list(Mal.QUOTE, read(reader));
      }

      case '`' -> {
        reader.next();
        yield Mal.list(Mal.QUASIQUOTE, read(reader));
      }

      case '~' -> {
        reader.next();
        if (token.equals("~")) {
          yield Mal.list(Mal.UNQUOTE, read(reader));
        }
        yield Mal.list(Mal.SPLICE_UNQUOTE, read(reader));
      }

      case '^' -> {
        var meta = read(reader);
        yield Mal.list(Mal.WITH_META, read(reader), meta);
      }

      case '@' -> Mal.list(Mal.DEREF, read(reader));

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
    return Mal.list(readList(reader, '(', ')'));
  }

  private static Mal readVector(Reader reader) {
    return Mal.vector(readList(reader, '[', ']'));
  }

  private static Mal readMap(Reader reader) {
    return Mal.map(readList(reader, '{', '}'));
  }

  private static List<Mal> readList(Reader reader, char start, char end) {
    var list = new ArrayList<Mal>();
    String token = reader.next();
    if (token.charAt(0) != start) {
        throw new IllegalStateException("expected '" + start + "'");
    }

    while ((token = reader.peek()) != null && token.charAt(0) != end) {
        list.add(read(reader));
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
      return Mal.number(Integer.parseInt(matcher.group(1)));
    } else if (matcher.group(3) != null) {
      return Mal.NIL;
    } else if (matcher.group(4) != null) {
      return Mal.TRUE;
    } else if (matcher.group(5) != null) {
      return Mal.FALSE;
    } else if (matcher.group(6) != null) {
      return Mal.string(unescapeJava(matcher.group(6)));
    } else if (matcher.group(7) != null) {
      throw new IllegalStateException("expected '\"', got EOF");
    } else if (matcher.group(8) != null) {
      return Mal.string("\u029e" + matcher.group(8));
    } else if (matcher.group(9) != null) {
      return Mal.symbol(matcher.group(9));
    }
    throw new IllegalStateException("unrecognized '" + matcher.group(0) + "'");
  }

  private static Reader tokenize(String input) {
    var tokens = new ArrayList<String>();
    var pattern = Pattern.compile("[\\s ,]*(~@|[\\[\\]{}()'`~@]|\"(?:[\\\\].|[^\\\\\"])*\"?|;.*|[^\\s \\[\\]{}()'\"`~@,;]*)");
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
