package org.eclipse.eripgrep.model;

public class Error {

  private final SearchedProject searchProject;
  private final String error;

  public Error(SearchedProject searchProject, String error) {
    this.searchProject = searchProject;
    this.error = error;
    searchProject.setError(this);
  }

  public SearchedProject getSearchProject() {
    return searchProject;
  }

  public String getError() {
    return error;
  }
}
