package org.eclipse.eripgrep.ui;

import static org.eclipse.eripgrep.ui.UiUtils.*;
import static org.eclipse.eripgrep.utils.PreferenceConstantes.*;

import java.text.DateFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.Adapters;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.SafeRunner;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.IJobChangeEvent;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.jobs.JobChangeAdapter;
import org.eclipse.eripgrep.Engine;
import org.eclipse.eripgrep.core.Span;
import org.eclipse.eripgrep.core.TextOffsets;
import org.eclipse.eripgrep.model.Error;
import org.eclipse.eripgrep.model.MatchingFile;
import org.eclipse.eripgrep.model.MatchingLine;
import org.eclipse.eripgrep.model.Request;
import org.eclipse.eripgrep.model.Response;
import org.eclipse.eripgrep.model.Scope;
import org.eclipse.eripgrep.model.SearchedProject;
import org.eclipse.eripgrep.ui.model.Folder;
import org.eclipse.eripgrep.ui.model.SeeAll;
import org.eclipse.eripgrep.utils.Utils;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.ActionContributionItem;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IMenuCreator;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.resource.LocalResourceManager;
import org.eclipse.jface.resource.ResourceManager;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.viewers.DelegatingStyledCellLabelProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TreePath;
import org.eclipse.jface.viewers.TreeSelection;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.ltk.ui.refactoring.RefactoringWizard;
import org.eclipse.ltk.ui.refactoring.RefactoringWizardOpenOperation;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.program.Program;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;
import org.eclipse.ui.IEditorDescriptor;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IEditorReference;
import org.eclipse.ui.ISelectionListener;
import org.eclipse.ui.IWorkbenchActionConstants;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.editors.text.EditorsUI;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.part.FileEditorInput;
import org.eclipse.ui.part.ViewPart;
import org.eclipse.ui.texteditor.ITextEditor;

/**
 * A view for RipGrep.
 */
public class ERipGrepViewPart extends ViewPart {

  public static final String ID = "org.eclipse.eripgrep.ERipGrepView";

  private static final int LIVE_SEARCH_DELAY = 300;
  private static final int LIVE_SEARCH_MIN_LENGTH = 3;
  private static final int REFRESH_DELAY = 200;
  private static final int AUTO_EXPAND_MAX_LINES = 200;
  private static final int[] CONTEXT_LINES_CHOICES = { 0, 1, 2, 3, 5 };

  private static final History history = History.load();

  private final ResultContentProvider contentProvider = new ResultContentProvider();
  private ResourceManager resourceManager;
  private Image image;
  private Image runningImage;
  private Image doneImage;

  private Text textField;
  private ToolItem caseSensitiveItem;
  private ToolItem wholeWordItem;
  private ToolItem regularExpressionItem;
  private ToolItem replaceItem;
  private Text replaceField;
  private Button replaceButton;
  private Text globField;
  private Combo scopeCombo;
  private Link statusLink;
  private SashForm sashForm;
  private TreeViewer treeViewer;
  private PreviewPane previewPane;

  private Action searchAgainAction;
  private Action cancelSearchAction;
  private Action removeSelectedMatchesAction;

  private Request currentRequest;
  private Job currentJob;
  /**
   * The search started while typing: the next one replaces it in the history.
   */
  private Request liveRequest;
  private boolean updatingFields;
  private final AtomicBoolean dirty = new AtomicBoolean();
  private List<IResource> selectedResources = List.of();

  private final Runnable liveSearch = () -> {
    if (!textField.isDisposed() && Utils.getBoolean(LIVE_SEARCH)) {
      searchFromFields(true);
    }
  };

  private final Runnable refresher = new Runnable() {
    @Override
    public void run() {
      if (treeViewer.getControl().isDisposed() || currentJob == null) {
        return;
      }
      if (dirty.getAndSet(false)) {
        refreshResults();
      }
      Display.getCurrent().timerExec(REFRESH_DELAY, this);
    }
  };

  private final ISelectionListener selectionListener = (part, selection) -> {
    if (part != this && selection instanceof IStructuredSelection structuredSelection) {
      List<IResource> resources = new ArrayList<>();
      for (Object element : structuredSelection.toList()) {
        IResource resource = Adapters.adapt(element, IResource.class);
        if (resource != null) {
          resources.add(resource);
        }
      }
      if (!resources.isEmpty()) {
        selectedResources = resources;
      }
    }
  };

