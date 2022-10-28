package org.eclipse.eripgrep;

public interface ProgressListener {

  public void update(Object element);

  public void done();

}
