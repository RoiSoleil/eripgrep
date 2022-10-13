package org.eclipse.eripgrep;

public interface ERipGrepProgressListener {

  public void update(Object element);

  public void done();
}
