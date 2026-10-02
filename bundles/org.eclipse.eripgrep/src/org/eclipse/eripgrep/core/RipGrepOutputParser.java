package org.eclipse.eripgrep.core;

import static org.eclipse.eripgrep.core.Json.*;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Parses the lines written by <code>rg --json</code>.
 */
public final class RipGrepOutputParser {

  private RipGrepOutputParser() {
  }

  /**
   * @throws IllegalArgumentException if the line is not a JSON message
   */
  public static void parse(String jsonLine, RipGrepOutputHandler handler) {
    Map<String, Object> message = object(Json.parse(jsonLine));
    Map<String, Object> data = object(message.get("data"));
    switch (string(message.get("type"), "")) {
    case "begin" -> handler.begin(text(data.get("path")));
    case "end" -> handler.end(text(data.get("path")));
    case "match", "context" -> parseLine(data, handler);
    case "summary" -> handler.summary(number(object(data.get("stats")).get("searches"), 0));
    default -> {
      // an unknown message of a newer RipGrep
    }
    }
  }

  private static void parseLine(Map<String, Object> data, RipGrepOutputHandler handler) {
    Object lines = data.get("lines");
    String text = text(lines);
    List<Object> submatches = array(data.get("submatches"));
    int[] byteOffsets = new int[submatches.size() * 2];
    List<String> replacements = new ArrayList<>();
    for (int i = 0; i < submatches.size(); i++) {
      Map<String, Object> submatch = object(submatches.get(i));
      byteOffsets[2 * i] = (int) number(submatch.get("start"), 0);
      byteOffsets[2 * i + 1] = (int) number(submatch.get("end"), 0);
      if (submatch.containsKey("replacement")) {
        replacements.add(text(submatch.get("replacement")));
      }
    }
    int[] charOffsets;
    if (object(lines).containsKey("text")) {
      charOffsets = TextOffsets.charOffsets(text, byteOffsets);
    } else {
      // not valid UTF-8: the text is an approximation, so are the offsets
      byte[] bytes = bytes(lines);
      charOffsets = new int[byteOffsets.length];
      for (int i = 0; i < byteOffsets.length; i++) {
        charOffsets[i] = new String(bytes, 0, Math.min(byteOffsets[i], bytes.length), StandardCharsets.UTF_8).length();
      }
    }
    int length = text.length();
    if (text.endsWith("\r\n")) {
      length -= 2;
    } else if (text.endsWith("\n")) {
      length--;
    }
    List<Span> spans = new ArrayList<>();
    for (int i = 0; i < submatches.size(); i++) {
      spans.add(new Span(Math.min(charOffsets[2 * i], length), Math.min(charOffsets[2 * i + 1], length)));
    }
    if (replacements.size() != spans.size()) {
      replacements.clear();
    }
    handler.line(text(data.get("path")), number(data.get("line_number"), 0), text.substring(0, length), spans, replacements);
  }

  /**
   * RipGrep writes <code>{"text": "..."}</code>, or <code>{"bytes": "base64"}</code> when it is not valid UTF-8.
   */
  private static String text(Object value) {
    Map<String, Object> object = object(value);
    if (object.containsKey("text")) {
      return string(object.get("text"), "");
    }
    return new String(bytes(value), StandardCharsets.UTF_8);
  }

  private static byte[] bytes(Object value) {
    try {
      return Base64.getDecoder().decode(string(object(value).get("bytes"), ""));
    } catch (IllegalArgumentException e) {
      return new byte[0];
    }
  }
}
