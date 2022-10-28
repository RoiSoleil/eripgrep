package org.eclipse.eripgrep.ui;

import static org.eclipse.eripgrep.utils.PreferenceConstantes.*;

import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.eripgrep.Activator;
import org.eclipse.jface.preference.*;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.program.Program;
import org.eclipse.swt.widgets.*;
import org.eclipse.ui.*;
import org.eclipse.ui.preferences.ScopedPreferenceStore;

public class PreferencePage extends FieldEditorPreferencePage implements IWorkbenchPreferencePage {

  public static final String ID = "org.eclipse.eripgrep.PreferencePage";

  @Override
  protected void createFieldEditors() {
    addField(new FileFieldEditor(RIPGREP_PATH, "&Rip grep binary : ", getFieldEditorParent()));
    addField(new BooleanFieldEditor(SEARCH_IN_CLOSED_PROJECT, "&Search in closed project", getFieldEditorParent()));
    addField(new IntegerFieldEditor(THREAD_NUMBER, "&Number of RipGrep thread : ", getFieldEditorParent(), 1));
    Composite parent = getFieldEditorParent();
    parent.setLayout(new GridLayout());
    Link link = new Link(parent, SWT.NONE);
    link.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    link.setText("<A>Get RipGrep !</A>");
    link.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        Program.launch("https://github.com/BurntSushi/ripgrep");
      }
    });
  }

  @Override
  public void init(IWorkbench workbench) {
    setPreferenceStore(new ScopedPreferenceStore(InstanceScope.INSTANCE, Activator.PLUGIN_ID));
  }

}
