package org.eclipse.eripgrep.core;

/**
 * A range of characters in a line, the end being exclusive.
 */
public record Span(int start, int end) {

  public int length() {
    return end - start;
  }
}
