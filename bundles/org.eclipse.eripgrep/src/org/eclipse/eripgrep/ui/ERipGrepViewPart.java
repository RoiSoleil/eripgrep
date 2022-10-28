package org.eclipse.eripgrep.ui;

import static org.eclipse.eripgrep.ui.UiUtils.*;
import static org.eclipse.eripgrep.utils.PreferenceConstantes.*;

import java.io.*;
import java.nio.charset.Charset;
import java.util.*;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

import org.eclipse.core.filesystem.*;
import org.eclipse.core.resources.*;
import org.eclipse.core.runtime.*;
import org.eclipse.core.runtime.jobs.*;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.eripgrep.*;
import org.eclipse.eripgrep.model.*;
import org.eclipse.eripgrep.model.Error;
import org.eclipse.eripgrep.ui.copy.*;
import org.eclipse.eripgrep.ui.model.*;
import org.eclipse.eripgrep.utils.*;
import org.eclipse.jface.action.*;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.*;
import org.eclipse.search.internal.ui.SearchPluginImages;
import org.eclipse.search.internal.ui.text.*;
import org.eclipse.search.ui.ISearchQuery;
import org.eclipse.search.ui.text.*;
import org.eclipse.search2.internal.ui.CancelSearchAction;
import org.eclipse.search2.internal.ui.basic.views.*;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.*;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;
import org.eclipse.ui.*;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.part.ViewPart;
import org.eclipse.ui.texteditor.ITextEditor;
import org.mozilla.universalchardet.ReaderFactory;

/**
 * A view for RipGrep.
 */
@SuppressWarnings("restriction")
public class ERipGrepViewPart extends ViewPart {

  public static final String ID = "org.eclipse.eripgrep.ERipGrepView";
  public static boolean ALPHABETICAL_SORT = Utils.getPreferences().getBoolean(PreferenceConstantes.ALPHABETICAL_SORT, false);
  public static boolean GROUP_BY_FOLDER = Utils.getPreferences().getBoolean(PreferenceConstantes.GROUP_BY_FOLDER,
      false);
  public static Comparator<Object> MATCHINGFILE_COMPARATOR = new Comparator<Object>() {
    @Override
    public int compare(Object o1, Object o2) {
      return ((MatchingFile) o1).getFilePath().compareTo(((MatchingFile) o2).getFilePath());
    }
  };

  private static String root = "/\\ROOT/\\";
  private static Object originalElement;
  private static Job currentJob;
  private static LinkedHashMap<Request, Response> history = loadHistory();

  private static ImageDescriptor alphabSortImage = createImageDescriptorFromURL(
      "platform:/plugin/org.eclipse.jdt.ui/icons/full/elcl16/alphab_sort_co.png");
  private static ImageDescriptor groupByFolderImage = createImageDescriptorFromURL(
      "platform:/plugin/org.eclipse.search/icons/full/etool16/group_by_folder.png");
  private static ImageDescriptor settingsImage = createImageDescriptorFromURL(
      "platform:/plugin/org.eclipse.egit.ui/icons/obj16/settings.png");

  private static Comparator<Object> searchProjectComparator = new Comparator<Object>() {
    @Override
    public int compare(Object o1, Object o2) {
      return ((SearchedProject) o1).getProject().getName().compareTo(((SearchedProject) o2).getProject().getName());
    }
  };
  private static Comparator<Object> folderComparator = new Comparator<Object>() {
    @Override
    public int compare(Object o1, Object o2) {
      return ((Folder) o1).getName().compareTo(((Folder) o2).getName());
    }
  };

  private static AbstractTextSearchResult abstractTextSearchResult = new AbstractTextSearchResult() {

    @Override
    public String getLabel() {
      return null;
    }

    @Override
    public String getTooltip() {
      return null;
    }

    @Override
    public ImageDescriptor getImageDescriptor() {
      return null;
    }

    @Override
    public ISearchQuery getQuery() {
      return null;
    }

    @Override
    public IEditorMatchAdapter getEditorMatchAdapter() {
      return null;
    }

    @Override
    public IFileMatchAdapter getFileMatchAdapter() {
      return null;
    }

    public int getMatchCount(Object element) {
      element = originalElement;
      if (element instanceof SearchedProject) {
        return ((SearchedProject) element).getMatchingFiles().size();
      } else if (element instanceof MatchingFile) {
        return ((MatchingFile) element).getMatchingLines().size();
      }
      return 0;
    }
  };

