package org.eclipse.eripgrep.ui;

import static org.eclipse.eripgrep.TestWorkspace.*;
import static org.eclipse.eripgrep.utils.PreferenceConstantes.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.BooleanSupplier;

import org.eclipse.core.resources.*;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.eripgrep.model.*;
import org.eclipse.eripgrep.ui.model.Folder;
import org.eclipse.eripgrep.utils.Utils;
import org.eclipse.jface.action.ActionContributionItem;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.preference.PreferenceDialog;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.viewers.*;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.widgets.*;
import org.eclipse.ui.*;
import org.eclipse.ui.dialogs.PreferencesUtil;
import org.eclipse.ui.handlers.IHandlerService;
import org.eclipse.ui.texteditor.ITextEditor;
import org.junit.jupiter.api.*;

/**
 * Drives the view in the workbench of the tests.
 */
class ERipGrepViewPartTest {

  private IProject project;
  private IWorkbenchPage page;
  private ERipGrepViewPart view;

  @BeforeEach
  void openView() throws CoreException {
    settings();
    deleteProjects();
    for (String preference : List.of(ALPHABETICAL_SORT, GROUP_BY_FOLDER, CONTEXT_LINES, LIVE_SEARCH, SHOW_PREVIEW, SCOPE, FILE_GLOBS,
        CASE_SENSITIVE, WHOLE_WORD, REGULAR_EXPRESSION, SHOW_REPLACE, HISTORY)) {
      Utils.getPreferences().remove(preference);
    }
    project = createProject("demo");
    createFile(project, "src/org/demo/Greeter.java",
        "package org.demo;\n\npublic class Greeter {\n\n  public String greet(String name) {\n    return \"Hello \" + name;\n  }\n}\n");
    createFile(project, "src/org/demo/Main.java",
        "package org.demo;\n\npublic class Main {\n\n  public static void main(String[] args) {\n    Greeter greeter = new Greeter();\n"
            + "    System.out.println(greeter.greet(\"world\"));\n  }\n}\n");
    createFile(project, "README.md", "# Demo\n\nThe Greeter says hello.\n");
    page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
    IViewPart intro = page.findView("org.eclipse.ui.internal.introview");
    if (intro != null) {
      page.hideView(intro);
    }
    view = (ERipGrepViewPart) page.showView(ERipGrepViewPart.ID);
    ERipGrepViewPart.getHistory().clear();
  }

  @AfterEach
  void closeView() throws CoreException {
    page.closeAllEditors(false);
    page.hideView(view);
    waitFor(() -> false, 100);
    deleteProjects();
  }

  /**
   * Runs the events of the display until the condition is true.
   */
  private static boolean waitFor(BooleanSupplier condition, long timeout) {
    Display display = Display.getCurrent();
    long end = System.currentTimeMillis() + timeout;
    while (!condition.getAsBoolean()) {
      if (System.currentTimeMillis() > end) {
        return false;
      }
      if (!display.readAndDispatch()) {
        try {
          Thread.sleep(10);
        } catch (InterruptedException e) {
          return false;
        }
      }
    }
    return true;
  }

  private void search(Request request) {
    view.searchFor(request);
    waitForSearch();
  }

  private void waitForSearch() {
    assertTrue(waitFor(() -> !view.isSearching(), 20000), "The search does not end");
    waitFor(() -> false, 50);
  }

  private static Request request(String text) {
    Request request = new Request();
    request.setText(text);
    request.setCaseSensitive(true);
    return request;
  }

  private Text field(String message) {
    return find(view.getTreeViewer().getControl().getParent().getParent(), Text.class, text -> message.equals(text.getMessage()));
  }

  private static <T extends Widget> T find(Control control, Class<T> type, java.util.function.Predicate<T> filter) {
    if (type.isInstance(control) && filter.test(type.cast(control))) {
      return type.cast(control);
    }
    if (control instanceof Composite composite) {
      for (Control child : composite.getChildren()) {
        T found = find(child, type, filter);
        if (found != null) {
          return found;
        }
      }
    }
    return null;
  }

