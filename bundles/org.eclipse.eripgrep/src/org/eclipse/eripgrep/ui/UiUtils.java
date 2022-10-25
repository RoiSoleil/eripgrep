package org.eclipse.eripgrep.ui;

import org.eclipse.jface.preference.PreferenceDialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.dialogs.PreferencesUtil;

public class UiUtils {

  public static void openPreferencePage() {
    PreferenceDialog dialog = PreferencesUtil.createPreferenceDialogOn(Display.getDefault().getActiveShell(),
        ERipGrepPreferencePage.ID, new String[] { ERipGrepPreferencePage.ID }, null);
    dialog.open();
  }
}
