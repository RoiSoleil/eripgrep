package org.eclipse.eripgrep.utils;

import org.eclipse.core.runtime.preferences.*;
import org.eclipse.eripgrep.Activator;

public class Utils {

  public static IEclipsePreferences getPreferences() {
    return InstanceScope.INSTANCE.getNode(Activator.PLUGIN_ID);
  }
}
