package org.eclipse.eripgrep.core;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

class LineExcerptTest {

  @Test
  void removesIndentation() {
    LineExcerpt excerpt = LineExcerpt.of("\t  int foo = 1;  ", List.of(new Span(7, 10)), 100);
    assertEquals("int foo = 1;", excerpt.text());
    assertEquals(List.of(new Span(4, 7)), excerpt.spans());
  }

  @Test
  void replacesControlCharacters() {
    LineExcerpt excerpt = LineExcerpt.of("a\tb\r\nc", List.of(new Span(2, 6)), 100);
    assertEquals("a b  c", excerpt.text());
    assertEquals(List.of(new Span(2, 6)), excerpt.spans());
  }

  @Test
  void shortensLongLines() {
    String line = "x".repeat(500);
    LineExcerpt excerpt = LineExcerpt.of(line, List.of(new Span(2, 4), new Span(300, 310)), 100);
    assertEquals("x".repeat(100) + "\u2026", excerpt.text());
    assertEquals(List.of(new Span(2, 4)), excerpt.spans());
  }

  @Test
  void showsTheFirstMatchOfALongLine() {
    String line = "a".repeat(300) + "MATCH" + "b".repeat(300);
    LineExcerpt excerpt = LineExcerpt.of(line, List.of(new Span(300, 305)), 100);
    assertEquals(102, excerpt.text().length());
    assertTrue(excerpt.text().startsWith("\u2026a"));
    assertTrue(excerpt.text().endsWith("b\u2026"));
    Span span = excerpt.spans().get(0);
    assertEquals("MATCH", excerpt.text().substring(span.start(), span.end()));
  }

  @Test
  void showsTheEndOfALongLine() {
    String line = "a".repeat(300) + "MATCH";
    LineExcerpt excerpt = LineExcerpt.of(line, List.of(new Span(300, 305)), 100);
    assertEquals("\u2026" + "a".repeat(95) + "MATCH", excerpt.text());
    assertEquals(List.of(new Span(96, 101)), excerpt.spans());
  }

  @Test
  void clipsAMatchCutByTheEnd() {
    LineExcerpt excerpt = LineExcerpt.of("ab" + "c".repeat(200), List.of(new Span(1, 150)), 100);
    assertEquals(List.of(new Span(1, 100)), excerpt.spans());
  }

  @Test
  void blankLine() {
    LineExcerpt excerpt = LineExcerpt.of("   ", List.of(), 100);
    assertEquals("", excerpt.text());
    assertTrue(excerpt.spans().isEmpty());
  }
}
