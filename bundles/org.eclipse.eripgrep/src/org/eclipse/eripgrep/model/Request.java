package org.eclipse.eripgrep.model;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.eripgrep.ProgressListener;

public class Request {

  private long time = -1;
  private String text;
  private boolean caseSensitive;
  private boolean regularExpression;

  private ProgressListener listener;
  private IProgressMonitor progressMonitor;

  public long getTime() {
    return time;
  }

  public void setTime(long time) {
    this.time = time;
  }

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

  public boolean isRegularExpression() {
    return regularExpression;
  }

  public void setRegularExpression(boolean regularExpression) {
    this.regularExpression = regularExpression;
  }

  public ProgressListener getListener() {
    return listener;
  }

  public void setListener(ProgressListener listener) {
    this.listener = listener;
  }

  public IProgressMonitor getProgressMonitor() {
    return progressMonitor;
  }

  public void setProgressMonitor(IProgressMonitor progressMonitor) {
    this.progressMonitor = progressMonitor;
  }
}
