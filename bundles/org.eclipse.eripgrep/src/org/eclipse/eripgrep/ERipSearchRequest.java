package org.eclipse.eripgrep;

public class ERipSearchRequest {

  private String text;
  private boolean caseSensitive = true;

  public String getText() {
    return text;
  }

  public void setText(String text) {
    this.text = text;
  }

  public boolean isCaseSensitive() {
    return caseSensitive;
  }

  public void setCaseSensitive(boolean caseSensitive) {
    this.caseSensitive = caseSensitive;
  }

}
