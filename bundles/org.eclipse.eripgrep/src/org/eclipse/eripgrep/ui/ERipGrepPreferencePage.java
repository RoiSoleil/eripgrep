package org.eclipse.eripgrep.ui;

import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.eripgrep.Activator;
import org.eclipse.jface.preference.*;
import org.eclipse.ui.*;
import org.eclipse.ui.preferences.ScopedPreferenceStore;

/**
 * The {@link PreferencePage} for ERipGrep.
 */
public class ERipGrepPreferencePage extends FieldEditorPreferencePage implements IWorkbenchPreferencePage {

  public ERipGrepPreferencePage() {
  }

  @Override
  protected void createFieldEditors() {
    addField(new FileFieldEditor("RIPGREP_PATH", "&Rip grep binary : ", getFieldEditorParent()));
  }

  @Override
  public void init(IWorkbench workbench) {
    setPreferenceStore(new ScopedPreferenceStore(InstanceScope.INSTANCE, Activator.PLUGIN_ID));
  }
}
