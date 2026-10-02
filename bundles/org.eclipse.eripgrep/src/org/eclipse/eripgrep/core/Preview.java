package org.eclipse.eripgrep.core;

import java.util.*;

/**
 * The text of the preview: the lines of a file around a match, each one after its line number.
 */
public record Preview(String text, List<Range> ranges) {

  public enum Kind {
    LINE_NUMBER, CURRENT_LINE, MATCH
  }

  public record Range(Kind kind, int start, int length) {
  }

  /**
   * @param lines       the lines of the file
   * @param currentLine the number of the selected line, from 1
   * @param lineCount   the number of lines of the selected match
   * @param around      the number of lines shown before and after
   * @param matches     the matches to highlight, by line number; a span may run over the following lines
   */
  public static Preview of(List<String> lines, long currentLine, int lineCount, int around, Map<Long, List<Span>> matches) {
    int first = (int) Math.max(1, currentLine - around);
    int last = (int) Math.min(lines.size(), currentLine + lineCount - 1 + around);
    int width = String.valueOf(last).length();
    StringBuilder text = new StringBuilder();
    List<Range> ranges = new ArrayList<>();
    int[] lineStarts = new int[Math.max(0, last - first + 2)];
    for (int number = first; number <= last; number++) {
      String gutter = " ".repeat(width - String.valueOf(number).length()) + number + "  ";
      ranges.add(new Range(Kind.LINE_NUMBER, text.length(), gutter.length()));
      text.append(gutter);
      lineStarts[number - first] = text.length();
      String line = lines.get(number - 1);
      if (number >= currentLine && number < currentLine + lineCount) {
        ranges.add(new Range(Kind.CURRENT_LINE, text.length(), line.length()));
      }
      text.append(line);
      if (number < last) {
        text.append('\n');
      }
    }
    for (Map.Entry<Long, List<Span>> entry : matches.entrySet()) {
      long number = entry.getKey();
      if (number < first || number > last) {
        continue;
      }
      for (Span span : entry.getValue()) {
        addMatch(lines, first, last, lineStarts, (int) number, span, ranges);
      }
    }
    return new Preview(text.toString(), ranges);
  }

  private static void addMatch(List<String> lines, int first, int last, int[] lineStarts, int number, Span span, List<Range> ranges) {
    int start = span.start();
    int remaining = span.length();
    while (remaining > 0 && number <= last) {
      String line = lines.get(number - 1);
      int length = Math.min(remaining, line.length() - start);
      if (length > 0) {
        ranges.add(new Range(Kind.MATCH, lineStarts[number - first] + start, length));
      }
      // the line feed between two lines of a multiline match
      remaining -= Math.max(length, 0) + 1;
      start = 0;
      number++;
    }
  }
}
