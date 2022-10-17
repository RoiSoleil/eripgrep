package org.eclipse.eripgrep.model;

public class RipGrepError {

  private final SearchProject searchProject;
  private final String error;

  public RipGrepError(SearchProject searchProject, String error) {
    this.searchProject = searchProject;
    this.error = error;
    searchProject.setRipGrepError(this);
  }

  public SearchProject getSearchProject() {
    return searchProject;
  }

  public String getError() {
    return error;
  }
}
