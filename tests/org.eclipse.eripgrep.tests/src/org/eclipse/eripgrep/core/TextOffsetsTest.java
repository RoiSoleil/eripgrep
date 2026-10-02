package org.eclipse.eripgrep.core;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class TextOffsetsTest {

  @Test
  void findsLines() {
    String content = "one\ntwo\r\n\nfour";
    assertEquals(0, TextOffsets.lineOffset(content, 1));
    assertEquals(4, TextOffsets.lineOffset(content, 2));
    assertEquals(9, TextOffsets.lineOffset(content, 3));
    assertEquals(10, TextOffsets.lineOffset(content, 4));
    assertEquals(-1, TextOffsets.lineOffset(content, 5));
    assertEquals(0, TextOffsets.lineOffset("", 1));
  }

  @Test
  void carriageReturnAloneIsNotALineOfRipGrep() {
    assertEquals(-1, TextOffsets.lineOffset("one\rtwo", 2));
  }

  @Test
  void convertsByteOffsets() {
    // 1, 2, 3 and 4 bytes in UTF-8
    String text = "a\u00e9\u20ac\uD83D\uDE00z";
    assertArrayEquals(new int[] { 0, 1, 2, 3, 5, 6 }, TextOffsets.charOffsets(text, new int[] { 0, 1, 3, 6, 10, 11 }));
  }

  @Test
  void convertsUnsortedAndDuplicateOffsets() {
    assertArrayEquals(new int[] { 2, 0, 2, 1 }, TextOffsets.charOffsets("\u00e9\u00e9\u00e9", new int[] { 4, 0, 4, 2 }));
  }

  @Test
  void clampsOffsetsAfterTheEnd() {
    assertArrayEquals(new int[] { 2 }, TextOffsets.charOffsets("ab", new int[] { 10 }));
    assertArrayEquals(new int[] {}, TextOffsets.charOffsets("ab", new int[] {}));
  }
}
