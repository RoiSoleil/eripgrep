package org.eclipse.eripgrep.core;

import java.util.Arrays;

/**
 * Conversions between the positions given by RipGrep (line numbers, UTF-8 byte offsets) and character offsets.
 */
public final class TextOffsets {

  private TextOffsets() {
  }

  /**
   * Returns the offset of the first character of a line, as RipGrep counts them: lines are numbered from 1 and
   * end with a line feed.
   *
   * @return the offset, or -1 if the content has fewer lines
   */
  public static int lineOffset(CharSequence content, long lineNumber) {
    int offset = 0;
    for (long line = 1; line < lineNumber; line++) {
      int lineFeed = indexOf(content, '\n', offset);
      if (lineFeed < 0) {
        return -1;
      }
      offset = lineFeed + 1;
    }
    return offset;
  }

  private static int indexOf(CharSequence content, char c, int from) {
    for (int i = from; i < content.length(); i++) {
      if (content.charAt(i) == c) {
        return i;
      }
    }
    return -1;
  }

  /**
   * Converts offsets in the UTF-8 encoding of a text into character offsets of this text.
   */
  public static int[] charOffsets(String text, int[] byteOffsets) {
    int[] sorted = byteOffsets.clone();
    Arrays.sort(sorted);
    int[] converted = new int[sorted.length];
    int next = 0;
    int bytes = 0;
    int i = 0;
    while (next < sorted.length) {
      if (bytes >= sorted[next] || i >= text.length()) {
        converted[next++] = i;
        continue;
      }
      char c = text.charAt(i);
      if (Character.isHighSurrogate(c) && i + 1 < text.length() && Character.isLowSurrogate(text.charAt(i + 1))) {
        bytes += 4;
        i += 2;
      } else {
        bytes += c < 0x80 ? 1 : c < 0x800 ? 2 : 3;
        i++;
      }
    }
    int[] result = new int[byteOffsets.length];
    for (int j = 0; j < byteOffsets.length; j++) {
      result[j] = converted[Arrays.binarySearch(sorted, byteOffsets[j])];
    }
    return result;
  }
}