  @Override
  public void createPartControl(Composite parent) {
    resourceManager = new LocalResourceManager(JFaceResources.getResources(), parent);
    image = resourceManager.createImage(createImageDescriptor("icons/eripgrep.png"));
    runningImage = resourceManager.createImage(createImageDescriptor("icons/eripgrep-running.png"));
    doneImage = resourceManager.createImage(createImageDescriptor("icons/eripgrep-done.png"));
    contentProvider.setAlphabeticalSort(Utils.getBoolean(ALPHABETICAL_SORT));
    contentProvider.setGroupByFolder(Utils.getBoolean(GROUP_BY_FOLDER));
    GridLayout gridLayout = new GridLayout();
    gridLayout.horizontalSpacing = 0;
    gridLayout.verticalSpacing = 0;
    gridLayout.marginHeight = 0;
    gridLayout.marginWidth = 0;
    parent.setLayout(gridLayout);
    createSearchFields(parent);
    statusLink = new Link(parent, SWT.NONE);
    GridData statusData = new GridData(SWT.FILL, SWT.CENTER, true, false);
    statusData.horizontalIndent = 6;
    statusData.verticalIndent = 2;
    statusLink.setLayoutData(statusData);
    statusLink.addSelectionListener(SelectionListener.widgetSelectedAdapter(e -> {
      if ("get".equals(e.text)) {
        Program.launch("https://github.com/BurntSushi/ripgrep#installation");
      } else {
        UiUtils.openPreferencePage();
        searchAgain();
      }
    }));
    sashForm = new SashForm(parent, SWT.VERTICAL);
    sashForm.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
    createTreeViewer(sashForm);
    previewPane = new PreviewPane(sashForm);
    sashForm.setWeights(60, 40);
    // the preview is beside the results in a wide view, below them in a high one
    sashForm.addListener(SWT.Resize, e -> {
      Point size = sashForm.getSize();
      int orientation = size.x > 2 * size.y ? SWT.HORIZONTAL : SWT.VERTICAL;
      if (sashForm.getOrientation() != orientation) {
        sashForm.setOrientation(orientation);
      }
    });
    showPreview(Utils.getBoolean(SHOW_PREVIEW));
    initToolbar();
    initMenu();
    getSite().getPage().addSelectionListener(selectionListener);
    selectionListener.selectionChanged(null, getSite().getPage().getSelection());
    updateStatus();
  }