  private TreeViewer treeViewer;
  private Text textField;
  private Button caseSensitiveButton;
  private Button regularExpressionButton;

  private EditorOpener editorOpener = new EditorOpener();

  private AbstractTextSearchViewPage abstractTextSearchViewPage = new AbstractTextSearchViewPage() {

    @Override
    protected void elementsChanged(Object[] objects) {
    }

    @Override
    protected void configureTreeViewer(TreeViewer viewer) {
    }

    @Override
    protected void configureTableViewer(TableViewer viewer) {
    }

    @Override
    protected void clear() {
    }

    @Override
    public void gotoNextMatch() {
      go_to(true);
    }

    @Override
    public void gotoPreviousMatch() {
      go_to(false);
    }

    private void go_to(boolean next) {
      SafeRunner.run(() -> {
        new TreeViewerNavigator(abstractTextSearchViewPage, treeViewer).navigateNext(next);
        Object firstElement = ((StructuredSelection) treeViewer.getSelection()).getFirstElement();
        if (firstElement instanceof MatchingLine) {
          showMatchingLine((MatchingLine) firstElement);
        }
      });
    }

    @Override
    public void internalRemoveSelected() {
      ((StructuredSelection) treeViewer.getSelection()).forEach(this::internalRemoveSelected);
    }

    private void internalRemoveSelected(Object element) {
      if (element instanceof SearchedProject) {
        SearchedProject searchProject = (SearchedProject) element;
        searchProject.getResponse().getSearchedProjects().remove(searchProject);
        treeViewer.refresh();
      } else if (element instanceof MatchingFile) {
        MatchingFile matchingFile = (MatchingFile) element;
        matchingFile.getSearchProject().getMatchingFiles().remove(matchingFile);
        if (matchingFile.getSearchProject().getMatchingFiles().isEmpty()) {
          internalRemoveSelected(matchingFile.getSearchProject());
        } else {
          treeViewer.refresh(matchingFile.getSearchProject());
        }
      } else if (element instanceof MatchingLine) {
        MatchingLine matchingLine = (MatchingLine) element;
        matchingLine.getMatchingFile().getMatchingLines().remove(matchingLine);
        if (matchingLine.getMatchingFile().getMatchingLines().isEmpty()) {
          internalRemoveSelected(matchingLine.getMatchingFile());
        } else {
          treeViewer.refresh(matchingLine.getMatchingFile());
        }
      } else if (element instanceof SeeAll) {
        SeeAll seeAll = (SeeAll) element;
        Arrays.asList(seeAll.toArray()).forEach(object -> seeAll.getUnderlyingObjects().remove(object));
        treeViewer.refresh(seeAll);
      } else if (element instanceof Folder) {
        Folder folder = (Folder) element;
        File _folder = new File(folder.getParentFile(), folder.getName());
        SearchedProject searchProject = folder.getSearchProject();
        searchProject.getMatchingFiles().removeAll(searchProject.getMatchingFiles().stream()
            .filter(matchingFile -> matchingFile.getFilePath().startsWith(_folder.getAbsolutePath())).toList());
        treeViewer.refresh(searchProject);
      }
    }

    @Override
    public int getDisplayedMatchCount(Object element) {
      return element instanceof MatchingLine ? 1 : 0;
    }

    @Override
    public AbstractTextSearchResult getInput() {
      return abstractTextSearchResult;
    }

  };

  private SearchAgainAction searchAgainAction;
  private CancelSearchAction cancelSearchAction;
  private ExpandAllAction expandAllAction;
  private CollapseAllAction collapseAllAction;
  private RemoveSelectedMatchesAction removeSelectedMatchesAction;

  private Request currentRequest;

  @Override
  public void createPartControl(Composite parent) {
    initToolbar();
    GridLayout gridLayout = new GridLayout();
    gridLayout.numColumns = 1;
    gridLayout.horizontalSpacing = 0;
    gridLayout.verticalSpacing = 0;
    gridLayout.marginHeight = 0;
    parent.setLayout(gridLayout);
    createSearchField(parent);
    createTreeViewer(parent);
  }

