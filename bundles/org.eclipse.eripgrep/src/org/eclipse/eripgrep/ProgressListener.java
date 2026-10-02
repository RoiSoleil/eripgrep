package org.eclipse.eripgrep;

public interface ProgressListener {

  /**
   * The response has new results. Called from the thread of the search.
   */
  public void update();

}
