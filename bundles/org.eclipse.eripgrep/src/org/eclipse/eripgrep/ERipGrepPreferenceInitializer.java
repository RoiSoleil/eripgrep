package org.eclipse.eripgrep;

import static org.eclipse.eripgrep.ui.ERipGrepPreferencePage.ALPHABETICAL_SORT;
import static org.eclipse.eripgrep.ui.ERipGrepPreferencePage.CASE_SENSITIVE;
import static org.eclipse.eripgrep.ui.ERipGrepPreferencePage.GROUP_BY_FOLDER;
import static org.eclipse.eripgrep.ui.ERipGrepPreferencePage.REGULAR_EXPRESSION;
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
    preferences.putBoolean(ALPHABETICAL_SORT, false);
    preferences.putBoolean(GROUP_BY_FOLDER, false);
    preferences.putBoolean(CASE_SENSITIVE, true);
    preferences.putBoolean(REGULAR_EXPRESSION, false);
  }

}
