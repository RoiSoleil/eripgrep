package org.eclipse.eripgrep.ui;

import org.eclipse.eripgrep.*;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.*;
import org.eclipse.ui.part.ViewPart;

/**
 * A view for RipGrep.
 */
public class ERipViewPart extends ViewPart {

  public static final String ID = "org.eclipse.eripgrep.ERipGrepView";

  public ERipViewPart() {
  }

  @Override
  public void createPartControl(Composite parent) {
    parent.setLayout(new FillLayout(SWT.VERTICAL));
    new Text(parent, SWT.SINGLE | SWT.BORDER | SWT.SEARCH | SWT.ICON_CANCEL);
    new TreeViewer(parent, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL | SWT.BORDER | SWT.FILL);
  }

  @Override
  public void setFocus() {
  }

  public void searchFor(String text) {
    ERipSearchRequest request = new ERipSearchRequest();
    request.setText(text);
    ERipGrepEngine.searchFor(request);
  }

}