  /**
   * The labels of the expanded tree, indented by level.
   */
  private List<String> tree() {
    List<String> labels = new ArrayList<>();
    collect(view.getTreeViewer().getTree().getItems(), "", labels);
    return labels;
  }

  private static void collect(TreeItem[] items, String indentation, List<String> labels) {
    for (TreeItem item : items) {
      labels.add(indentation + item.getText());
      if (item.getExpanded()) {
        collect(item.getItems(), indentation + "  ", labels);
      }
    }
  }

  private void select(Object element) {
    view.getTreeViewer().setSelection(new StructuredSelection(element), true);
  }

  private MatchingFile file(String name) {
    for (SearchedProject searchedProject : view.getCurrent().getSearchedProjects()) {
      for (MatchingFile matchingFile : searchedProject.getMatchingFiles()) {
        if (matchingFile.getFileName().equals(name)) {
          return matchingFile;
        }
      }
    }
    throw new NoSuchElementException(name);
  }

  private static void pressKey(Control control, int keyCode, char character, int stateMask) {
    Event event = new Event();
    event.keyCode = keyCode;
    event.character = character;
    event.stateMask = stateMask;
    control.notifyListeners(SWT.KeyDown, event);
  }

  @Test
  void showsTheResults() {
    Request request = request("Greeter");
    request.setFileGlobs("*.java");
    search(request);
    assertEquals(List.of(
        "demo (3 matches)",
        "  Greeter.java - src/org/demo (1)",
        "    3: public class Greeter {",
        "  Main.java - src/org/demo (2)",
        "    6: Greeter greeter = new Greeter();"), sorted(tree()));
    assertTrue(view.getStatusText().startsWith("3 matches in 2 files - 2 files searched - "), view.getStatusText());
    assertEquals("Greeter", field("Search").getText());
    assertEquals("*.java", field("Files to include or exclude, e.g. *.java, !test/").getText());
    assertSame(view.getCurrent(), ERipGrepViewPart.getHistory().getSearches().get(0).getValue());
  }

  /**
   * RipGrep gives the files in the order it finds them: the tests sort them.
   */
  private List<String> sorted(List<String> tree) {
    Utils.getPreferences().putBoolean(ALPHABETICAL_SORT, true);
    view.getTreeViewer().getContentProvider();
    ((ResultContentProvider) view.getTreeViewer().getContentProvider()).setAlphabeticalSort(true);
    view.getTreeViewer().refresh();
    view.getTreeViewer().expandAll();
    return tree();
  }

  @Test
  void groupsByFolderAndShowsTheContext() {
    Request request = request("Hello");
    request.setContextLines(1);
    search(request);
    ((ResultContentProvider) view.getTreeViewer().getContentProvider()).setGroupByFolder(true);
    view.getTreeViewer().refresh();
    view.getTreeViewer().expandAll();
    assertEquals(List.of(
        "demo (1 match)",
        "  src/org/demo",
        "    Greeter.java (1)",
        "      5: public String greet(String name) {",
        "      6: return \"Hello \" + name;",
        "      7: }"), tree());
    assertTrue(view.getStatusText().startsWith("1 match in 1 file - "), view.getStatusText());
  }

  @Test
  void opensAMatchInItsEditor() {
    search(request("greet("));
    MatchingFile main = file("Main.java");
    select(main);
    view.getTreeViewer().getTree().notifyListeners(SWT.DefaultSelection, defaultSelection());
    assertTrue(waitFor(() -> editor() != null, 10000));
    ITextEditor editor = editor();
    assertEquals("Main.java", editor.getTitle());
    ITextSelection selection = (ITextSelection) editor.getSelectionProvider().getSelection();
    assertEquals("greet(", selection.getText());
    assertEquals(6, selection.getStartLine());
  }

