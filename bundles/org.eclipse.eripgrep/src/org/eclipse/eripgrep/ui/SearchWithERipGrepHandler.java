package org.eclipse.eripgrep.ui;

import org.eclipse.core.commands.*;
import org.eclipse.eripgrep.Activator;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.ui.*;
import org.eclipse.ui.handlers.HandlerUtil;

public class SearchWithERipGrepHandler extends AbstractHandler {

  @Override
  public Object execute(ExecutionEvent event) throws ExecutionException {
    IWorkbenchWindow window = HandlerUtil.getActiveWorkbenchWindowChecked(event);
    ISelection selection = window.getSelectionService().getSelection();
    String text = selection instanceof ITextSelection textSelection ? textSelection.getText() : null;
    try {
      ERipGrepViewPart eRipViewPart = (ERipGrepViewPart) window.getActivePage().showView(ERipGrepViewPart.ID);
      if (text != null && !text.isBlank()) {
        eRipViewPart.searchFor(text, true, false);
      } else {
        eRipViewPart.focusSearchField();
      }
    } catch (PartInitException e) {
      Activator.error(e);
    }
    return null;
  }

}
