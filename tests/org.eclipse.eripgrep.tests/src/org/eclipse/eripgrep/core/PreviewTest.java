package org.eclipse.eripgrep.core;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

import org.eclipse.eripgrep.core.Preview.Kind;
import org.eclipse.eripgrep.core.Preview.Range;
import org.junit.jupiter.api.Test;

class PreviewTest {

  private static List<String> lines(int count) {
    List<String> lines = new ArrayList<>();
    for (int i = 1; i <= count; i++) {
      lines.add("line " + i);
    }
    return lines;
  }

  private static List<String> texts(Preview preview, Kind kind) {
    return preview.ranges().stream().filter(range -> range.kind() == kind)
        .map(range -> preview.text().substring(range.start(), range.start() + range.length())).toList();
  }

  @Test
  void showsTheLinesAround() {
    Preview preview = Preview.of(lines(20), 10, 1, 2, Map.of(10L, List.of(new Span(0, 4)), 12L, List.of(new Span(5, 7)), 1L, List.of(new Span(0, 1))));
    assertEquals(" 8  line 8\n 9  line 9\n10  line 10\n11  line 11\n12  line 12", preview.text());
    assertEquals(List.of(" 8  ", " 9  ", "10  ", "11  ", "12  "), texts(preview, Kind.LINE_NUMBER));
    assertEquals(List.of("line 10"), texts(preview, Kind.CURRENT_LINE));
    assertEquals(Set.of("line", "12"), Set.copyOf(texts(preview, Kind.MATCH)));
  }

  @Test
  void stopsAtTheLimitsOfTheFile() {
    assertEquals("1  line 1\n2  line 2", Preview.of(lines(2), 1, 1, 5, Map.of()).text());
    assertEquals("1  line 1\n2  line 2", Preview.of(lines(2), 2, 1, 5, Map.of()).text());
  }

  @Test
  void highlightsAMultilineMatch() {
    List<String> lines = List.of("a {", "  b", "} c");
    // "{\n  b\n}" from the offset 2 of the first line
    Preview preview = Preview.of(lines, 1, 3, 0, Map.of(1L, List.of(new Span(2, 9))));
    assertEquals(List.of("{", "  b", "}"), texts(preview, Kind.MATCH));
    assertEquals(List.of("a {", "  b", "} c"), texts(preview, Kind.CURRENT_LINE));
  }

  @Test
  void ignoresAMatchAfterTheEndOfItsLine() {
    Preview preview = Preview.of(List.of("ab"), 1, 1, 0, Map.of(1L, List.of(new Span(5, 8))));
    assertEquals(List.of(), preview.ranges().stream().filter(range -> range.kind() == Kind.MATCH).toList());
    assertEquals(new Range(Kind.LINE_NUMBER, 0, 3), preview.ranges().get(0));
  }
}
