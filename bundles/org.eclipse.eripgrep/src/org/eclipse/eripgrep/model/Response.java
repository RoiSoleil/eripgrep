package org.eclipse.eripgrep.model;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class Response {

  private final ConcurrentLinkedQueue<SearchedProject> searchedProjects = new ConcurrentLinkedQueue<>();

  public Queue<SearchedProject> getSearchedProjects() {
    return searchedProjects;
  }

}