  private void createSearchFields(Composite parent) {
    Composite composite = new Composite(parent, SWT.NONE);
    composite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
    GridLayout gridLayout = new GridLayout(2, false);
    gridLayout.verticalSpacing = 3;
    gridLayout.marginHeight = 4;
    composite.setLayout(gridLayout);

    textField = new Text(composite, SWT.SEARCH | SWT.ICON_SEARCH | SWT.ICON_CANCEL);
    textField.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    textField.setMessage("Search");
    textField.addModifyListener(e -> scheduleLiveSearch());
    textField.addSelectionListener(SelectionListener.widgetDefaultSelectedAdapter(e -> searchFromFields(false)));
    textField.addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        if (e.keyCode == SWT.ARROW_DOWN) {
          treeViewer.getControl().setFocus();
        }
      }
    });
    ToolBar toolBar = new ToolBar(composite, SWT.FLAT);
    toolBar.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));
    caseSensitiveItem = createOptionItem(toolBar, "Aa", "Case sensitive", CASE_SENSITIVE);
    wholeWordItem = createOptionItem(toolBar, "W", "Whole word", WHOLE_WORD);
    regularExpressionItem = createOptionItem(toolBar, ".*", "Regular expression", REGULAR_EXPRESSION);
    new ToolItem(toolBar, SWT.SEPARATOR);
    replaceItem = new ToolItem(toolBar, SWT.CHECK);
    replaceItem.setText("⇄");
    replaceItem.setToolTipText("Replace");
    replaceItem.setSelection(Utils.getBoolean(SHOW_REPLACE));
    replaceItem.addSelectionListener(SelectionListener.widgetSelectedAdapter(e -> {
      Utils.getPreferences().putBoolean(SHOW_REPLACE, replaceItem.getSelection());
      Utils.savePreferences();
      showReplace(replaceItem.getSelection());
      if (replaceItem.getSelection()) {
        replaceField.setFocus();
      }
    }));

    replaceField = new Text(composite, SWT.BORDER);
    replaceField.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    replaceField.setMessage("Replace");
    replaceField.addSelectionListener(SelectionListener.widgetDefaultSelectedAdapter(e -> replace(false)));
    replaceButton = new Button(composite, SWT.PUSH);
    replaceButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));
    replaceButton.setText("Replace All...");
    replaceButton.setToolTipText("Preview and replace all the matches");
    replaceButton.addSelectionListener(SelectionListener.widgetSelectedAdapter(e -> replace(false)));
    showReplace(replaceItem.getSelection());

    globField = new Text(composite, SWT.BORDER);
    globField.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    globField.setMessage("Files to include or exclude, e.g. *.java, !test/");
    globField.setToolTipText("Globs separated by commas, a leading ! excludes");
    globField.setText(Utils.getString(FILE_GLOBS));
    globField.addModifyListener(e -> {
      if (!updatingFields) {
        Utils.getPreferences().put(FILE_GLOBS, globField.getText());
        scheduleLiveSearch();
      }
    });
    globField.addSelectionListener(SelectionListener.widgetDefaultSelectedAdapter(e -> searchFromFields(false)));
    scopeCombo = new Combo(composite, SWT.READ_ONLY);
    scopeCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));
    for (Scope scope : Scope.values()) {
      scopeCombo.add(scope.getLabel());
    }
    scopeCombo.setToolTipText("Where to search");
    scopeCombo.select(getScope(Utils.getString(SCOPE)).ordinal());
    scopeCombo.addSelectionListener(SelectionListener.widgetSelectedAdapter(e -> {
      if (!updatingFields) {
        Utils.getPreferences().put(SCOPE, Scope.values()[scopeCombo.getSelectionIndex()].name());
        Utils.savePreferences();
        scheduleLiveSearch();
      }
    }));
  }

  private ToolItem createOptionItem(ToolBar toolBar, String text, String toolTip, String preference) {
    ToolItem item = new ToolItem(toolBar, SWT.CHECK);
    item.setText(text);
    item.setToolTipText(toolTip);
    item.setSelection(Utils.getBoolean(preference));
    item.addSelectionListener(SelectionListener.widgetSelectedAdapter(e -> {
      Utils.getPreferences().putBoolean(preference, item.getSelection());
      Utils.savePreferences();
      scheduleLiveSearch();
    }));
    return item;
  }

  private static Scope getScope(String name) {
    try {
      return Scope.valueOf(name);
    } catch (IllegalArgumentException e) {
      return Scope.WORKSPACE;
    }
  }

  private void showReplace(boolean show) {
    for (Control control : new Control[] { replaceField, replaceButton }) {
      ((GridData) control.getLayoutData()).exclude = !show;
      control.setVisible(show);
    }
    replaceField.getParent().getParent().layout(true, true);
  }

  private void showPreview(boolean show) {
    sashForm.setMaximizedControl(show ? null : treeViewer.getControl());
  }

  private void createTreeViewer(Composite parent) {
    treeViewer = new TreeViewer(parent, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL | SWT.BORDER);
    treeViewer.setUseHashlookup(true);
    treeViewer.setContentProvider(contentProvider);
    treeViewer.setLabelProvider(new DelegatingStyledCellLabelProvider(new ResultLabelProvider(contentProvider)));
    treeViewer.addOpenListener(event -> open(((IStructuredSelection) event.getSelection()).getFirstElement()));
    treeViewer.addSelectionChangedListener(event -> {
      MatchingLine matchingLine = getMatchingLine(event.getStructuredSelection().getFirstElement());
      if (matchingLine != null && sashForm.getMaximizedControl() == null) {
        previewPane.show(matchingLine);
      }
    });
    treeViewer.getControl().addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        if (e.character == SWT.DEL) {
          removeSelectedMatchesAction.run();
        } else if (e.stateMask == SWT.MOD1 && e.keyCode == 'c') {
          copySelection();
        } else if (e.keyCode == SWT.ARROW_DOWN) {
          e.doit = false;
          navigate(true);
        } else if (e.keyCode == SWT.ARROW_UP) {
          e.doit = false;
          navigate(false);
        }
      }
    });
    MenuManager menuMgr = new MenuManager("#PopUp");
    Menu menu = menuMgr.createContextMenu(treeViewer.getControl());
    treeViewer.getControl().setMenu(menu);
    menuMgr.setRemoveAllWhenShown(true);
    menuMgr.addMenuListener(mgr -> {
      IStructuredSelection selection = treeViewer.getStructuredSelection();
      mgr.add(new Action("Open") {
        @Override
        public void run() {
          open(selection.getFirstElement());
        }
      });
      mgr.add(new Action("Copy") {
        @Override
        public void run() {
          copySelection();
        }
      });
      mgr.add(removeSelectedMatchesAction);
      mgr.add(new Separator());
      Action replaceSelectedAction = new Action("Replace Selected...") {
        @Override
        public void run() {
          replace(true);
        }
      };
      replaceSelectedAction.setEnabled(currentRequest != null && !selection.isEmpty());
      mgr.add(replaceSelectedAction);
      mgr.add(new Separator(IWorkbenchActionConstants.MB_ADDITIONS));
    });
    getSite().registerContextMenu(menuMgr, treeViewer);
    getSite().setSelectionProvider(treeViewer);
  }

  private void initToolbar() {
    IToolBarManager toolBarManager = getViewSite().getActionBars().getToolBarManager();
    toolBarManager.add(createAction("Show Next Match", "elcl16/search_next.png", () -> navigate(true)));
    toolBarManager.add(createAction("Show Previous Match", "elcl16/search_prev.png", () -> navigate(false)));
    toolBarManager.add(new Separator());
    removeSelectedMatchesAction = createAction("Remove Selected Matches", "elcl16/search_rem.png", this::removeSelected);
    toolBarManager.add(removeSelectedMatchesAction);
    toolBarManager.add(new Separator());
    toolBarManager.add(createAction("Expand All", "elcl16/expandall.png", () -> treeViewer.expandAll()));
    toolBarManager.add(createAction("Collapse All", "elcl16/collapseall.png", () -> treeViewer.collapseAll()));
    toolBarManager.add(new Separator());
    searchAgainAction = createAction("Run the Search Again", "elcl16/refresh.png", this::searchAgain);
    searchAgainAction.setEnabled(false);
    toolBarManager.add(searchAgainAction);
    cancelSearchAction = createAction("Cancel the Search", "elcl16/stop.png", this::cancelSearch);
    cancelSearchAction.setEnabled(false);
    toolBarManager.add(cancelSearchAction);
    toolBarManager.add(new HistoryAction());
    toolBarManager.add(new Separator());
    toolBarManager.add(createToggleAction("Sort alphabetically", "elcl16/search_sortmatch.png", ALPHABETICAL_SORT, () -> {
      contentProvider.setAlphabeticalSort(Utils.getBoolean(ALPHABETICAL_SORT));
      treeViewer.refresh();
    }));
    toolBarManager.add(createToggleAction("Group by folder", "etool16/group_by_folder.png", GROUP_BY_FOLDER, () -> {
      contentProvider.setGroupByFolder(Utils.getBoolean(GROUP_BY_FOLDER));
      treeViewer.refresh();
      autoExpand();
    }));
  }

  private void initMenu() {
    IMenuManager menuManager = getViewSite().getActionBars().getMenuManager();
    menuManager.add(createToggleAction("Search as You Type", null, LIVE_SEARCH, () -> {
    }));
    menuManager.add(createToggleAction("Show Preview", null, SHOW_PREVIEW, () -> {
      showPreview(Utils.getBoolean(SHOW_PREVIEW));
      previewPane.show(getMatchingLine(treeViewer.getStructuredSelection().getFirstElement()));
    }));
    menuManager.add(new Separator());
    menuManager.add(createToggleAction("Search Hidden Files", null, SEARCH_HIDDEN, this::searchAgain));
    menuManager.add(createToggleAction("Use .gitignore and Other Ignore Files", null, USE_IGNORE_FILES, this::searchAgain));
    MenuManager contextMenu = new MenuManager("Lines of Context");
    for (int lines : CONTEXT_LINES_CHOICES) {
      Action action = new Action(String.valueOf(lines), IAction.AS_RADIO_BUTTON) {
        @Override
        public void run() {
          if (isChecked()) {
            Utils.getPreferences().putInt(CONTEXT_LINES, lines);
            Utils.savePreferences();
            searchAgain();
          }
        }
      };
      action.setChecked(Utils.getInt(CONTEXT_LINES) == lines);
      contextMenu.add(action);
    }
    menuManager.add(contextMenu);
    menuManager.add(new Separator());
    menuManager.add(new Action("Preferences...") {
      @Override
      public void run() {
        UiUtils.openPreferencePage();
      }
    });
  }

  private static Action createAction(String text, String searchIcon, Runnable runnable) {
    Action action = new Action(text) {
      @Override
      public void run() {
        runnable.run();
      }
    };
    action.setToolTipText(text);
    action.setImageDescriptor(getSearchImage(searchIcon));
    return action;
  }

  /**
   * An action which switches a boolean preference.
   */
  private static Action createToggleAction(String text, String searchIcon, String preference, Runnable runnable) {
    Action action = new Action(text, IAction.AS_CHECK_BOX) {
      @Override
      public void run() {
        Utils.getPreferences().putBoolean(preference, isChecked());
        Utils.savePreferences();
        runnable.run();
      }
    };
    action.setToolTipText(text);
    action.setChecked(Utils.getBoolean(preference));
    if (searchIcon != null) {
      action.setImageDescriptor(getSearchImage(searchIcon));
    }
    return action;
  }

  private static ImageDescriptor getSearchImage(String icon) {
    return createImageDescriptorFromURL("platform:/plugin/org.eclipse.search/icons/full/" + icon);
  }

  private void scheduleLiveSearch() {
    if (!updatingFields && Utils.getBoolean(LIVE_SEARCH)) {
      textField.getDisplay().timerExec(LIVE_SEARCH_DELAY, liveSearch);
    }
  }

  /**
   * @param live whether the search is started by the typing of the user rather than by its request
   */
  private void searchFromFields(boolean live) {
    String text = textField.getText();
    if (text.isEmpty()) {
      if (!live || treeViewer.getInput() != null) {
        cancelSearch();
        currentRequest = null;
        searchAgainAction.setEnabled(false);
        showResponse(null);
      }
      return;
    }
    Request request = createRequest(text);
    if (live && (text.length() < LIVE_SEARCH_MIN_LENGTH || request.isSameSearch(currentRequest))) {
      return;
    }
    searchFor(request, live);
  }

  private Request createRequest(String text) {
    Request request = new Request();
    request.setText(text);
    request.setCaseSensitive(caseSensitiveItem.getSelection());
    request.setWholeWord(wholeWordItem.getSelection());
    request.setRegularExpression(regularExpressionItem.getSelection());
    request.setFileGlobs(globField.getText());
    Scope scope = Scope.values()[Math.max(0, scopeCombo.getSelectionIndex())];
    request.setScope(scope, getScopePaths(scope));
    setViewOptions(request);
    return request;
  }

  private void setViewOptions(Request request) {
    request.setSearchHidden(Utils.getBoolean(SEARCH_HIDDEN));
    request.setUseIgnoreFiles(Utils.getBoolean(USE_IGNORE_FILES));
    request.setContextLines(Utils.getInt(CONTEXT_LINES));
  }

  private List<String> getScopePaths(Scope scope) {
    List<String> paths = new ArrayList<>();
    if (scope == Scope.SELECTION) {
      selectedResources.forEach(resource -> paths.add(resource.getFullPath().toString()));
    } else if (scope == Scope.PROJECT) {
      IEditorPart editor = getEditor();
      IResource resource = editor != null ? Adapters.adapt(editor.getEditorInput(), IResource.class) : null;
      if (resource != null) {
        paths.add(resource.getProject().getFullPath().toString());
      }
    }
    return paths;
  }

  /**
   * The editor shown in the editor area. It is not the active one after the view opened it.
   */
  private IEditorPart getEditor() {
    IWorkbenchPage page = getSite().getPage();
    if (page.getActiveEditor() != null) {
      return page.getActiveEditor();
    }
    for (IEditorReference reference : page.getEditorReferences()) {
      IEditorPart editor = reference.getEditor(false);
      if (editor != null && page.isPartVisible(editor)) {
        return editor;
      }
    }
    return null;
  }

  /**
   * Searches a text in the workspace.
   */
  public void searchFor(String text, boolean caseSensitive, boolean regularExpression) {
    Request request = new Request();
    request.setText(text);
    request.setCaseSensitive(caseSensitive);
    request.setRegularExpression(regularExpression);
    setViewOptions(request);
    searchFor(request);
  }

  public void searchFor(Request request) {
    searchFor(request, false);
  }

  private void searchFor(Request request, boolean live) {
    cancelSearch();
    if (liveRequest != null) {
      history.remove(liveRequest);
    }
    liveRequest = live ? request : null;
    request.setTime(System.currentTimeMillis());
    Response response = new Response();
    history.add(request, response);
    history.save();
    setCurrent(request, response);
    if (request.getScope() != Scope.WORKSPACE && request.getScopePaths().isEmpty()) {
      response.getErrors().add(new Error(request.getScope() == Scope.SELECTION
          ? "No resource is selected: select projects, folders or files in another view."
          : "The active editor does not edit a file of the workspace."));
      response.setState(Response.State.DONE);
      refreshResults();
      return;
    }
    Job job = Job.create("Searching for \"" + request.getText() + "\" with RipGrep ...", monitor -> {
      Engine.search(request, response, Utils.getSettings(), () -> dirty.set(true), monitor);
      return monitor.isCanceled() ? Status.CANCEL_STATUS : Status.OK_STATUS;
    });
    job.addJobChangeListener(new JobChangeAdapter() {
      @Override
      public void done(IJobChangeEvent event) {
        Display.getDefault().asyncExec(() -> searchDone(job));
      }
    });
    // the searches started while typing do not flash in the progress of the workbench
    job.setSystem(live);
    currentJob = job;
    cancelSearchAction.setEnabled(true);
    setTitleImage(runningImage);
    updateStatus();
    job.schedule();
    Display.getCurrent().timerExec(REFRESH_DELAY, refresher);
  }

  private void searchDone(Job job) {
    if (job != currentJob || treeViewer.getControl().isDisposed()) {
      return;
    }
    currentJob = null;
    cancelSearchAction.setEnabled(false);
    setTitleImage(getSite().getPage().getActivePart() == this ? image : doneImage);
    refreshResults();
  }

  private void searchAgain() {
    if (currentRequest != null) {
      Request request = new Request(currentRequest);
      setViewOptions(request);
      searchFor(request);
    }
  }

  private void cancelSearch() {
    if (currentJob != null) {
      currentJob.cancel();
      currentJob = null;
      cancelSearchAction.setEnabled(false);
      setTitleImage(image);
      updateStatus();
    }
  }

  /**
   * Whether a search is running.
   */
  public boolean isSearching() {
    return currentJob != null;
  }

  /**
   * Shows a search and its results.
   */
  public void setCurrent(Request searchRequest, Response response) {
    this.currentRequest = searchRequest;
    searchAgainAction.setEnabled(true);
    updatingFields = true;
    try {
      if (!textField.getText().equals(searchRequest.getText())) {
        textField.setText(searchRequest.getText());
      }
      caseSensitiveItem.setSelection(searchRequest.isCaseSensitive());
      wholeWordItem.setSelection(searchRequest.isWholeWord());
      regularExpressionItem.setSelection(searchRequest.isRegularExpression());
      if (!globField.getText().equals(searchRequest.getFileGlobs())) {
        globField.setText(searchRequest.getFileGlobs());
      }
      scopeCombo.select(searchRequest.getScope().ordinal());
    } finally {
      updatingFields = false;
    }
    showResponse(response);
  }

  private void showResponse(Response response) {
    treeViewer.setInput(response);
    previewPane.show(null);
    autoExpand();
    updateStatus();
  }

  public Response getCurrent() {
    return (Response) treeViewer.getInput();
  }

  public TreeViewer getTreeViewer() {
    return treeViewer;
  }

  private void refreshResults() {
    treeViewer.refresh();
    autoExpand();
    updateStatus();
  }

  /**
   * Expands the results when there are few of them.
   */
  private void autoExpand() {
    Response response = getCurrent();
    if (response == null) {
      return;
    }
    int lines = 0;
    for (SearchedProject searchedProject : response.getSearchedProjects()) {
      for (MatchingFile matchingFile : List.copyOf(searchedProject.getMatchingFiles())) {
        lines += matchingFile.getMatchingLines().size();
        if (lines > AUTO_EXPAND_MAX_LINES) {
          return;
        }
      }
    }
    treeViewer.expandAll();
  }

  private void updateStatus() {
    statusLink.setText(getStatus());
    statusLink.getParent().layout();
  }

  private String getStatus() {
    Response response = treeViewer != null ? getCurrent() : null;
    if (response == null) {
      return "";
    } else if (response.isRipGrepMissing()) {
      return "RipGrep was not found: <a href=\"preferences\">set its location</a> or <a href=\"get\">install it</a>.";
    }
    NumberFormat format = NumberFormat.getIntegerInstance();
    int matchCount = response.getMatchCount();
    int fileCount = response.getFileCount();
    StringBuilder status = new StringBuilder();
    if (response.getState() == Response.State.RUNNING && currentJob != null) {
      status.append("Searching... ");
    }
    if (matchCount == 0) {
      status.append(response.getState() == Response.State.RUNNING ? "" : "No match");
    } else {
      status.append(format.format(matchCount)).append(matchCount == 1 ? " match in " : " matches in ")
          .append(format.format(fileCount)).append(fileCount == 1 ? " file" : " files");
    }
    if (response.getState() != Response.State.RUNNING) {
      if (response.getSearchedFiles() >= 0) {
        status.append(" - ").append(format.format(response.getSearchedFiles())).append(" files searched");
      }
      status.append(" - ").append(format.format(response.getElapsedMillis())).append(" ms");
      if (response.isLimitReached()) {
        status.append(" - stopped at the <a href=\"preferences\">limit</a>");
      } else if (response.getState() == Response.State.CANCELED) {
        status.append(" - canceled");
      }
    }
    return status.toString();
  }

  /**
   * The status shown under the search fields, without the markup of its links.
   */
  public String getStatusText() {
    return statusLink.getText().replaceAll("<[^>]*>", "");
  }

  private static MatchingLine getMatchingLine(Object element) {
    if (element instanceof MatchingFile matchingFile) {
      return List.copyOf(matchingFile.getMatchingLines()).stream().filter(line -> !line.isContext()).findFirst().orElse(null);
    }
    return element instanceof MatchingLine matchingLine ? matchingLine : null;
  }

  private void open(Object element) {
    MatchingLine matchingLine = getMatchingLine(element);
    if (matchingLine != null) {
      SafeRunner.run(() -> showMatchingLine(matchingLine));
    }
  }

  private void showMatchingLine(MatchingLine matchingLine) throws CoreException {
    // the search is used: it stays in the history
    liveRequest = null;
    MatchingFile matchingFile = matchingLine.getMatchingFile();
    IEditorPart editorPart;
    if (matchingFile.getMatchingResource() instanceof IFile file) {
      // a match can only be shown in an editor of Eclipse: not in the external program of the file type
      IEditorDescriptor descriptor = IDE.getEditorDescriptor(file, true, true);
      String editorId = descriptor == null || descriptor.isOpenExternal() || descriptor.isOpenInPlace()
          ? EditorsUI.DEFAULT_TEXT_EDITOR_ID
          : descriptor.getId();
      editorPart = getSite().getPage().openEditor(new FileEditorInput(file), editorId, false);
    } else {
      editorPart = IDE.openInternalEditorOnFileStore(getSite().getPage(),
          EFS.getLocalFileSystem().getStore(new Path(matchingFile.getFilePath())));
      getSite().getPage().activate(this);
    }
    ITextEditor textEditor = Adapters.adapt(editorPart, ITextEditor.class);
    IDocument document = textEditor != null && textEditor.getDocumentProvider() != null
        ? textEditor.getDocumentProvider().getDocument(textEditor.getEditorInput())
        : null;
    if (document != null) {
      int lineOffset = TextOffsets.lineOffset(document.get(), matchingLine.getLineNumber());
      if (lineOffset >= 0) {
        Span span = matchingLine.getSpans().isEmpty() ? new Span(0, 0) : matchingLine.getSpans().get(0);
        int offset = Math.min(lineOffset + span.start(), document.getLength());
        textEditor.selectAndReveal(offset, Math.min(span.length(), document.getLength() - offset));
      }
    }
  }

  /**
   * Selects the next or the previous match and shows it in its editor.
   */
  private void navigate(boolean next) {
    List<TreePath> paths = contentProvider.getMatchingLinePaths(getCurrent());
    if (paths.isEmpty()) {
      return;
    }
    TreePath[] selection = treeViewer.getStructuredSelection().getPaths();
    int index = next ? 0 : paths.size() - 1;
    if (selection.length > 0) {
      TreePath selected = selection[0];
      if (selected.getLastSegment() instanceof MatchingLine matchingLine && matchingLine.isContext()) {
        selected = selected.getParentPath();
      }
      for (int i = 0; i < paths.size(); i++) {
        if (paths.get(i).startsWith(selected, null)) {
          // from a project, a folder or a file, the next match is its first one
          boolean onMatch = paths.get(i).getSegmentCount() == selected.getSegmentCount();
          index = next ? (onMatch ? i + 1 : i) : i - 1;
          break;
        }
      }
    }
    TreePath path = paths.get(Math.floorMod(index, paths.size()));
    treeViewer.setSelection(new TreeSelection(path), true);
    open(path.getLastSegment());
  }

  private void removeSelected() {
    treeViewer.getStructuredSelection().forEach(this::remove);
    treeViewer.refresh();
    updateStatus();
  }

  private void remove(Object element) {
    if (element instanceof SearchedProject searchedProject) {
      searchedProject.getResponse().getSearchedProjects().remove(searchedProject);
    } else if (element instanceof MatchingFile matchingFile) {
      SearchedProject searchedProject = matchingFile.getSearchProject();
      searchedProject.getMatchingFiles().remove(matchingFile);
      if (searchedProject.getMatchingFiles().isEmpty()) {
        remove(searchedProject);
      }
    } else if (element instanceof MatchingLine matchingLine) {
      MatchingFile matchingFile = matchingLine.getMatchingFile();
      matchingFile.getMatchingLines().remove(matchingLine);
      if (matchingFile.getMatchCount() == 0) {
        remove(matchingFile);
      }
    } else if (element instanceof Folder folder) {
      SearchedProject searchedProject = folder.searchProject();
      searchedProject.getMatchingFiles().removeIf(folder::contains);
      if (searchedProject.getMatchingFiles().isEmpty()) {
        remove(searchedProject);
      }
    } else if (element instanceof SeeAll seeAll) {
      for (Object child : contentProvider.getChildren(seeAll)) {
        remove(child);
      }
    } else if (element instanceof Error error && getCurrent() != null) {
      getCurrent().getErrors().remove(error);
    }
  }

  /**
   * The text copied from the selected results, a line for each one.
   */
  String getSelectionText() {
    StringBuilder sb = new StringBuilder();
    for (Object element : treeViewer.getStructuredSelection().toArray()) {
      if (element instanceof MatchingLine matchingLine) {
        sb.append(matchingLine.getMatchingFile().getFilePath())
          .append(":")
          .append(matchingLine.getLineNumber())
          .append(": ")
          .append(matchingLine.getLine());
      } else if (element instanceof MatchingFile matchingFile) {
        sb.append(matchingFile.getFilePath());
      } else if (element instanceof Folder folder) {
        sb.append(folder.path());
      } else if (element instanceof SearchedProject searchedProject) {
        sb.append(searchedProject.getName());
      } else if (element instanceof Error error) {
        sb.append(error.getError());
      } else {
        continue;
      }
      sb.append(System.lineSeparator());
    }
    return sb.toString();
  }

  private void copySelection() {
    String text = getSelectionText();
    if (text.isEmpty()) {
      return;
    }
    Clipboard clipboard = new Clipboard(Display.getDefault());
    try {
      clipboard.setContents(
          new Object[] { text },
          new Transfer[] { TextTransfer.getInstance() });
    } finally {
      clipboard.dispose();
    }
  }

  /**
   * Opens the preview of the replacement of the matches which are still in the view.
   *
   * @param selectionOnly whether only the selected results are replaced
   */
  private void replace(boolean selectionOnly) {
    if (currentRequest == null || getCurrent() == null) {
      return;
    }
    if (!replaceItem.getSelection()) {
      // the replacement must be seen before it is applied
      replaceItem.setSelection(true);
      showReplace(true);
      replaceField.setFocus();
      return;
    }
    if (!PlatformUI.getWorkbench().saveAllEditors(true)) {
      return;
    }
    ReplaceRefactoring refactoring = createReplaceRefactoring(replaceField.getText(), selectionOnly);
    RefactoringWizard wizard = new RefactoringWizard(refactoring,
        RefactoringWizard.DIALOG_BASED_USER_INTERFACE | RefactoringWizard.PREVIEW_EXPAND_FIRST_NODE) {
      @Override
      protected void addUserInputPages() {
      }
    };
    wizard.setDefaultPageTitle("Replace with RipGrep");
    try {
      if (new RefactoringWizardOpenOperation(wizard).run(getSite().getShell(), "Replace with RipGrep") == IDialogConstants.OK_ID) {
        searchAgain();
      }
    } catch (InterruptedException e) {
      // canceled
    }
  }

  /**
   * @param selectionOnly whether only the selected results are replaced, rather than all the results of the view
   */
  public ReplaceRefactoring createReplaceRefactoring(String replacement, boolean selectionOnly) {
    List<MatchingLine> matchingLines = new ArrayList<>();
    if (selectionOnly) {
      treeViewer.getStructuredSelection().forEach(element -> collectMatchingLines(element, matchingLines));
    } else {
      collectMatchingLines(getCurrent(), matchingLines);
    }
    Set<String> keys = new HashSet<>();
    matchingLines.forEach(matchingLine -> keys.add(getKey(matchingLine)));
    Request request = new Request(currentRequest);
    request.setReplacement(replacement);
    request.setContextLines(0);
    return new ReplaceRefactoring(request, Utils.getSettings(), matchingLine -> keys.contains(getKey(matchingLine)));
  }

  private static String getKey(MatchingLine matchingLine) {
    return matchingLine.getMatchingFile().getFilePath() + ":" + matchingLine.getLineNumber();
  }

  private void collectMatchingLines(Object element, Collection<MatchingLine> matchingLines) {
    if (element instanceof MatchingLine matchingLine) {
      matchingLines.add(matchingLine);
    } else if (element instanceof MatchingFile matchingFile) {
      matchingLines.addAll(List.copyOf(matchingFile.getMatchingLines()));
    } else if (element instanceof Response response) {
      response.getSearchedProjects().forEach(searchedProject -> collectMatchingLines(searchedProject, matchingLines));
    } else if (element instanceof SearchedProject searchedProject) {
      List.copyOf(searchedProject.getMatchingFiles()).forEach(matchingFile -> collectMatchingLines(matchingFile, matchingLines));
    } else if (element instanceof Folder folder) {
      List.copyOf(folder.searchProject().getMatchingFiles()).stream().filter(folder::contains)
          .forEach(matchingFile -> collectMatchingLines(matchingFile, matchingLines));
    } else if (element instanceof SeeAll seeAll) {
      for (Object child : contentProvider.getChildren(seeAll)) {
        collectMatchingLines(child, matchingLines);
      }
    }
  }

  /**
   * Puts the focus in the search field, to type a search.
   */
  public void focusSearchField() {
    textField.setFocus();
    textField.selectAll();
  }

  @Override
  public void setFocus() {
    if (currentJob == null) {
      setTitleImage(image);
    }
    // a click in a field of the view also activates the view: the focus stays where the user put it
    for (Control control = Display.getCurrent().getFocusControl(); control != null; control = control.getParent()) {
      if (control == sashForm.getParent()) {
        return;
      }
    }
    if (getCurrent() != null && getCurrent().getFileCount() > 0) {
      treeViewer.getControl().setFocus();
    } else {
      textField.setFocus();
    }
  }

  @Override
  public void dispose() {
    if (currentJob != null) {
      currentJob.cancel();
      currentJob = null;
    }
    getSite().getPage().removeSelectionListener(selectionListener);
    super.dispose();
  }

  public static History getHistory() {
    return history;
  }

  public void clearHistory() {
    cancelSearch();
    searchAgainAction.setEnabled(false);
    currentRequest = null;
    liveRequest = null;
    updatingFields = true;
    textField.setText("");
    updatingFields = false;
    history.clear();
    history.save();
    showResponse(null);
  }

  /**
   * The drop down of the last searches.
   */
  private class HistoryAction extends Action implements IMenuCreator {

    private Menu menu;

    HistoryAction() {
      super("Show Previous Searches", IAction.AS_DROP_DOWN_MENU);
      setToolTipText("Show Previous Searches");
      setImageDescriptor(getSearchImage("elcl16/search_history.png"));
      setMenuCreator(this);
    }

    @Override
    public void dispose() {
      if (menu != null) {
        menu.dispose();
      }
    }

    @Override
    public Menu getMenu(Menu parent) {
      return null;
    }

    @Override
    public Menu getMenu(Control parent) {
      dispose();
      menu = new Menu(parent);
      for (Map.Entry<Request, Response> search : history.getSearches()) {
        Request request = search.getKey();
        Response response = search.getValue();
        Action action = new Action(getLabel(request), IAction.AS_RADIO_BUTTON) {
          @Override
          public void run() {
            if (!isChecked()) {
              return;
            }
            liveRequest = null;
            if (response != null) {
              cancelSearch();
              setCurrent(request, response);
            } else {
              searchFor(new Request(request));
            }
          }
        };
        action.setChecked(response != null && response == getCurrent());
        new ActionContributionItem(action).fill(menu, -1);
      }
      if (!history.isEmpty()) {
        new Separator().fill(menu, -1);
      }
      Action clearAction = new Action("Clear History") {
        @Override
        public void run() {
          if (MessageDialog.openConfirm(getSite().getShell(), "Clear History", "Remove all the searches from the history?")) {
            clearHistory();
          }
        }
      };
      clearAction.setEnabled(!history.isEmpty());
      new ActionContributionItem(clearAction).fill(menu, -1);
      return menu;
    }

    private String getLabel(Request request) {
      // "&" is the mnemonic and "@" the accelerator of the label of an action
      String label = request.getText().replace("&", "&&").replace('\n', ' ');
      if (label.length() > 60) {
        label = label.substring(0, 60) + "…";
      }
      if (request.getTime() != -1) {
        label += " (" + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(request.getTime())) + ")";
      }
      return label.indexOf('@') >= 0 ? label + '@' : label;
    }

    @Override
    public void runWithEvent(Event event) {
      // a click on the button opens the menu, as a click on its arrow
      if (event.widget instanceof ToolItem item) {
        Rectangle bounds = item.getBounds();
        Menu historyMenu = getMenu(item.getParent());
        historyMenu.setLocation(item.getParent().toDisplay(bounds.x, bounds.y + bounds.height));
        historyMenu.setVisible(true);
      }
    }
  }
}
