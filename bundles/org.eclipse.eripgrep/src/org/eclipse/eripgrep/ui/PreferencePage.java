package org.eclipse.eripgrep.ui;

import static org.eclipse.eripgrep.utils.PreferenceConstantes.*;

import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.eripgrep.Activator;
import org.eclipse.eripgrep.core.RipGrep;
import org.eclipse.jface.preference.*;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.program.Program;
import org.eclipse.swt.widgets.*;
import org.eclipse.ui.*;
import org.eclipse.ui.preferences.ScopedPreferenceStore;

public class PreferencePage extends FieldEditorPreferencePage implements IWorkbenchPreferencePage {

  public static final String ID = "org.eclipse.eripgrep.PreferencePage";

  private FileFieldEditor ripGrepPathEditor;
  private Label versionLabel;

  public PreferencePage() {
    super(GRID);
  }

  @Override
  protected void createFieldEditors() {
    Composite parent = getFieldEditorParent();
    ripGrepPathEditor = new FileFieldEditor(RIPGREP_PATH, "&Rip grep binary : ", parent);
    ripGrepPathEditor.getTextControl(parent).setMessage("Found on the PATH if empty");
    ripGrepPathEditor.getTextControl(parent).addModifyListener(e -> updateVersion());
    addField(ripGrepPathEditor);
    versionLabel = new Label(parent, SWT.NONE);
    versionLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 3, 1));
    addField(new BooleanFieldEditor(SEARCH_IN_CLOSED_PROJECT, "&Search in closed project", parent));
    addField(new IntegerFieldEditor(THREAD_NUMBER, "&Number of RipGrep thread (0: automatic) : ", parent, 3));
    addField(new IntegerFieldEditor(MAX_MATCHES, "&Maximum number of matches (0: no limit) : ", parent, 9));
    StringFieldEditor extraArgumentsEditor = new StringFieldEditor(EXTRA_ARGUMENTS, "&Additional arguments : ", parent);
    extraArgumentsEditor.getTextControl(parent).setMessage("e.g. --follow --max-filesize 1M");
    addField(extraArgumentsEditor);
    Link link = new Link(parent, SWT.NONE);
    link.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 3, 1));
    link.setText("<A>Get RipGrep !</A>");
    link.addSelectionListener(SelectionListener.widgetSelectedAdapter(e -> Program.launch("https://github.com/BurntSushi/ripgrep")));
  }

  @Override
  protected void initialize() {
    super.initialize();
    updateVersion();
  }

  private void updateVersion() {
    if (versionLabel == null) {
      return;
    }
    String path = RipGrep.locate(ripGrepPathEditor.getStringValue());
    String version = path != null ? RipGrep.version(path) : null;
    if (version == null) {
      versionLabel.setText("RipGrep is not found.");
    } else {
      versionLabel.setText(version + " (" + path + ")");
    }
  }

  @Override
  public void init(IWorkbench workbench) {
    setPreferenceStore(new ScopedPreferenceStore(InstanceScope.INSTANCE, Activator.PLUGIN_ID));
  }

}
