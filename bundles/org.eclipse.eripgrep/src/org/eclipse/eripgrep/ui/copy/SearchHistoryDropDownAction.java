package org.eclipse.eripgrep.ui.copy;

import java.text.*;
import java.util.*;
import java.util.List;
import java.util.Map.Entry;

import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.eripgrep.model.*;
import org.eclipse.eripgrep.ui.ERipGrepViewPart;
import org.eclipse.jface.action.*;
import org.eclipse.search.internal.ui.SearchPluginImages;
import org.eclipse.search2.internal.ui.SearchMessages;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.*;

public class SearchHistoryDropDownAction extends Action implements IMenuCreator {

  private class ShowSearchFromHistoryAction extends Action {
    private ERipGrepViewPart ripGrepViewPart;
    private Request searchRequest;
    private Response response;

    public ShowSearchFromHistoryAction(ERipGrepViewPart ripGrepViewPart, Request searchRequest, Response response) {
      super("", AS_RADIO_BUTTON); //$NON-NLS-1$
      this.searchRequest = searchRequest;
      this.ripGrepViewPart = ripGrepViewPart;
      this.response = response;

      String label = escapeAmp(searchRequest.getText());
      if (label.indexOf('@') >= 0)
        label += '@';
      if (searchRequest.getTime() != -1) {
        label += " (" + DateFormat.getDateTimeInstance().format(new Date(searchRequest.getTime())).toString() + ")";
      }
      setText(label);
    }

    private String escapeAmp(String label) {
      StringBuilder buf = new StringBuilder();
      for (int i = 0; i < label.length(); i++) {
        char ch = label.charAt(i);
        buf.append(ch);
        if (ch == '&') {
          buf.append('&');
        }
      }
      return buf.toString();
    }

    @Override
    public void runWithEvent(Event event) {
      if (response != null) {
        ripGrepViewPart.setCurrent(searchRequest, response);
      } else {
        ripGrepViewPart.searchFor(searchRequest);
      }
    }

  }

  public static final int RESULTS_IN_DROP_DOWN = 10;

  private Menu fMenu;
  private ERipGrepViewPart ripGrepViewPart;

  public SearchHistoryDropDownAction(ERipGrepViewPart ripGrepViewPart) {
    setText(SearchMessages.SearchDropDownAction_label);
    setToolTipText(SearchMessages.SearchDropDownAction_tooltip);
    SearchPluginImages.setImageDescriptors(this, SearchPluginImages.T_LCL, SearchPluginImages.IMG_LCL_SEARCH_HISTORY);
    this.ripGrepViewPart = ripGrepViewPart;
    setMenuCreator(this);
  }

  public void updateEnablement() {
    boolean hasQueries = !ripGrepViewPart.getHistory().isEmpty();
    setEnabled(hasQueries);
  }

  @Override
  public void dispose() {
    disposeMenu();
  }

  void disposeMenu() {
    if (fMenu != null)
      fMenu.dispose();
  }

  @Override
  public Menu getMenu(Menu parent) {
    return null;
  }

  @Override
  public Menu getMenu(Control parent) {
    disposeMenu();
    fMenu = new Menu(parent);
    List<Entry<Request, Response>> history = new ArrayList<>(ripGrepViewPart.getHistory().entrySet());
    if (history.size() > 0) {
      Collections.reverse(history);
      Response currentSearch = ripGrepViewPart.getCurrent();

      for (Entry<Request, Response> search : history) {
        ShowSearchFromHistoryAction action = new ShowSearchFromHistoryAction(ripGrepViewPart, search.getKey(), search.getValue());
        action.setChecked(currentSearch != null && Objects.equals(search.getValue(), currentSearch));
        addActionToMenu(fMenu, action);
      }
      new MenuItem(fMenu, SWT.SEPARATOR);
      addActionToMenu(fMenu, new RemoveAllSearchesAction(ripGrepViewPart));
    }
    return fMenu;
  }

  protected void addActionToMenu(Menu parent, Action action) {
    ActionContributionItem item = new ActionContributionItem(action);
    item.fill(parent, -1);
  }

  @Override
  public void run() {
  }
}
