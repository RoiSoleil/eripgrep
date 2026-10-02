package org.eclipse.eripgrep.ui;

import java.net.*;

import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.Path;
import org.eclipse.eripgrep.Activator;
import org.eclipse.jface.preference.PreferenceDialog;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.dialogs.PreferencesUtil;

public class UiUtils {

  public static ImageDescriptor createImageDescriptorFromURL(String url) {
    try {
      return ImageDescriptor.createFromURL(new URI(url).toURL());
    } catch (MalformedURLException | URISyntaxException e) {
      Activator.error(e);
    }
    return ImageDescriptor.getMissingImageDescriptor();
  }

  /**
   * An image of this plug-in, e.g. "icons/eripgrep.png".
   */
  public static ImageDescriptor createImageDescriptor(String path) {
    return ImageDescriptor.createFromURL(FileLocator.find(Activator.getDefault().getBundle(), new Path(path)));
  }

  public static void openPreferencePage() {
    PreferenceDialog dialog = PreferencesUtil.createPreferenceDialogOn(Display.getDefault().getActiveShell(),
        PreferencePage.ID, new String[] { PreferencePage.ID }, null);
    dialog.open();
  }
}
