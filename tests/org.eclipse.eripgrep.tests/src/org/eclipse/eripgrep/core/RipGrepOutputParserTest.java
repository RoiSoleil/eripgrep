package org.eclipse.eripgrep.core;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.*;

import org.junit.jupiter.api.Test;

class RipGrepOutputParserTest {

  private final List<String> events = new ArrayList<>();

  private final RipGrepOutputHandler handler = new RipGrepOutputHandler() {

    @Override
    public void begin(String path) {
      events.add("begin " + path);
    }

    @Override
    public void line(String path, long lineNumber, String text, List<Span> spans, List<String> replacements) {
      events.add("line " + path + " " + lineNumber + " [" + text + "] " + spans + " " + replacements);
    }

    @Override
    public void end(String path) {
      events.add("end " + path);
    }

    @Override
    public void summary(long searchedFiles) {
      events.add("summary " + searchedFiles);
    }
  };

  private void parse(String line) {
    RipGrepOutputParser.parse(line, handler);
  }

  @Test
  void parsesASearch() {
    parse("{\"type\":\"begin\",\"data\":{\"path\":{\"text\":\"t/a.txt\"}}}");
    parse("{\"type\":\"context\",\"data\":{\"path\":{\"text\":\"t/a.txt\"},\"lines\":{\"text\":\"before\\n\"},\"line_number\":1,\"absolute_offset\":0,\"submatches\":[]}}");
    parse("{\"type\":\"match\",\"data\":{\"path\":{\"text\":\"t/a.txt\"},\"lines\":{\"text\":\"foo h\u00e9llo bar hello\\n\"},\"line_number\":2,\"absolute_offset\":12,"
        + "\"submatches\":[{\"match\":{\"text\":\"h\u00e9llo\"},\"start\":4,\"end\":10},{\"match\":{\"text\":\"hello\"},\"start\":15,\"end\":20}]}}");
    parse("{\"type\":\"end\",\"data\":{\"path\":{\"text\":\"t/a.txt\"},\"binary_offset\":null,\"stats\":{\"matches\":2}}}");
    parse("{\"data\":{\"elapsed_total\":{\"human\":\"0.06s\",\"nanos\":62000841,\"secs\":0},\"stats\":{\"matched_lines\":1,\"matches\":2,\"searches\":42,\"searches_with_match\":1}},\"type\":\"summary\"}");
    assertEquals(List.of(
        "begin t/a.txt",
        "line t/a.txt 1 [before] [] []",
        "line t/a.txt 2 [foo h\u00e9llo bar hello] [Span[start=4, end=9], Span[start=14, end=19]] []",
        "end t/a.txt",
        "summary 42"), events);
  }

  @Test
  void parsesReplacements() {
    parse("{\"type\":\"match\",\"data\":{\"path\":{\"text\":\"a\"},\"lines\":{\"text\":\"Hello\\r\\n\"},\"line_number\":3,"
        + "\"submatches\":[{\"match\":{\"text\":\"Hello\"},\"replacement\":{\"text\":\"Bye\"},\"start\":0,\"end\":5}]}}");
    assertEquals(List.of("line a 3 [Hello] [Span[start=0, end=5]] [Bye]"), events);
  }

  @Test
  void keepsTheLinesOfAMultilineMatch() {
    parse("{\"type\":\"match\",\"data\":{\"path\":{\"text\":\"a\"},\"lines\":{\"text\":\"a {\\n}\\n\"},\"line_number\":1,"
        + "\"submatches\":[{\"match\":{\"text\":\"{\\n}\\n\"},\"start\":2,\"end\":6}]}}");
    // the match is cut at the end of the text, which has no line terminator
    assertEquals(List.of("line a 1 [a {\n}] [Span[start=2, end=5]] []"), events);
  }

  @Test
  void decodesTextWhichIsNotUtf8() {
    byte[] bytes = { 'a', (byte) 0xE9, ' ', 'x', 'y', '\n' };
    String base64 = Base64.getEncoder().encodeToString(bytes);
    String path = Base64.getEncoder().encodeToString("d\u00e9j\u00e0".getBytes(StandardCharsets.UTF_8));
    parse("{\"type\":\"match\",\"data\":{\"path\":{\"bytes\":\"" + path + "\"},\"lines\":{\"bytes\":\"" + base64 + "\"},\"line_number\":1,"
        + "\"submatches\":[{\"match\":{\"text\":\"xy\"},\"start\":3,\"end\":5}]}}");
    assertEquals(List.of("line d\u00e9j\u00e0 1 [a\uFFFD xy] [Span[start=3, end=5]] []"), events);
  }

  @Test
  void ignoresInvalidBase64AndUnknownMessages() {
    parse("{\"type\":\"match\",\"data\":{\"path\":{\"bytes\":\"***\"},\"lines\":{\"text\":\"a\"},\"line_number\":1,\"submatches\":[]}}");
    parse("{\"type\":\"future\",\"data\":{}}");
    parse("{}");
    assertEquals(List.of("line  1 [a] [] []"), events);
  }

  @Test
  void rejectsWhatIsNotJson() {
    assertThrows(IllegalArgumentException.class, () -> parse("ripgrep 15.1.0"));
  }
}
