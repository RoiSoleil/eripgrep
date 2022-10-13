package org.eclipse.eripgrep.model;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IProject;

public class SearchProject {

  private IProject project;
  private List<MatchingFile> matchingFiles = new ArrayList<>();
  private RipGrepError ripGrepError;

  public IProject getProject() {
    return project;
  }

  public void setProject(IProject project) {
    this.project = project;
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