  private void initToolbar() {
    getViewSite().getActionBars().getToolBarManager().add(new ShowNextResultAction(abstractTextSearchViewPage));
    getViewSite().getActionBars().getToolBarManager().add(new ShowPreviousResultAction(abstractTextSearchViewPage));
    getViewSite().getActionBars().getToolBarManager().add(new Separator());
    removeSelectedMatchesAction = new RemoveSelectedMatchesAction(abstractTextSearchViewPage);
    getViewSite().getActionBars().getToolBarManager().add(removeSelectedMatchesAction);
    getViewSite().getActionBars().getToolBarManager().add(new Separator());
    expandAllAction = new ExpandAllAction();
    getViewSite().getActionBars().getToolBarManager().add(expandAllAction);
    collapseAllAction = new CollapseAllAction();
    getViewSite().getActionBars().getToolBarManager().add(collapseAllAction);
    getViewSite().getActionBars().getToolBarManager().add(new Separator());
    searchAgainAction = new SearchAgainAction() {
      @Override
      public void run() {
        searchFor(currentRequest);
      }
    };
    searchAgainAction.setEnabled(currentJob != null);
    getViewSite().getActionBars().getToolBarManager().add(searchAgainAction);
    cancelSearchAction = new CancelSearchAction(null) {
      @Override
      public void run() {
        if (currentJob != null) {
          currentJob.cancel();
        }
      }
    };
    cancelSearchAction.setEnabled(currentJob != null && currentJob.getThread() != null);
    getViewSite().getActionBars().getToolBarManager().add(cancelSearchAction);
    getViewSite().getActionBars().getToolBarManager().add(new SearchHistoryDropDownAction(this));
    getViewSite().getActionBars().getToolBarManager().add(new Separator());
    Action sortAlphabeticallyAction = new Action("Sort alphabetically", IAction.AS_PUSH_BUTTON) {
      @Override
      public void run() {
        ALPHABETICAL_SORT = !ALPHABETICAL_SORT;
        Utils.getPreferences().putBoolean(PreferenceConstantes.ALPHABETICAL_SORT, ALPHABETICAL_SORT);
        treeViewer.refresh();
      }
    };
    sortAlphabeticallyAction.setImageDescriptor(alphabSortImage);
    sortAlphabeticallyAction.setChecked(ALPHABETICAL_SORT);
    getViewSite().getActionBars().getToolBarManager().add(sortAlphabeticallyAction);
    Action groupByFolderAction = new Action("Group by folder") {
      @Override
      public void run() {
        Folder.clear();
        GROUP_BY_FOLDER = !GROUP_BY_FOLDER;
        Utils.getPreferences().putBoolean(PreferenceConstantes.GROUP_BY_FOLDER, GROUP_BY_FOLDER);
        treeViewer.refresh();
      }
    };
    groupByFolderAction.setImageDescriptor(groupByFolderImage);
    getViewSite().getActionBars().getToolBarManager().add(groupByFolderAction);
    getViewSite().getActionBars().getToolBarManager().add(new Separator());
    Action openSettingsAction = new Action("Settings", settingsImage) {
      @Override
      public void run() {
        UiUtils.openPreferencePage();
      }
    };
    getViewSite().getActionBars().getToolBarManager().add(openSettingsAction);
  }

