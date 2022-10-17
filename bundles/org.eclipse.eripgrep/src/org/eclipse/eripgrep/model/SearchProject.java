package org.eclipse.eripgrep.model;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IProject;

public class SearchProject {

  private final ERipGrepResponse response;
  private final IProject project;
  private final List<MatchingFile> matchingFiles = new ArrayList<>();
  private RipGrepError ripGrepError;

  public SearchProject(ERipGrepResponse response, IProject project) {
    this.response = response;
    this.project = project;
    response.getSearchProjects().add(this);
  }

  public ERipGrepResponse getResponse() {
    return response;
  }

  public IProject getProject() {
    return project;
  }

  public List<MatchingFile> getMatchingFiles() {
    return matchingFiles;
  }

  public RipGrepError getRipGrepError() {
    return ripGrepError;
  }

  public void setRipGrepError(RipGrepError ripGrepError) {
    this.ripGrepError = ripGrepError;
  }
}
