package org.eclipse.eripgrep.ui.copy;

import org.eclipse.eripgrep.ui.ERipGrepViewPart;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.dialogs.*;
import org.eclipse.search.internal.ui.SearchPlugin;
import org.eclipse.search2.internal.ui.SearchMessages;

public class RemoveAllSearchesAction extends Action {

  private ERipGrepViewPart ripGrepViewPart;

  public RemoveAllSearchesAction(ERipGrepViewPart ripGrepViewPart) {
    super(SearchMessages.RemoveAllSearchesAction_label);
    this.ripGrepViewPart = ripGrepViewPart;
    setToolTipText(SearchMessages.RemoveAllSearchesAction_tooltip);
  }

  private boolean promptForConfirmation() {

    MessageDialog dialog = new MessageDialog(SearchPlugin.getActiveWorkbenchShell(),
        SearchMessages.RemoveAllSearchesAction_tooltip, // title
        null, // image
        SearchMessages.RemoveAllSearchesAction_confirm_message, // message
        MessageDialog.CONFIRM,
        new String[] { SearchMessages.RemoveAllSearchesAction_confirm_label, IDialogConstants.CANCEL_LABEL },
        IDialogConstants.OK_ID);

    dialog.open();
    if (dialog.getReturnCode() != IDialogConstants.OK_ID) {
      return false;
    }
    return true;
  }

  @Override
  public void run() {
    if (promptForConfirmation()) {
      ripGrepViewPart.clearHistory();
    }
  }
}
