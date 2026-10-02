package org.eclipse.eripgrep.model;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The results of a search. It is filled by the search while the view shows it.
 */
public class Response {

  public enum State {
    RUNNING, DONE, CANCELED
  }

  private final ConcurrentLinkedQueue<SearchedProject> searchedProjects = new ConcurrentLinkedQueue<>();
  private final List<Error> errors = new CopyOnWriteArrayList<>();

  private volatile State state = State.RUNNING;
  private volatile boolean limitReached;
  private volatile boolean ripGrepMissing;
  private volatile long searchedFiles = -1;
  private volatile long elapsedMillis;

  public Queue<SearchedProject> getSearchedProjects() {
    return searchedProjects;
  }

  public List<Error> getErrors() {
    return errors;
  }

  public State getState() {
    return state;
  }

  public void setState(State state) {
    this.state = state;
  }

  /**
   * Whether the search was stopped because it found too many matches.
   */
  public boolean isLimitReached() {
    return limitReached;
  }

  public void setLimitReached(boolean limitReached) {
    this.limitReached = limitReached;
  }

  public boolean isRipGrepMissing() {
    return ripGrepMissing;
  }

  public void setRipGrepMissing(boolean ripGrepMissing) {
    this.ripGrepMissing = ripGrepMissing;
  }

  /**
   * The number of files RipGrep searched in, -1 if unknown.
   */
  public long getSearchedFiles() {
    return searchedFiles;
  }

  public void setSearchedFiles(long searchedFiles) {
    this.searchedFiles = searchedFiles;
  }

  public long getElapsedMillis() {
    return elapsedMillis;
  }

  public void setElapsedMillis(long elapsedMillis) {
    this.elapsedMillis = elapsedMillis;
  }

  public int getFileCount() {
    int count = 0;
    for (SearchedProject searchedProject : searchedProjects) {
      count += searchedProject.getMatchingFiles().size();
    }
    return count;
  }

  public int getMatchCount() {
    int count = 0;
    for (SearchedProject searchedProject : searchedProjects) {
      count += searchedProject.getMatchCount();
    }
    return count;
  }
}
