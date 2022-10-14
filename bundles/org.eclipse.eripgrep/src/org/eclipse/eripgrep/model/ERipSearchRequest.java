package org.eclipse.eripgrep.model;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.eripgrep.ERipGrepProgressListener;

public class ERipSearchRequest {

  private String text;
  private boolean caseSensitive = true;
  private ERipGrepProgressListener listener;
  private IProgressMonitor progressMonitor;

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

  public IProgressMonitor getProgressMonitor() {
    return progressMonitor;
  }

  public void setProgressMonitor(IProgressMonitor progressMonitor) {
    this.progressMonitor = progressMonitor;
  }
}
