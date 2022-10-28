package org.eclipse.eripgrep.model;

import java.util.*;

import org.eclipse.core.resources.IProject;

public class SearchedProject {

  private final Response response;
  private final IProject project;
 
  private final List<MatchingFile> matchingFiles = new ArrayList<>();
  private Error error;

  public SearchedProject(Response response, IProject project) {
    this.response = response;
    this.project = project;
    response.getSearchedProjects().add(this);
  }

  public Response getResponse() {
    return response;
  }

  public IProject getProject() {
    return project;
  }

  public List<MatchingFile> getMatchingFiles() {
    return matchingFiles;
  }

  public Error getError() {
    return error;
  }

  public void setError(Error ripGrepError) {
    this.error = ripGrepError;
  }
}
