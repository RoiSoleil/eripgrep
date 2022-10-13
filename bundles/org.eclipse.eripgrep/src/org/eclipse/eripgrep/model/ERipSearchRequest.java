package org.eclipse.eripgrep.model;

import org.eclipse.eripgrep.ERipGrepProgressListener;

public class ERipSearchRequest {

  private String text;
  private boolean caseSensitive = true;
  private ERipGrepProgressListener listener;
  
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

  public ERipGrepProgressListener getListener() {
    return listener;
  }
  
  public void setListener(ERipGrepProgressListener listener) {
    this.listener = listener;
  }
}
