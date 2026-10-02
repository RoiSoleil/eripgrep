package org.eclipse.eripgrep.core;

import java.util.*;

/**
 * A matching line prepared for display on a single row: without indentation nor control characters, and
 * shortened around its first match when too long.
 */
public record LineExcerpt(String text, List<Span> spans) {

  private static final String ELLIPSIS = "…";
  private static final int CONTEXT_BEFORE_MATCH = 40;

  public static LineExcerpt of(String line, List<Span> spans, int maxLength) {
    int start = 0;
    while (start < line.length() && Character.isWhitespace(line.charAt(start))) {
      start++;
    }
    int end = line.length();
    while (end > start && Character.isWhitespace(line.charAt(end - 1))) {
      end--;
    }
    String prefix = "";
    String suffix = "";
    if (end - start > maxLength) {
      if (!spans.isEmpty() && spans.get(0).start() - start > CONTEXT_BEFORE_MATCH) {
        start = Math.min(spans.get(0).start() - CONTEXT_BEFORE_MATCH, end - maxLength);
        prefix = ELLIPSIS;
      }
      if (end - start > maxLength) {
        end = start + maxLength;
        suffix = ELLIPSIS;
      }
    }
    StringBuilder text = new StringBuilder(prefix);
    for (int i = start; i < end; i++) {
      char c = line.charAt(i);
      text.append(Character.isISOControl(c) ? ' ' : c);
    }
    text.append(suffix);
    List<Span> shifted = new ArrayList<>();
    int shift = prefix.length() - start;
    for (Span span : spans) {
      int spanStart = Math.max(span.start(), start);
      int spanEnd = Math.min(span.end(), end);
      if (spanStart < spanEnd) {
        shifted.add(new Span(spanStart + shift, spanEnd + shift));
      }
    }
    return new LineExcerpt(text.toString(), shifted);
  }
}
