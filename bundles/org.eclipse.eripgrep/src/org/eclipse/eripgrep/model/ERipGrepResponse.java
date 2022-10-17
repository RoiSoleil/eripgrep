package org.eclipse.eripgrep.model;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ERipGrepResponse {

  private final ConcurrentLinkedQueue<SearchProject> searchProjects = new ConcurrentLinkedQueue<>();

  public Queue<SearchProject> getSearchProjects() {
    return searchProjects;
  }

}