  /**
   * The editor shown in the editor area: the view keeps the focus when it opens a match.
   */
  private ITextEditor editor() {
    for (IEditorReference reference : page.getEditorReferences()) {
      IEditorPart editor = reference.getEditor(false);
      if (editor != null && page.isPartVisible(editor)) {
        return (ITextEditor) editor;
      }
    }
    return null;
  }

  private Event defaultSelection() {
    Event event = new Event();
    event.item = view.getTreeViewer().getTree().getSelection()[0];
    return event;
  }

  @Test
  void navigatesBetweenTheMatches() {
    search(request("Greeter"));
    sorted(tree());
    Tree tree = view.getTreeViewer().getTree();
    List<String> visited = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      pressKey(tree, SWT.ARROW_DOWN, (char) 0, 0);
      MatchingLine matchingLine = (MatchingLine) view.getTreeViewer().getStructuredSelection().getFirstElement();
      visited.add(matchingLine.getMatchingFile().getFileName() + ":" + matchingLine.getLineNumber());
    }
    // goes back to the first match after the last one
    assertEquals(List.of("README.md:3", "Greeter.java:3", "Main.java:6", "README.md:3", "Greeter.java:3"), visited);
    assertEquals("Greeter.java", editor().getTitle());
    pressKey(tree, SWT.ARROW_UP, (char) 0, 0);
    pressKey(tree, SWT.ARROW_UP, (char) 0, 0);
    assertEquals("Main.java", editor().getTitle());
    ITextSelection selection = (ITextSelection) editor().getSelectionProvider().getSelection();
    assertEquals("Greeter", selection.getText());
    // from a file, the next match is its first one
    select(file("Greeter.java"));
    pressKey(tree, SWT.ARROW_DOWN, (char) 0, 0);
    assertSame(file("Greeter.java").getMatchingLines().get(0), view.getTreeViewer().getStructuredSelection().getFirstElement());
  }

  @Test
  void removesTheSelectedResults() {
    search(request("Greeter"));
    assertEquals(4, view.getCurrent().getMatchCount());
    Tree tree = view.getTreeViewer().getTree();
    select(file("Main.java").getMatchingLines().get(0));
    pressKey(tree, SWT.DEL, SWT.DEL, 0);
    // the file had a single line
    assertEquals(2, view.getCurrent().getFileCount());
    assertTrue(view.getStatusText().startsWith("2 matches in 2 files"), view.getStatusText());
    select(file("README.md"));
    pressKey(tree, SWT.DEL, SWT.DEL, 0);
    assertEquals(1, view.getCurrent().getFileCount());
    ((ResultContentProvider) view.getTreeViewer().getContentProvider()).setGroupByFolder(true);
    view.getTreeViewer().refresh();
    select(new Folder(view.getCurrent().getSearchedProjects().peek(), "", "src/org/demo"));
    pressKey(tree, SWT.DEL, SWT.DEL, 0);
    assertEquals(0, view.getCurrent().getSearchedProjects().size());
    assertEquals(List.of(), tree());
  }

  @Test
  void copiesTheSelection() {
    search(request("Hello"));
    MatchingFile greeter = file("Greeter.java");
    view.getTreeViewer().setSelection(new StructuredSelection(List.of(greeter, greeter.getMatchingLines().get(0))), true);
    String path = greeter.getFilePath();
    assertEquals(path + System.lineSeparator() + path + ":6:     return \"Hello \" + name;" + System.lineSeparator(),
        view.getSelectionText());
    select(view.getCurrent().getSearchedProjects().peek());
    assertEquals("demo" + System.lineSeparator(), view.getSelectionText());
  }

  @Test
  void searchesWhileTyping() {
    Text search = field("Search");
    search.setText("gr");
    waitFor(() -> false, 600);
    // too short to search while typing
    assertNull(view.getCurrent());
    search.setText("gree");
    search.setText("greet(");
    assertTrue(waitFor(() -> view.getCurrent() != null, 5000));
    waitForSearch();
    assertEquals(2, view.getCurrent().getMatchCount());
    search.setText("greeter.");
    assertTrue(waitFor(() -> view.getCurrent() != null && view.getCurrent().getMatchCount() == 1 && !view.isSearching(), 5000));
    // only the last typed search is kept
    assertEquals(List.of("greeter."), ERipGrepViewPart.getHistory().getSearches().stream().map(entry -> entry.getKey().getText()).toList());
    search.setText("");
    assertTrue(waitFor(() -> view.getCurrent() == null, 5000));
    assertEquals("", view.getStatusText());
  }

  @Test
  void searchesTheTextTypedWithEnter() {
    Utils.getPreferences().putBoolean(LIVE_SEARCH, false);
    Text search = field("Search");
    search.setText("hello");
    waitFor(() -> false, 500);
    assertNull(view.getCurrent());
    search.notifyListeners(SWT.DefaultSelection, new Event());
    waitForSearch();
    // case sensitive by default
    assertEquals(1, view.getCurrent().getMatchCount());
    assertEquals("README.md", view.getCurrent().getSearchedProjects().peek().getMatchingFiles().get(0).getFileName());
  }

  @Test
  void searchesInTheSelectedResources() throws CoreException {
    IProject other = createProject("other");
    createFile(other, "Greeter.txt", "Greeter\n");
    Request request = request("Greeter");
    request.setScope(Scope.SELECTION, List.of("/other"));
    search(request);
    assertEquals(List.of("other (1 match)", "  Greeter.txt (1)", "    1: Greeter"), tree());
    request = request("Greeter");
    request.setScope(Scope.SELECTION, List.of());
    search(request);
    assertEquals(List.of("No resource is selected: select projects, folders or files in another view."), tree());
  }

  @Test
  void theCommandSearchesTheSelectedText() throws Exception {
    IFile file = project.getFile("src/org/demo/Main.java");
    ITextEditor editor = (ITextEditor) org.eclipse.ui.ide.IDE.openEditor(page, file, "org.eclipse.ui.DefaultTextEditor");
    editor.selectAndReveal(read(file).indexOf("println"), "println".length());
    page.activate(editor);
    PlatformUI.getWorkbench().getService(IHandlerService.class).executeCommand("org.eclipse.eripgrep.SearchWithERipGrepCommand", null);
    waitForSearch();
    assertEquals("println", field("Search").getText());
    assertEquals(List.of("demo (1 match)", "  Main.java - src/org/demo (1)", "    7: System.out.println(greeter.greet(\"world\"));"), tree());
  }

  @Test
  void replacesTheResultsOfTheView() throws CoreException {
    search(request("Greeter"));
    // the removed results are not replaced
    select(file("README.md"));
    pressKey(view.getTreeViewer().getTree(), SWT.DEL, SWT.DEL, 0);
    ReplaceRefactoring refactoring = view.createReplaceRefactoring("Welcomer", false);
    assertTrue(perform(refactoring).getConditionCheckingStatus().isOK());
    assertEquals(3, refactoring.getReplacementCount());
    assertTrue(read(project.getFile("src/org/demo/Main.java")).contains("Welcomer greeter = new Welcomer();"));
    assertEquals("# Demo\n\nThe Greeter says hello.\n", read(project.getFile("README.md")));
    // only the selection
    search(request("greet"));
    select(file("Main.java"));
    refactoring = view.createReplaceRefactoring("welcome", true);
    assertTrue(perform(refactoring).getConditionCheckingStatus().isOK());
    assertTrue(read(project.getFile("src/org/demo/Main.java")).contains("Welcomer welcomeer = new Welcomer();"));
    assertTrue(read(project.getFile("src/org/demo/Greeter.java")).contains("greet(String name)"));
  }

  @Test
  void showsThatRipGrepIsMissing() {
    Utils.getPreferences().put(RIPGREP_PATH, new File("missing-rg").getAbsolutePath());
    try {
      search(request("Greeter"));
      assertEquals("RipGrep was not found: set its location or install it.", view.getStatusText());
      assertEquals(1, tree().size());
    } finally {
      Utils.getPreferences().remove(RIPGREP_PATH);
    }
  }

  @Test
  void showsThePreviewOfTheSelectedMatch() {
    search(request("greet("));
    select(file("Main.java").getMatchingLines().get(0));
    StyledText preview = find(view.getTreeViewer().getControl().getParent(), StyledText.class, text -> true);
    assertEquals("1  package org.demo;\n2  \n3  public class Main {\n4  \n5    public static void main(String[] args) {\n"
        + "6      Greeter greeter = new Greeter();\n7      System.out.println(greeter.greet(\"world\"));\n8    }\n9  }", preview.getText());
    StyleRange match = Arrays.stream(preview.getStyleRanges()).filter(range -> range.background != null).findFirst().orElseThrow();
    assertEquals("greet(", preview.getText(match.start, match.start + match.length - 1));
    assertNotNull(preview.getLineBackground(6));
    assertNull(preview.getLineBackground(5));
    // the file is deleted: only the line found by RipGrep is shown
    new File(file("Greeter.java").getFilePath()).delete();
    select(file("Greeter.java"));
    assertEquals("1    public String greet(String name) {", preview.getText());
  }

  @Test
  void showsTheHistory() {
    search(request("Greeter"));
    Response first = view.getCurrent();
    Request second = request("a & b @ c");
    second.setTime(0);
    search(second);
    IToolBarManager toolBar = view.getViewSite().getActionBars().getToolBarManager();
    IAction history = Arrays.stream(toolBar.getItems()).filter(ActionContributionItem.class::isInstance)
        .map(item -> ((ActionContributionItem) item).getAction()).filter(action -> action.getMenuCreator() != null).findFirst().orElseThrow();
    Menu menu = history.getMenuCreator().getMenu(view.getTreeViewer().getControl());
    List<String> labels = Arrays.stream(menu.getItems()).map(MenuItem::getText).toList();
    assertEquals(4, labels.size());
    // the "&" and the "@" of the text are not the mnemonic and the accelerator of the item
    assertTrue(labels.get(0).startsWith("a && b @ c ("), labels.get(0));
    assertTrue(labels.get(1).startsWith("Greeter ("), labels.get(1));
    assertEquals("Clear History", labels.get(3));
    assertTrue(menu.getItem(0).getSelection());
    // shows the results of the first search again, without running it
    menu.getItem(0).setSelection(false);
    menu.getItem(1).setSelection(true);
    menu.getItem(1).notifyListeners(SWT.Selection, new Event());
    assertSame(first, view.getCurrent());
    assertEquals("Greeter", field("Search").getText());
    assertFalse(view.isSearching());
    view.clearHistory();
    assertNull(view.getCurrent());
    assertEquals("", field("Search").getText());
    assertTrue(ERipGrepViewPart.getHistory().isEmpty());
    assertEquals(1, history.getMenuCreator().getMenu(view.getTreeViewer().getControl()).getItemCount());
  }

  @Test
  void searchesInTheScopeChosenInTheView() throws Exception {
    IProject other = createProject("other");
    createFile(other, "Greeter.txt", "Greeter\n");
    IViewPart explorer = page.showView(IPageLayout.ID_PROJECT_EXPLORER);
    explorer.getSite().getSelectionProvider().setSelection(new StructuredSelection(other));
    page.activate(view);
    Combo scope = find(view.getTreeViewer().getControl().getParent().getParent(), Combo.class, combo -> true);
    scope.select(Scope.SELECTION.ordinal());
    scope.notifyListeners(SWT.Selection, new Event());
    Text search = field("Search");
    search.setText("Greeter");
    search.notifyListeners(SWT.DefaultSelection, new Event());
    waitForSearch();
    assertEquals(Scope.SELECTION, ERipGrepViewPart.getHistory().getSearches().get(0).getKey().getScope());
    assertEquals(List.of("other (1 match)", "  Greeter.txt (1)", "    1: Greeter"), tree());
    // the project of the editor
    org.eclipse.ui.ide.IDE.openEditor(page, project.getFile("README.md"), "org.eclipse.ui.DefaultTextEditor");
    page.activate(view);
    scope.select(Scope.PROJECT.ordinal());
    scope.notifyListeners(SWT.Selection, new Event());
    search.setText("says");
    search.notifyListeners(SWT.DefaultSelection, new Event());
    waitForSearch();
    assertEquals(List.of("demo (1 match)", "  README.md (1)", "    3: The Greeter says hello."), tree());
  }

  @Test
  void opensThePreferencePage() {
    PreferenceDialog dialog = PreferencesUtil.createPreferenceDialogOn(page.getWorkbenchWindow().getShell(), PreferencePage.ID,
        new String[] { PreferencePage.ID }, null);
    dialog.setBlockOnOpen(false);
    dialog.open();
    try {
      PreferencePage preferencePage = (PreferencePage) dialog.getSelectedPage();
      Label version = find(preferencePage.getControl(), Label.class, label -> label.getText().startsWith("ripgrep "));
      assertNotNull(version);
      Text path = find(preferencePage.getControl(), Text.class, text -> "Found on the PATH if empty".equals(text.getMessage()));
      path.setText(new File("missing-rg").getAbsolutePath());
      assertEquals("RipGrep is not found.", version.getText());
    } finally {
      dialog.close();
    }
  }

  /**
   * Saves a picture of the view in the file given by the environment variable ERIPGREP_SCREENSHOT. The display
   * must be shown and, on Wayland, GDK_BACKEND=x11 is needed to read the pixels of the window.
   */
  @Test
  void takesAScreenshot() {
    Shell shell = page.getWorkbenchWindow().getShell();
    shell.setMaximized(false);
    shell.setBounds(40, 30, 1000, 560);
    page.setEditorAreaVisible(false);
    page.setPartState(page.getReference(view), IWorkbenchPage.STATE_MAXIMIZED);
    ToolItem replace = Arrays.stream(find(view.getTreeViewer().getControl().getParent().getParent(), ToolBar.class, toolBar -> true).getItems())
        .filter(item -> "Replace".equals(item.getToolTipText())).findFirst().orElseThrow();
    replace.setSelection(true);
    replace.notifyListeners(SWT.Selection, new Event());
    field("Replace").setText("welcome");
    Request request = request("greet");
    request.setCaseSensitive(false);
    search(request);
    ((ResultContentProvider) view.getTreeViewer().getContentProvider()).setGroupByFolder(true);
    sorted(tree());
    select(file("Main.java").getMatchingLines().get(1));
    page.activate(view);
    view.getTreeViewer().getControl().setFocus();
    waitFor(() -> false, 1000);
    assertEquals(8, view.getCurrent().getMatchCount());
    String target = System.getenv("ERIPGREP_SCREENSHOT");
    if (target != null) {
      Rectangle bounds = shell.getClientArea();
      Image image = new Image(shell.getDisplay(), bounds.width, bounds.height);
      GC gc = new GC(shell);
      gc.copyArea(image, 0, 0);
      gc.dispose();
      ImageLoader loader = new ImageLoader();
      loader.data = new ImageData[] { image.getImageData() };
      loader.save(target, SWT.IMAGE_PNG);
      image.dispose();
      assertTrue(new File(target).length() > 0);
    }
    replace.setSelection(false);
    replace.notifyListeners(SWT.Selection, new Event());
    page.setPartState(page.getReference(view), IWorkbenchPage.STATE_RESTORED);
    page.setEditorAreaVisible(true);
  }
}
