package org.eclipse.eripgrep.ui;

import java.net.*;

import org.eclipse.eripgrep.Activator;
import org.eclipse.jface.preference.PreferenceDialog;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.dialogs.PreferencesUtil;

public class UiUtils {

  public static Image createImageFromURL(String url) {
    ImageDescriptor imageDescriptor = createImageDescriptorFromURL(url);
    return imageDescriptor != null ? imageDescriptor.createImage() : null;
  }

  public static ImageDescriptor createImageDescriptorFromURL(String url) {
    try {
      return ImageDescriptor.createFromURL(new URL(url));
    } catch (MalformedURLException e) {
      Activator.error(e);
    }
    return null;
  }

  public static void openPreferencePage() {
    PreferenceDialog dialog = PreferencesUtil.createPreferenceDialogOn(Display.getDefault().getActiveShell(),
        PreferencePage.ID, new String[] { PreferencePage.ID }, null);
    dialog.open();
  }
}
