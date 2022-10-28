package org.eclipse.eripgrep;

import static org.eclipse.eripgrep.utils.PreferenceConstantes.*;

import org.eclipse.core.runtime.preferences.*;

public class PreferenceInitializer extends AbstractPreferenceInitializer {

  @Override
  public void initializeDefaultPreferences() {
    IEclipsePreferences preferences = DefaultScope.INSTANCE.getNode(Activator.PLUGIN_ID);
    preferences.putBoolean(SEARCH_IN_CLOSED_PROJECT, true);
    preferences.putInt(THREAD_NUMBER, 5);
    preferences.putBoolean(ALPHABETICAL_SORT, false);
    preferences.putBoolean(GROUP_BY_FOLDER, false);
    preferences.putBoolean(CASE_SENSITIVE, true);
    preferences.putBoolean(REGULAR_EXPRESSION, false);
  }

}
