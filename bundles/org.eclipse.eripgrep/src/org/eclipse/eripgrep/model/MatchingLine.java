package org.eclipse.eripgrep.model;

import java.util.List;

import org.eclipse.eripgrep.core.Span;

/**
 * A line with matches or, without any match, a line of context.
 */
public class MatchingLine {

  private final MatchingFile matchingFile;
  private final long lineNumber;
  private final String line;
  private final List<Span> spans;
  private final List<String> replacements;

  public MatchingLine(MatchingFile matchingFile, long lineNumber, String line, List<Span> spans, List<String> replacements) {
    this.matchingFile = matchingFile;
    this.lineNumber = lineNumber;
    this.line = line;
    this.spans = List.copyOf(spans);
    this.replacements = List.copyOf(replacements);
    matchingFile.getMatchingLines().add(this);
  }

  public MatchingFile getMatchingFile() {
    return matchingFile;
  }

  /**
   * The text, several lines for a multiline match.
   */
  public String getLine() {
    return line;
  }

  public long getLineNumber() {
    return lineNumber;
  }

  public int getLineCount() {
    return (int) line.chars().filter(c -> c == '\n').count() + 1;
  }

  public List<Span> getSpans() {
    return spans;
  }

  /**
   * The replacement of each match, empty if the search had no replacement.
   */
  public List<String> getReplacements() {
    return replacements;
  }

  public boolean isContext() {
    return spans.isEmpty();
  }
}
