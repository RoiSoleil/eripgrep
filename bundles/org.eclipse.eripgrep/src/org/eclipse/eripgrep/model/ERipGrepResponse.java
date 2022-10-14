package org.eclipse.eripgrep.model;

import java.util.*;

public class ERipGrepResponse {

  private Set<SearchProject> searchProjects = new LinkedHashSet<>();

  public Set<SearchProject> getSearchProjects() {
    return searchProjects;
  }

}
