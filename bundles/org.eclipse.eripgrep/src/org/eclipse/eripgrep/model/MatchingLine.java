package org.eclipse.eripgrep.model;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MatchingLine {

  private static final String ANSI_END = "\u001B[0m";
  private static final Pattern MATCHING_PATTERN = Pattern.compile("\u001B\\[0m\u001B\\[1m\u001B\\[31m(.*?)" + ANSI_END.replace("[", "\\["));
  private static final int LINENUMBER_PREFIX_LENGTH = 9;

  final MatchingFile matchingFile;
  final String line;

  private final int lineNumber;
  private final String matchingLine;

  public MatchingLine(MatchingFile matchingFile, String line) {
    this.matchingFile = matchingFile;
    this.line = line;
    String tmp = line.substring(LINENUMBER_PREFIX_LENGTH);
    int i = tmp.indexOf(ANSI_END);
    lineNumber = Integer.parseInt(tmp.substring(0, i));
    matchingLine = tmp.substring(i + ANSI_END.length() + 1);
    matchingFile.getMatchingLines().add(this);
  }

  public MatchingFile getMatchingFile() {
    return matchingFile;
  }

  public String getLine() {
    return line;
  }

  public int getLineNumber() {
    return lineNumber;
  }

  public String getMatchingLine() {
    return matchingLine;
  }

  public Matcher getMatcher() {
    return MATCHING_PATTERN.matcher(matchingLine);
  }
}