  private void createSearchField(Composite parent) {
    GridLayout gridLayout;
    Composite composite = new Composite(parent, SWT.NONE);
    composite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
    gridLayout = new GridLayout();
    gridLayout.numColumns = 3;
    composite.setLayout(gridLayout);
    textField = new Text(composite, SWT.BORDER);
    textField.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
    caseSensitiveButton = new Button(composite, SWT.CHECK);
    caseSensitiveButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));
    caseSensitiveButton.setText("Case sensitive");
    caseSensitiveButton.setSelection(Utils.getPreferences().getBoolean(CASE_SENSITIVE, true));
    caseSensitiveButton.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        Utils.getPreferences().putBoolean(CASE_SENSITIVE, caseSensitiveButton.getSelection());
      }
    });
    regularExpressionButton = new Button(composite, SWT.CHECK);
    regularExpressionButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));
    regularExpressionButton.setText("Regular expression");
    regularExpressionButton.setSelection(InstanceScope.INSTANCE.getNode(Activator.PLUGIN_ID).getBoolean(REGULAR_EXPRESSION, false));
    regularExpressionButton.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        Utils.getPreferences().putBoolean(REGULAR_EXPRESSION, regularExpressionButton.getSelection());
      }
    });
    textField.addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        if (e.character == SWT.CR) {
          searchFor(textField.getText(), caseSensitiveButton.getSelection(), regularExpressionButton.getSelection());
        }
      }
    });
  }

  private void createTreeViewer(Composite parent) {
    treeViewer = new TreeViewer(parent, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL);
    treeViewer.getControl().setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
    expandAllAction.setViewer(treeViewer);
    collapseAllAction.setViewer(treeViewer);
    treeViewer.setContentProvider(getContentProvider());
    treeViewer.setLabelProvider(getLabelProvider());
    treeViewer.addOpenListener(new IOpenListener() {

      @Override
      public void open(OpenEvent event) {
        SafeRunner.run(() -> {
          Object firstElement = ((IStructuredSelection) event.getSelection()).getFirstElement();
          if (firstElement instanceof MatchingFile) {
            firstElement = ((MatchingFile) firstElement).getMatchingLines().get(0);
          }
          if (firstElement instanceof MatchingLine) {
            MatchingLine matchingLine = (MatchingLine) firstElement;
            showMatchingLine(matchingLine);
          }
        });
      }
    });
    treeViewer.getControl().addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        if (e.character == SWT.DEL)
          removeSelectedMatchesAction.run();
        else if (e.keyCode == SWT.ARROW_DOWN) {
          e.doit = false;
          abstractTextSearchViewPage.gotoNextMatch();
        } else if (e.keyCode == SWT.ARROW_UP) {
          e.doit = false;
          abstractTextSearchViewPage.gotoPreviousMatch();
        }
      }
    });
    MenuManager menuMgr = new MenuManager("#PopUp");
    Menu menu = menuMgr.createContextMenu(treeViewer.getControl());
    treeViewer.getControl().setMenu(menu);
    menuMgr.setRemoveAllWhenShown(true);
    menuMgr.addMenuListener(mgr -> {
      mgr.add(new Action("Copy") {
        // TODO
      });
    });
    getSite().registerContextMenu(menuMgr, treeViewer);
  }

  private ITreeContentProvider getContentProvider() {
    return new ITreeContentProvider() {

      @Override
      public boolean hasChildren(Object element) {
        if (element instanceof Response) {
          return !((Response) element).getSearchedProjects().isEmpty();
        } else if (element instanceof SearchedProject) {
          return !((SearchedProject) element).getMatchingFiles().isEmpty();
        } else if (element instanceof MatchingFile) {
          return !((MatchingFile) element).getMatchingLines().isEmpty();
        } else if (element instanceof Folder) {
          return true;
        }
        return getChildren(element) != null && getChildren(element).length > 0;
      }

      @Override
      public Object getParent(Object element) {
        if (element instanceof SearchedProject) {
          return ((SearchedProject) element).getResponse();
        } else if (element instanceof Folder) {
          Folder folder = ((Folder) element);
          return folder.getFolder() != null ? folder.getFolder() : folder.getSearchProject();
        }
        return null;
      }

      @Override
      public Object[] getElements(Object inputElement) {
        Collection<SearchedProject> searchProjects = ((Response) inputElement).getSearchedProjects();
        if (ALPHABETICAL_SORT) {
          List<SearchedProject> list = new ArrayList<>(searchProjects);
          list.sort(searchProjectComparator);
          searchProjects = list;
        }
        return searchProjects.toArray();
      }

      @Override
      public Object[] getChildren(Object parentElement) {
        if (parentElement instanceof SearchedProject) {
          SearchedProject searchProject = (SearchedProject) parentElement;
          if (searchProject.getError() != null)
            return new Object[] { searchProject.getError() };
          if (GROUP_BY_FOLDER) {
            List<Object> children = new ArrayList<>();
            File _folder = searchProject.getProject().getLocation().toFile();
            Map<String, List<MatchingFile>> firstLevelFolders = getFirstLevelFolders(_folder,
                searchProject.getMatchingFiles());
            for (Entry<String, List<MatchingFile>> entry : firstLevelFolders.entrySet()) {
              if (root.equals(entry.getKey()))
                continue;
              Folder folder = Folder.getOrCreate(searchProject, _folder, entry.getKey(),
                  (IFolder) (searchProject.getProject().isOpen() ? searchProject.getProject().findMember(entry.getKey())
                      : null));
              folder.setMatchingFiles(entry.getValue());
              children.add(folder);
            }
            children.sort(folderComparator);
            if (firstLevelFolders.get(root) != null) {
              List<MatchingFile> matchingFiles = firstLevelFolders.get(root);
              children.addAll(Arrays
                  .asList(getMaxChildren(searchProject, matchingFiles, ALPHABETICAL_SORT ? MATCHINGFILE_COMPARATOR : null)));
            }
            return children.toArray();
          }
          return getMaxChildren(searchProject, searchProject.getMatchingFiles(),
              ALPHABETICAL_SORT ? MATCHINGFILE_COMPARATOR : null);
        } else if (parentElement instanceof MatchingFile) {
          MatchingFile matchingFile = (MatchingFile) parentElement;
          return getMaxChildren(matchingFile, matchingFile.getMatchingLines(), null);
        } else if (parentElement instanceof SeeAll) {
          return ((SeeAll) parentElement).toArray();
        } else if (parentElement instanceof Folder) {
          Folder folder = (Folder) parentElement;
          File _folder = new File(folder.getParentFile(), folder.getName());
          List<Object> children = new ArrayList<>();
          Map<String, List<MatchingFile>> firstLevelFolders = getFirstLevelFolders(_folder, folder.getMatchingFiles());
          for (Entry<String, List<MatchingFile>> entry : firstLevelFolders.entrySet()) {
            if (root.equals(entry.getKey()))
              continue;
            Folder subFolder = Folder.getOrCreate(folder.getSearchProject(), _folder, entry.getKey(),
                (IFolder) (folder.getFolder() != null ? folder.getFolder().findMember(entry.getKey()) : null));
            subFolder.setMatchingFiles(entry.getValue());
            children.add(subFolder);
          }
          children.sort(folderComparator);
          if (firstLevelFolders.get(root) != null) {
            List<MatchingFile> matchingFiles = firstLevelFolders.get(root);
            children.addAll(
                Arrays.asList(getMaxChildren(folder, matchingFiles, ALPHABETICAL_SORT ? MATCHINGFILE_COMPARATOR : null)));
          }
          return children.toArray();
        }
        return null;
      }

      private Map<String, List<MatchingFile>> getFirstLevelFolders(File rootFolder, List<MatchingFile> matchingFiles) {
        Map<String, List<MatchingFile>> firstLevelForlders = new HashMap<>();
        for (MatchingFile matchingFile : matchingFiles) {
          File folder = new File(matchingFile.getFilePath());
          while (!folder.getParentFile().equals(rootFolder)) {
            folder = folder.getParentFile();
          }
          String folderName = folder.isFile() ? root : folder.getName();

          firstLevelForlders.computeIfAbsent(folderName, s -> new ArrayList<>()).add(matchingFile);
        }
        return firstLevelForlders;
      }

      private Object[] getMaxChildren(Object element, List<?> collection, Comparator comparator) {
        List<Object> objects = new ArrayList<>();
        for (int i = 0; i < collection.size() && i < SeeAll.MAX_NUMBER; i++) {
          objects.add(collection.get(i));
        }
        if (comparator != null) {
          objects.sort(comparator);
        }
        if (collection.size() > SeeAll.MAX_NUMBER) {
          objects.add(SeeAll.getOrCreate(element, collection));
        }
        return objects.toArray();
      }
    };
  }

  private DecoratingFileSearchLabelProvider getLabelProvider() {
    DecoratingFileSearchLabelProvider decoratingFileSearchLabelProvider = new DecoratingFileSearchLabelProvider(new ERipGrepLabelProvider()) {

      @Override
      protected StyledString getStyledText(Object element) {
        originalElement = element;
        if (element instanceof SeeAll) {
          return new StyledString("See all " + ((SeeAll) element).toArray().length + " remaining elements");
        } else if (element instanceof SearchedProject) {
          element = ((SearchedProject) element).getProject();
        } else if (element instanceof MatchingFile && ((MatchingFile) element).getMatchingResource() != null) {
          element = ((MatchingFile) element).getMatchingResource();
        } else if (element instanceof Folder && ((Folder) element).getFolder() != null) {
          element = ((Folder) element).getFolder();
        }
        return super.getStyledText(element);
      }

      @Override
      public Image getImage(Object element) {
        if (element instanceof SearchedProject) {
          element = ((SearchedProject) element).getProject();
        } else if (element instanceof MatchingFile && ((MatchingFile) element).getMatchingResource() != null) {
          element = ((MatchingFile) element).getMatchingResource();
        } else if (element instanceof Folder && ((Folder) element).getFolder() != null) {
          element = ((Folder) element).getFolder();
        }
        return super.getImage(element);
      }
    };
    decoratingFileSearchLabelProvider.addListener(event -> treeViewer.refresh());
    return decoratingFileSearchLabelProvider;
  }

  public void setCurrent(Request searchRequest, Response response) {
    this.currentRequest = searchRequest;
    textField.setText(searchRequest.getText());
    caseSensitiveButton.setSelection(searchRequest.isCaseSensitive());
    regularExpressionButton.setSelection(searchRequest.isRegularExpression());
    treeViewer.setInput(response);
  }

  public Response getCurrent() {
    return (Response) treeViewer.getInput();
  }

  private void showMatchingLine(MatchingLine matchingLine) throws PartInitException, IOException, CoreException {
    IResource resource = matchingLine.getMatchingFile().getMatchingResource();
    if (resource == null) {
      IFileStore fileStore = EFS.getLocalFileSystem().getStore(new Path(matchingLine.getMatchingFile().getFilePath()));
      IEditorPart editorPart = IDE.openInternalEditorOnFileStore(getSite().getPage(), fileStore);
      if (editorPart instanceof ITextEditor) {
        try (BufferedReader bufferedReader = ReaderFactory
            .createBufferedReader(new File(matchingLine.getMatchingFile().getFilePath()))) {
          int lineNumber = 1;
          int offset = 0;
          String line = null;
          while ((line = bufferedReader.readLine()) != null && lineNumber != matchingLine.getLineNumber()) {
            lineNumber++;
            offset += line.length() + 2;
          }
          if (line != null) {
            Matcher matcher = matchingLine.getMatcher();
            matcher.find();
            ((ITextEditor) editorPart).selectAndReveal(offset + matcher.start(), matcher.group(1).length());
          }
        }
      }
    } else {
      IFile file = (IFile) resource;
      Charset charset = Charset.forName(file.getCharset());
      try (ExtendedBufferedReader bufferedReader = new ExtendedBufferedReader(
          new InputStreamReader(file.getContents(), charset))) {
        while (bufferedReader.getCurrentLineNumber() + 1 != matchingLine.getLineNumber()
            && bufferedReader.readLine() != null) {
        }
        Matcher matcher = matchingLine.getMatcher();
        matcher.find();
        editorOpener.openAndSelect(getSite().getPage(), file, (int) bufferedReader.getPosition() + matcher.start(),
            matcher.group(1).length(), true);
      }
    }
    setFocus();
  }

  @Override
  public void setFocus() {
    treeViewer.getControl().setFocus();
  }

  public void searchFor(String text, boolean caseSensitive, boolean regularExpression) {
    Request request = new Request();
    request.setText(text);
    request.setCaseSensitive(caseSensitive);
    request.setRegularExpression(regularExpression);
    searchFor(request);
  }

  public void searchFor(Request request) {
    currentRequest = request;
    (currentJob = new Job("Searching for \"" + request.getText() + "\" with RipGrep ...") {

      @Override
      protected IStatus run(IProgressMonitor monitor) {
        Display.getDefault().asyncExec(() -> {
          textField.setText(request.getText());
          searchAgainAction.setEnabled(false);
          cancelSearchAction.setEnabled(true);
          getViewSite().getActionBars().updateActionBars();
        });
        AtomicBoolean done = new AtomicBoolean();
        request.setTime(System.currentTimeMillis());
        request.setProgressMonitor(monitor);
        request.setListener(new ProgressListener() {

          @Override
          public void update(Object element) {
            if (treeViewer.isBusy()) {
              return;
            }
            Display.getDefault().asyncExec(() -> treeViewer.refresh(element));
          }

          @Override
          public void done() {
            done.set(true);
            while (treeViewer.isBusy()) {
              try {
                Thread.sleep(100);
              } catch (InterruptedException e) {
              }
            }
            if (!monitor.isCanceled())
              Display.getDefault().asyncExec(() -> {
                treeViewer.refresh(treeViewer.getInput());
                SeeAll.clear();
              });
          }
        });
        Display.getDefault().asyncExec(() -> {
          SeeAll.clear();
          Folder.clear();
          Response response = Engine.searchFor(request);
          treeViewer.setInput(response);
          history.put(request, response);
          saveHistory();
        });
        while (!(done.get() || monitor.isCanceled())) {
          try {
            Thread.sleep(100);
            Thread.yield();
          } catch (InterruptedException e) {
          }
        }
        return monitor.isCanceled() ? Status.CANCEL_STATUS : Status.OK_STATUS;
      }
    }).schedule();
    currentJob.addJobChangeListener(new JobChangeAdapter() {

      @Override
      public void done(IJobChangeEvent event) {
        Display.getDefault().asyncExec(() -> {
          searchAgainAction.setEnabled(true);
          cancelSearchAction.setEnabled(false);
          getViewSite().getActionBars().updateActionBars();
        });
      }

    });
  }

  private class ERipGrepLabelProvider extends FileLabelProvider {

    private Image fLineMatchImage = SearchPluginImages.get(SearchPluginImages.IMG_OBJ_TEXT_SEARCH_LINE);
    private Image folderImage = createImageFromURL("platform:/plugin/org.eclipse.ui.ide/icons/full/obj16/folder.png");
    private Image fileImage = createImageFromURL(
        "platform:/plugin/org.eclipse.ui.ide/icons/full/obj16/fileType_filter.png");
    private Image seeAllImage = createImageFromURL(
        "platform:/plugin/org.eclipse.search/icons/full/elcl16/hierarchicalLayout.png");
    private Image errorImage = createImageFromURL(
        "platform:/plugin/org.eclipse.ui.views.log/icons/eview16/error_log.png");

    public ERipGrepLabelProvider() {
      super(abstractTextSearchViewPage, SHOW_LABEL);
    }

    @Override
    public StyledString getStyledText(Object element) {
      if (element instanceof MatchingFile) {
        return new StyledString(((MatchingFile) element).getFileName());
      } else if (element instanceof MatchingLine) {
        return getStyledTextForMatchingLine((MatchingLine) element);
      } else if (element instanceof Folder) {
        return new StyledString(((Folder) element).getName());
      } else if (element instanceof Error) {
        return new StyledString(((Error) element).getError());
      }
      return super.getStyledText(element);
    }

    private StyledString getStyledTextForMatchingLine(MatchingLine matchingLine) {
      StyledString styledString = new StyledString();
      styledString.append(matchingLine.getLineNumber() + ": ");
      String matString = matchingLine.getMatchingLine();
      Matcher matcher = matchingLine.getMatcher();
      int i = 0;
      while (matcher.find()) {
        String t = matString.substring(i, matcher.start());
        styledString.append(t);
        styledString.append(matcher.group(1), DecoratingFileSearchLabelProvider.HIGHLIGHT_STYLE);
        i = matcher.end();
      }
      styledString.append(matString.substring(i, matString.length()));
      return styledString;
    }

    @Override
    public Image getImage(Object element) {
      if (element instanceof SeeAll) {
        return seeAllImage;
      } else if (element instanceof MatchingFile) {
        return fileImage;
      } else if (element instanceof MatchingLine) {
        return fLineMatchImage;
      } else if (element instanceof Folder) {
        return folderImage;
      } else if (element instanceof Error) {
        return errorImage;
      }
      return super.getImage(element);
    }

  }

  public static LinkedHashMap<Request, Response> getHistory() {
    return history;
  }

  private static LinkedHashMap<Request, Response> loadHistory() {
    LinkedHashMap<Request, Response> history = new LinkedHashMap<>();
    Arrays.asList(Utils.getPreferences().get(HISTORY, "").split("\\|")).stream()
        .filter(Predicate.not(String::isBlank))
        .forEach(r -> {
          Request request = new Request();
          request.setText(r);
          history.put(request, null);
        });
    return history;
  }

  public void clearHistory() {
    if (currentJob != null && currentJob.getState() == Job.RUNNING) {
      currentJob.cancel();
    }
    searchAgainAction.setEnabled(false);
    currentRequest = null;
    textField.setText("");
    history.clear();
    treeViewer.setInput(null);
    saveHistory();
  }

  private void saveHistory() {
    List<Request> requests = new ArrayList<>(history.keySet());
    Collections.reverse(requests);
    if (requests.size() > 25) {
      requests = requests.subList(0, 25);
    }
    String history = requests.stream().map(Request::getText).collect(Collectors.joining("|"));
    Utils.getPreferences().put(HISTORY,
        history);
  }
}
