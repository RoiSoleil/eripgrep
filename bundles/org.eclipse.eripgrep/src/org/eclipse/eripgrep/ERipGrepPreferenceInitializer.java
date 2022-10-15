package org.eclipse.eripgrep;

import static org.eclipse.eripgrep.ui.ERipGrepPreferencePage.SEARCH_IN_CLOSED_PROJECT;
import static org.eclipse.eripgrep.ui.ERipGrepPreferencePage.THREAD_NUMBER;

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer;
import org.eclipse.core.runtime.preferences.DefaultScope;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;

public class ERipGrepPreferenceInitializer extends AbstractPreferenceInitializer {

  public ERipGrepPreferenceInitializer() {
  }

  @Override
  public void initializeDefaultPreferences() {
    IEclipsePreferences preferences = DefaultScope.INSTANCE.getNode(Activator.PLUGIN_ID);
    preferences.putBoolean(SEARCH_IN_CLOSED_PROJECT, true);
    preferences.putInt(THREAD_NUMBER, 5);
  }

}
