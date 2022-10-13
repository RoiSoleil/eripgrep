package org.eclipse.eripgrep.model;

import java.util.HashSet;
import java.util.Set;

public class ERipGrepResponse {

  private Set<SearchProject> searchProjects = new HashSet<>();
  
  public Set<SearchProject> getSearchProjects() {
    return searchProjects;
  }

}
