package org.eclipse.eripgrep.ui;

import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.eripgrep.Activator;
import org.eclipse.jface.preference.BooleanFieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.FileFieldEditor;
import org.eclipse.jface.preference.IntegerFieldEditor;
import org.eclipse.jface.preference.PreferencePage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.program.Program;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Link;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.eclipse.ui.preferences.ScopedPreferenceStore;

/**
 * The {@link PreferencePage} for ERipGrep.
 */
public class ERipGrepPreferencePage extends FieldEditorPreferencePage implements IWorkbenchPreferencePage {

  public static final String RIPGREP_PATH = "RIPGREP_PATH";
  public static final String SEARCH_IN_CLOSED_PROJECT = "SEARCH_IN_CLOSED_PROJECT";
  public static final String THREAD_NUMBER = "THREAD_NUMBER";
  public static final String ALPHABETICAL_SORT = "ALPHABETICAL_SORT";
  public static final String GROUP_BY_FOLDER = "GROUP_BY_FOLDER";
  public static final String CASE_SENSITIVE = "CASE_SENSITIVE";
  public static final String REGULAR_EXPRESSION = "REGULAR_EXPRESSION";

  public ERipGrepPreferencePage() {
  }

  @Override
  protected void createFieldEditors() {
    addField(new FileFieldEditor(RIPGREP_PATH, "&Rip grep binary : ", getFieldEditorParent()));
    addField(new BooleanFieldEditor(SEARCH_IN_CLOSED_PROJECT, "&Search in closed project : ", getFieldEditorParent()));
    addField(new IntegerFieldEditor(THREAD_NUMBER, "&Number of RipGrep thread : ", getFieldEditorParent(), 1));
    Composite parent = getFieldEditorParent();
    parent.setLayout(new GridLayout());
    Link link = new Link(parent, SWT.BORDER);
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
