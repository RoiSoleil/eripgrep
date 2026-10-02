package org.eclipse.eripgrep.core;

import java.util.*;

/**
 * A minimal JSON reader and writer, enough for the output of <code>rg --json</code> and for the search history.
 * <p>
 * Objects are {@link Map}s, arrays are {@link List}s, numbers are {@link Long}s or {@link Double}s.
 */
public final class Json {

  private final String text;
  private int position;

  private Json(String text) {
    this.text = text;
  }

  public static Object parse(String text) {
    Json json = new Json(text);
    Object value = json.readValue();
    json.skipWhitespaces();
    if (json.position != text.length()) {
      throw json.error("Unexpected trailing characters");
    }
    return value;
  }

  @SuppressWarnings("unchecked")
  public static Map<String, Object> object(Object value) {
    return value instanceof Map ? (Map<String, Object>) value : Map.of();
  }

  @SuppressWarnings("unchecked")
  public static List<Object> array(Object value) {
    return value instanceof List ? (List<Object>) value : List.of();
  }

  public static String string(Object value, String defaultValue) {
    return value instanceof String string ? string : defaultValue;
  }

  public static long number(Object value, long defaultValue) {
    return value instanceof Number number ? number.longValue() : defaultValue;
  }

  public static boolean bool(Object value, boolean defaultValue) {
    return value instanceof Boolean bool ? bool : defaultValue;
  }

  private Object readValue() {
    skipWhitespaces();
    if (position >= text.length()) {
      throw error("Unexpected end");
    }
    char c = text.charAt(position);
    switch (c) {
    case '{':
      return readObject();
    case '[':
      return readArray();
    case '"':
      return readString();
    case 't':
      return readLiteral("true", Boolean.TRUE);
    case 'f':
      return readLiteral("false", Boolean.FALSE);
    case 'n':
      return readLiteral("null", null);
    default:
      return readNumber();
    }
  }

  private Map<String, Object> readObject() {
    Map<String, Object> object = new LinkedHashMap<>();
    position++;
    skipWhitespaces();
    if (peek() == '}') {
      position++;
      return object;
    }
    while (true) {
      skipWhitespaces();
      if (peek() != '"') {
        throw error("Expected a string");
      }
      String key = readString();
      skipWhitespaces();
      expect(':');
      object.put(key, readValue());
      skipWhitespaces();
      if (peek() == ',') {
        position++;
      } else {
        expect('}');
        return object;
      }
    }
  }

  private List<Object> readArray() {
    List<Object> array = new ArrayList<>();
    position++;
    skipWhitespaces();
    if (peek() == ']') {
      position++;
      return array;
    }
    while (true) {
      array.add(readValue());
      skipWhitespaces();
      if (peek() == ',') {
        position++;
      } else {
        expect(']');
        return array;
      }
    }
  }

  private String readString() {
    position++;
    StringBuilder builder = new StringBuilder();
    while (true) {
      if (position >= text.length()) {
        throw error("Unterminated string");
      }
      char c = text.charAt(position++);
      if (c == '"') {
        return builder.toString();
      } else if (c != '\\') {
        builder.append(c);
      } else {
        if (position >= text.length()) {
          throw error("Unterminated string");
        }
        char escaped = text.charAt(position++);
        switch (escaped) {
        case 'n' -> builder.append('\n');
        case 't' -> builder.append('\t');
        case 'r' -> builder.append('\r');
        case 'b' -> builder.append('\b');
        case 'f' -> builder.append('\f');
        case 'u' -> {
          if (position + 4 > text.length()) {
            throw error("Invalid unicode escape");
          }
          try {
            builder.append((char) Integer.parseInt(text.substring(position, position + 4), 16));
          } catch (NumberFormatException e) {
            throw error("Invalid unicode escape");
          }
          position += 4;
        }
        case '"', '\\', '/' -> builder.append(escaped);
        default -> throw error("Invalid escape");
        }
      }
    }
  }

  private Object readLiteral(String literal, Object value) {
    if (!text.startsWith(literal, position)) {
      throw error("Unexpected character");
    }
    position += literal.length();
    return value;
  }

  private Number readNumber() {
    int start = position;
    while (position < text.length() && "+-0123456789.eE".indexOf(text.charAt(position)) >= 0) {
      position++;
    }
    String number = text.substring(start, position);
    try {
      if (number.indexOf('.') < 0 && number.indexOf('e') < 0 && number.indexOf('E') < 0) {
        return Long.valueOf(number);
      }
      return Double.valueOf(number);
    } catch (NumberFormatException e) {
      position = start;
      throw error("Unexpected character");
    }
  }

  private void skipWhitespaces() {
    while (position < text.length() && Character.isWhitespace(text.charAt(position))) {
      position++;
    }
  }

  private char peek() {
    return position < text.length() ? text.charAt(position) : 0;
  }

  private void expect(char c) {
    if (peek() != c) {
      throw error("Expected '" + c + "'");
    }
    position++;
  }

  private IllegalArgumentException error(String message) {
    return new IllegalArgumentException(message + " at position " + position);
  }

  public static String write(Object value) {
    StringBuilder builder = new StringBuilder();
    write(value, builder);
    return builder.toString();
  }

  private static void write(Object value, StringBuilder builder) {
    if (value instanceof Map<?, ?> map) {
      builder.append('{');
      boolean first = true;
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        if (!first) {
          builder.append(',');
        }
        first = false;
        writeString(String.valueOf(entry.getKey()), builder);
        builder.append(':');
        write(entry.getValue(), builder);
      }
      builder.append('}');
    } else if (value instanceof Collection<?> collection) {
      builder.append('[');
      boolean first = true;
      for (Object element : collection) {
        if (!first) {
          builder.append(',');
        }
        first = false;
        write(element, builder);
      }
      builder.append(']');
    } else if (value instanceof Number || value instanceof Boolean || value == null) {
      builder.append(value);
    } else {
      writeString(value.toString(), builder);
    }
  }

  private static void writeString(String string, StringBuilder builder) {
    builder.append('"');
    for (int i = 0; i < string.length(); i++) {
      char c = string.charAt(i);
      switch (c) {
      case '"' -> builder.append("\\\"");
      case '\\' -> builder.append("\\\\");
      case '\n' -> builder.append("\\n");
      case '\r' -> builder.append("\\r");
      case '\t' -> builder.append("\\t");
      default -> {
        if (c < 0x20) {
          builder.append(String.format("\\u%04x", (int) c));
        } else {
          builder.append(c);
        }
      }
      }
    }
    builder.append('"');
  }
}
