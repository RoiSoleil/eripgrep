package org.eclipse.eripgrep.ui;

import org.eclipse.jface.action.Action;
import org.eclipse.search.internal.ui.SearchPluginImages;
import org.eclipse.search2.internal.ui.SearchMessages;

class SearchAgainAction extends Action {

  public SearchAgainAction() {
    setText(SearchMessages.SearchAgainAction_label);
    setToolTipText(SearchMessages.SearchAgainAction_tooltip);
    SearchPluginImages.setImageDescriptors(this, SearchPluginImages.T_LCL, SearchPluginImages.IMG_LCL_REFRESH);
  }

  @Override
  public void run() {
  }
}
