package org.eclipse.eripgrep.core;

import java.util.List;

/**
 * Receives the events of the output of <code>rg --json</code>.
 */
public interface RipGrepOutputHandler {

  /**
   * A file with at least one match starts.
   */
  void begin(String path);

  /**
   * A matching line or, when <code>spans</code> is empty, a line of context.
   *
   * @param text         the line without its line terminator; several lines for a multiline match
   * @param spans        the matches in the text
   * @param replacements the replacement of each match, empty if no replacement was requested
   */
  void line(String path, long lineNumber, String text, List<Span> spans, List<String> replacements);

  void end(String path);

  /**
   * The end of the search.
   *
   * @param searchedFiles the number of files RipGrep searched in
   */
  void summary(long searchedFiles);
}
