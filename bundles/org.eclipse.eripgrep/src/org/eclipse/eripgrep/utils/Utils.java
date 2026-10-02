package org.eclipse.eripgrep.utils;

import static org.eclipse.eripgrep.utils.PreferenceConstantes.*;

import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.preferences.*;
import org.eclipse.eripgrep.Activator;
import org.eclipse.eripgrep.core.*;
import org.osgi.service.prefs.BackingStoreException;

public class Utils {

  public static IEclipsePreferences getPreferences() {
    return InstanceScope.INSTANCE.getNode(Activator.PLUGIN_ID);
  }

  public static void savePreferences() {
    try {
      getPreferences().flush();
    } catch (BackingStoreException e) {
      Activator.error(e);
    }
  }

  public static boolean getBoolean(String key) {
    return Platform.getPreferencesService().getBoolean(Activator.PLUGIN_ID, key, false, null);
  }

  public static int getInt(String key) {
    return Platform.getPreferencesService().getInt(Activator.PLUGIN_ID, key, 0, null);
  }

  public static String getString(String key) {
    return Platform.getPreferencesService().getString(Activator.PLUGIN_ID, key, "", null);
  }

  public static RipGrepSettings getSettings() {
    return new RipGrepSettings(RipGrep.locate(getString(RIPGREP_PATH)), Math.max(0, getInt(THREAD_NUMBER)),
        Math.max(0, getInt(MAX_MATCHES)), RipGrepCommand.splitArguments(getString(EXTRA_ARGUMENTS)),
        getBoolean(SEARCH_IN_CLOSED_PROJECT));
  }
}
