package org.eclipse.eripgrep.model;

/**
 * Where to search.
 */
public enum Scope {

  WORKSPACE("Workspace"), SELECTION("Selected resources"), PROJECT("Project of the editor");

  private final String label;

  Scope(String label) {
    this.label = label;
  }

  public String getLabel() {
    return label;
  }
}
