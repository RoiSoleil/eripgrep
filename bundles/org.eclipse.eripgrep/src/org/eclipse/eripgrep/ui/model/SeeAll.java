package org.eclipse.eripgrep.ui.model;

/**
 * The node, at the end of a long list of children, which holds the remaining ones.
 *
 * @param parent the element whose children are shortened
 */
public record SeeAll(Object parent) {

  public static final int MAX_NUMBER = 50;

  @Override
  public boolean equals(Object object) {
    return object instanceof SeeAll seeAll && seeAll.parent == parent;
  }

  @Override
  public int hashCode() {
    return System.identityHashCode(parent);
  }
}
