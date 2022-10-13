package org.eclipse.eripgrep.ui;

import org.eclipse.eripgrep.ERipGrepEngine;
import org.eclipse.eripgrep.ERipGrepProgressListener;
import org.eclipse.eripgrep.model.ERipGrepResponse;
import org.eclipse.eripgrep.model.ERipSearchRequest;
import org.eclipse.eripgrep.model.MatchingFile;
import org.eclipse.eripgrep.model.MatchingLine;
import org.eclipse.eripgrep.model.SearchProject;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.part.ViewPart;

/**
 * A view for RipGrep.
 */
public class ERipGrepViewPart extends ViewPart {

  public static final String ID = "org.eclipse.eripgrep.ERipGrepView";

  private TreeViewer treeViewer;

  public ERipGrepViewPart() {
  }

  @Override
  public void createPartControl(Composite parent) {
    parent.setLayout(new FillLayout(SWT.VERTICAL));
    new Text(parent, SWT.SINGLE | SWT.BORDER | SWT.SEARCH | SWT.ICON_CANCEL);
    treeViewer = new TreeViewer(parent, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL | SWT.BORDER | SWT.FILL);
    treeViewer.setContentProvider(new ITreeContentProvider() {

      @Override
      public boolean hasChildren(Object element) {
        return getChildren(element) != null && getChildren(element).length > 0;
      }

      @Override
      public Object getParent(Object element) {
        return null;
      }

      @Override
      public Object[] getElements(Object inputElement) {
        if (inputElement instanceof ERipGrepResponse) {
          return ((ERipGrepResponse) inputElement).getSearchProjects().toArray();
        }
        return null;
      }

      @Override
      public Object[] getChildren(Object parentElement) {
        if (parentElement instanceof SearchProject) {
          return ((SearchProject) parentElement).getMatchingFiles().toArray();
        } else if (parentElement instanceof MatchingFile) {
          return ((MatchingFile) parentElement).getMatchingLines().toArray();
        }
        return null;
      }
    });
    treeViewer.setLabelProvider(new LabelProvider() {

      @Override
      public String getText(Object element) {
        if (element instanceof SearchProject) {
          return ((SearchProject) element).getProject().getName();
        } else if (element instanceof MatchingFile) {
          return ((MatchingFile) element).getFilePath();
        } else if (element instanceof MatchingLine) {
          return ((MatchingLine) element).getLine();
        }
        return super.getText(element);
      }

    });
  }

  @Override
  public void setFocus() {
  }

  public void searchFor(String text) {
    ERipSearchRequest request = new ERipSearchRequest();
    request.setText(text);
    request.setListener(new ERipGrepProgressListener() {

      @Override
      public void update(Object element) {
        if (treeViewer.isBusy()) {
          return;
        }

        Display.getDefault().asyncExec(() -> {
          treeViewer.refresh(element);
          if (element instanceof SearchProject) {
            treeViewer.expandToLevel(element, 1);
          }
        });
      }

      @Override
      public void done() {
        while (treeViewer.isBusy()) {
          try {
            Thread.sleep(100);
          } catch (InterruptedException e) {
            e.printStackTrace();
          }
        }
        Display.getDefault().asyncExec(() -> treeViewer.refresh(treeViewer.getInput()));
      }
    });
    treeViewer.setInput(ERipGrepEngine.searchFor(request));
  }

}
