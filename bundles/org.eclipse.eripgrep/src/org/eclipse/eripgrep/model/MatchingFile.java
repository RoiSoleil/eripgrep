package org.eclipse.eripgrep.model;

import java.util.ArrayList;
import java.util.List;

public class MatchingFile {

  private String filePath;
  private List<MatchingLine> matchingLines = new ArrayList<>();

  public String getFilePath() {
    return filePath;
  }

  public void setFilePath(String filePath) {
    this.filePath = filePath;
  }

  public List<MatchingLine> getMatchingLines() {
    return matchingLines;
  }
}
