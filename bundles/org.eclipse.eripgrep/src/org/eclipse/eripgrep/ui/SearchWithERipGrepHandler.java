package org.eclipse.eripgrep.ui;

import org.eclipse.core.commands.*;
import org.eclipse.eripgrep.Activator;
import org.eclipse.jface.text.TextSelection;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.ui.*;

public class SearchWithERipGrepHandler extends AbstractHandler {

  @Override
  public Object execute(ExecutionEvent event) throws ExecutionException {
    ISelection selection = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getSelectionService().getSelection();
    if (selection instanceof TextSelection && !((TextSelection) selection).isEmpty()) {
      String text = ((TextSelection) selection).getText();
      if (!text.isEmpty()) {
        try {
          ERipGrepViewPart eRipViewPart = (ERipGrepViewPart) PlatformUI.getWorkbench().getActiveWorkbenchWindow()
              .getActivePage().showView(ERipGrepViewPart.ID);
          eRipViewPart.searchFor(text, true, false);
        } catch (PartInitException e) {
          Activator.error(e);
        }
      }
    }
    return null;
  }

}
