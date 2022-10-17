package org.eclipse.eripgrep.ui;

import static org.eclipse.eripgrep.ui.ERipGrepPreferencePage.ALPHABETICAL_SORT;
import static org.eclipse.eripgrep.ui.ERipGrepPreferencePage.CASE_SENSITIVE;
import static org.eclipse.eripgrep.ui.ERipGrepPreferencePage.GROUP_BY_FOLDER;
import static org.eclipse.eripgrep.ui.ERipGrepPreferencePage.REGULAR_EXPRESSION;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.ISafeRunnable;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.SafeRunner;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.IJobChangeEvent;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.jobs.JobChangeAdapter;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.eripgrep.Activator;
import org.eclipse.eripgrep.ERipGrepEngine;
import org.eclipse.eripgrep.ERipGrepProgressListener;
import org.eclipse.eripgrep.model.ERipGrepResponse;
import org.eclipse.eripgrep.model.ERipSearchRequest;
import org.eclipse.eripgrep.model.MatchingFile;
import org.eclipse.eripgrep.model.MatchingLine;
import org.eclipse.eripgrep.model.RipGrepError;
import org.eclipse.eripgrep.model.SearchProject;
import org.eclipse.eripgrep.utils.ExtendedBufferedReader;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.IOpenListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.OpenEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.StyledString;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.search.internal.ui.SearchPluginImages;
import org.eclipse.search.internal.ui.text.DecoratingFileSearchLabelProvider;
import org.eclipse.search.internal.ui.text.EditorOpener;
import org.eclipse.search.internal.ui.text.FileLabelProvider;
import org.eclipse.search.ui.ISearchQuery;
import org.eclipse.search.ui.text.AbstractTextSearchResult;
import org.eclipse.search.ui.text.AbstractTextSearchViewPage;
import org.eclipse.search.ui.text.IEditorMatchAdapter;
import org.eclipse.search.ui.text.IFileMatchAdapter;
import org.eclipse.search2.internal.ui.CancelSearchAction;
import org.eclipse.search2.internal.ui.basic.views.CollapseAllAction;
import org.eclipse.search2.internal.ui.basic.views.ExpandAllAction;
import org.eclipse.search2.internal.ui.basic.views.RemoveSelectedMatchesAction;
import org.eclipse.search2.internal.ui.basic.views.ShowNextResultAction;
import org.eclipse.search2.internal.ui.basic.views.ShowPreviousResultAction;
import org.eclipse.search2.internal.ui.basic.views.TreeViewerNavigator;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.part.ViewPart;
import org.eclipse.ui.texteditor.ITextEditor;
import org.mozilla.universalchardet.ReaderFactory;

/**
 * A view for RipGrep.
 */
public class ERipGrepViewPart extends ViewPart {

  public static final String ID = "org.eclipse.eripgrep.ERipGrepView";

  private static String ROOT = "/\\ROOT/\\";

  private static Object originalElement;

  private ImageDescriptor alphabSortImage = createImageDescriptorFromURL(
      "platform:/plugin/org.eclipse.jdt.ui/icons/full/elcl16/alphab_sort_co.png");

  private ImageDescriptor groupByFolderImage = createImageDescriptorFromURL(
      "platform:/plugin/org.eclipse.search/icons/full/etool16/group_by_folder.png");

  private static boolean alphabSort = InstanceScope.INSTANCE.getNode(Activator.PLUGIN_ID).getBoolean(ALPHABETICAL_SORT,
      false);
  private static boolean groupByFolder = InstanceScope.INSTANCE.getNode(Activator.PLUGIN_ID).getBoolean(GROUP_BY_FOLDER,
      false);

  private static Comparator<Object> matchingFileComparator = new Comparator<Object>() {

    @Override
    public int compare(Object o1, Object o2) {
      return ((MatchingFile) o1).getFilePath().compareTo(((MatchingFile) o2).getFilePath());
    }
  };

  private static Comparator<Object> searchProjectComparator = new Comparator<Object>() {

    @Override
    public int compare(Object o1, Object o2) {
      return ((SearchProject) o1).getProject().getName().compareTo(((SearchProject) o2).getProject().getName());
    }
  };

  private static Comparator<Object> folderComparator = new Comparator<Object>() {

    @Override
    public int compare(Object o1, Object o2) {
      return ((Folder) o1).getName().compareTo(((Folder) o2).getName());
    }
  };

  private TreeViewer treeViewer;
  private static Job currentJob;

  private EditorOpener editorOpener = new EditorOpener();

  private AbstractTextSearchResult abstractTextSearchResult = new AbstractTextSearchResult() {

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
      if (element instanceof SearchProject) {
        return ((SearchProject) element).getMatchingFiles().size();
      } else if (element instanceof MatchingFile) {
        return ((MatchingFile) element).getMatchingLines().size();
      }
      return 0;
    }
  };

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

    public void gotoNextMatch() {
      new TreeViewerNavigator(abstractTextSearchViewPage, treeViewer).navigateNext(true);
      Object firstElement = ((StructuredSelection) treeViewer.getSelection()).getFirstElement();
      if (firstElement instanceof MatchingLine) {
        try {
          showMatchingLine((MatchingLine) firstElement);
        } catch (IOException | CoreException e) {
        }
      }
    }

    public void gotoPreviousMatch() {
      new TreeViewerNavigator(abstractTextSearchViewPage, treeViewer).navigateNext(false);
      ((StructuredSelection) treeViewer.getSelection()).getFirstElement();
      Object firstElement = ((StructuredSelection) treeViewer.getSelection()).getFirstElement();
      if (firstElement instanceof MatchingLine) {
        try {
          showMatchingLine((MatchingLine) firstElement);
        } catch (IOException | CoreException e) {
        }
      }
    }

    public void internalRemoveSelected() {
      ((StructuredSelection) treeViewer.getSelection()).forEach(this::internalRemoveSelected);
    }

    private void internalRemoveSelected(Object element) {
      if (element instanceof SearchProject) {
        SearchProject searchProject = (SearchProject) element;
        searchProject.getResponse().getSearchProjects().remove(searchProject);
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
        Arrays.asList(seeAll.toArray()).forEach(object -> seeAll.getAllObjects().remove(object));
        treeViewer.refresh(seeAll);
      } else if (element instanceof Folder) {
        Folder folder = (Folder) element;
        File _folder = new File(folder.getParentFile(), folder.getName());
        SearchProject searchProject = folder.getSearchProject();
        ;
        searchProject.getMatchingFiles().removeAll(searchProject.getMatchingFiles().stream()
            .filter(matchingFile -> matchingFile.getFilePath().startsWith(_folder.getAbsolutePath())).toList());
        treeViewer.refresh(searchProject);
      }
    }

    // private Set<MatchingFile> getMatchingFiles(Folder folder) {
    // Set<MatchingFile> ma
    // }

    public int getDisplayedMatchCount(Object element) {
      return element instanceof MatchingLine ? 1 : 0;
    }

    public AbstractTextSearchResult getInput() {
      return abstractTextSearchResult;
    }

  };

  private SearchAgainAction searchAgainAction;
  private CancelSearchAction cancelSearchAction;
  private ExpandAllAction expandAllAction;
  private CollapseAllAction collapseAllAction;
  private RemoveSelectedMatchesAction removeSelectedMatchesAction;

  public ERipGrepViewPart() {
  }

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
    treeViewer = new TreeViewer(parent, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL);
    treeViewer.getControl().setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
    expandAllAction.setViewer(treeViewer);
    collapseAllAction.setViewer(treeViewer);
    treeViewer.setContentProvider(new ITreeContentProvider() {

      @Override
      public boolean hasChildren(Object element) {
        return getChildren(element) != null && getChildren(element).length > 0;
      }

      @Override
      public Object getParent(Object element) {
        return null;
      }

      @Override
      public Object[] getElements(Object inputElement) {
        if (inputElement instanceof ERipGrepResponse) {
          Collection<SearchProject> searchProjects = ((ERipGrepResponse) inputElement).getSearchProjects();
          if (alphabSort) {
            List<SearchProject> list = new ArrayList<>(searchProjects);
            list.sort(searchProjectComparator);
            searchProjects = list;
          }
          return searchProjects.toArray();
        }
        return null;
      }

      @Override
      public Object[] getChildren(Object parentElement) {
        if (parentElement instanceof SearchProject) {
          SearchProject searchProject = (SearchProject) parentElement;
          if (searchProject.getRipGrepError() != null)
            return new Object[] { searchProject.getRipGrepError() };

          if (groupByFolder) {
            List<Object> children = new ArrayList<>();
            File _folder = searchProject.getProject().getLocation().toFile();
            Map<String, List<MatchingFile>> firstLevelFolders = getFirstLevelFolders(_folder,
                searchProject.getMatchingFiles());
            for (Entry<String, List<MatchingFile>> entry : firstLevelFolders.entrySet()) {
              if (ROOT.equals(entry.getKey()))
                continue;
              Folder folder = Folder.getOrCreate(searchProject, _folder, entry.getKey(),
                  (IFolder) (searchProject.getProject().isOpen() ? searchProject.getProject().findMember(entry.getKey())
                      : null));
              folder.setMatchingFiles(entry.getValue());
              children.add(folder);
            }
            children.sort(folderComparator);
            if (firstLevelFolders.get(ROOT) != null) {
              List<MatchingFile> matchingFiles = firstLevelFolders.get(ROOT);
              children.addAll(Arrays
                  .asList(getMaxChildren(searchProject, matchingFiles, alphabSort ? matchingFileComparator : null)));
            }
            return children.toArray();
          }
          return getMaxChildren(searchProject, searchProject.getMatchingFiles(),
              alphabSort ? matchingFileComparator : null);
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
            if (ROOT.equals(entry.getKey()))
              continue;
            Folder subFolder = Folder.getOrCreate(folder.getSearchProject(), _folder, entry.getKey(),
                (IFolder) (folder.getFolder() != null ? folder.getFolder().findMember(entry.getKey()) : null));
            subFolder.setMatchingFiles(entry.getValue());
            children.add(subFolder);
          }
          children.sort(folderComparator);
          if (firstLevelFolders.get(ROOT) != null) {
            List<MatchingFile> matchingFiles = firstLevelFolders.get(ROOT);
            children.addAll(
                Arrays.asList(getMaxChildren(folder, matchingFiles, alphabSort ? matchingFileComparator : null)));
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
          String folderName = folder.isFile() ? ROOT : folder.getName();

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
    });
    treeViewer.setLabelProvider(new DecoratingFileSearchLabelProvider(new ERipGrepLabelProvider()) {

      @Override
      protected StyledString getStyledText(Object element) {
        originalElement = element;
        if (element instanceof SeeAll) {
          return new StyledString("See all " + ((SeeAll) element).toArray().length + " remaining elements");
        } else if (element instanceof SearchProject) {
          element = ((SearchProject) element).getProject();
        } else if (element instanceof MatchingFile && ((MatchingFile) element).getMatchingResource() != null) {
          element = ((MatchingFile) element).getMatchingResource();
        } else if (element instanceof Folder && ((Folder) element).getFolder() != null) {
          element = ((Folder) element).getFolder();
        }
        return super.getStyledText(element);
      }

      @Override
      public Image getImage(Object element) {
        if (element instanceof SearchProject) {
          element = ((SearchProject) element).getProject();
        } else if (element instanceof MatchingFile && ((MatchingFile) element).getMatchingResource() != null) {
          element = ((MatchingFile) element).getMatchingResource();
        } else if (element instanceof Folder && ((Folder) element).getFolder() != null) {
          element = ((Folder) element).getFolder();
        }
        return super.getImage(element);
      }
    });
    treeViewer.addOpenListener(new IOpenListener() {

      @Override
      public void open(OpenEvent event) {
        ISafeRunnable safeRunnable = new ISafeRunnable() {

          @Override
          public void run() throws Exception {
            Object firstElement = ((IStructuredSelection) event.getSelection()).getFirstElement();
            if (firstElement instanceof MatchingFile) {
              firstElement = ((MatchingFile) firstElement).getMatchingLines().get(0);
            }
            if (firstElement instanceof MatchingLine) {
              MatchingLine matchingLine = (MatchingLine) firstElement;
              showMatchingLine(matchingLine);
            }
          }
        };
        SafeRunner.run(safeRunnable);
      }
    });
    treeViewer.getControl().addKeyListener(new KeyAdapter() {
      @Override
      public void keyReleased(KeyEvent e) {
        if (e.character == 0x7F)
          removeSelectedMatchesAction.run();
      }
    });
  }

  private void createSearchField(Composite parent) {
    GridLayout gridLayout;
    Composite composite = new Composite(parent, SWT.NONE);
    composite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
    gridLayout = new GridLayout();
    gridLayout.numColumns = 3;
    composite.setLayout(gridLayout);
    Text text = new Text(composite, SWT.BORDER);
    text.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
    Button caseSensitiveButton = new Button(composite, SWT.CHECK);
    caseSensitiveButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));
    caseSensitiveButton.setText("Case sensitive");
    caseSensitiveButton.setSelection(InstanceScope.INSTANCE.getNode(Activator.PLUGIN_ID).getBoolean(CASE_SENSITIVE, true));
    caseSensitiveButton.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        IEclipsePreferences preferences = InstanceScope.INSTANCE.getNode(Activator.PLUGIN_ID);
        preferences.putBoolean(CASE_SENSITIVE, caseSensitiveButton.getSelection());
      }
    });
    Button regularExpressionButton = new Button(composite, SWT.CHECK);
    regularExpressionButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));
    regularExpressionButton.setText("Regular expression");
    regularExpressionButton.setSelection(InstanceScope.INSTANCE.getNode(Activator.PLUGIN_ID).getBoolean(REGULAR_EXPRESSION, false));
    regularExpressionButton.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        IEclipsePreferences preferences = InstanceScope.INSTANCE.getNode(Activator.PLUGIN_ID);
        preferences.putBoolean(REGULAR_EXPRESSION, regularExpressionButton.getSelection());
      }
    });
    text.addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        if (e.character == SWT.CR) {
          searchFor(text.getText(), caseSensitiveButton.getSelection(), regularExpressionButton.getSelection());
        }
      }
    });
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
        if (currentJob != null) {
          currentJob.schedule();
        }
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
    getViewSite().getActionBars().getToolBarManager().add(new Separator());
    Action sortAlphabeticallyAction = new Action("Sort alphabetically", IAction.AS_PUSH_BUTTON) {

      @Override
      public void run() {
        alphabSort = !alphabSort;
        IEclipsePreferences preferences = InstanceScope.INSTANCE.getNode(Activator.PLUGIN_ID);
        preferences.putBoolean(ALPHABETICAL_SORT, alphabSort);
        treeViewer.refresh();
      }
    };
    sortAlphabeticallyAction.setImageDescriptor(alphabSortImage);
    sortAlphabeticallyAction.setChecked(alphabSort);
    getViewSite().getActionBars().getToolBarManager().add(sortAlphabeticallyAction);
    Action groupByFolderAction = new Action("Group by folder") {

      @Override
      public void run() {
        Folder.cache.clear();
        groupByFolder = !groupByFolder;
        IEclipsePreferences preferences = InstanceScope.INSTANCE.getNode(Activator.PLUGIN_ID);
        preferences.putBoolean(GROUP_BY_FOLDER, groupByFolder);
        treeViewer.refresh();
      }
    };
    groupByFolderAction.setImageDescriptor(groupByFolderImage);
    getViewSite().getActionBars().getToolBarManager().add(groupByFolderAction);
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
  }

  @Override
  public void setFocus() {
    treeViewer.getControl().setFocus();
  }

  public void searchFor(String text, boolean caseSensitive, boolean regularExpression) {
    (currentJob = new Job("Searching for \"" + text + "\" with RipGrep ...") {

      @Override
      protected IStatus run(IProgressMonitor monitor) {
        Display.getDefault().asyncExec(() -> {
          searchAgainAction.setEnabled(false);
          cancelSearchAction.setEnabled(true);
          getViewSite().getActionBars().updateActionBars();
        });
        AtomicBoolean done = new AtomicBoolean();
        ERipSearchRequest request = new ERipSearchRequest();
        request.setProgressMonitor(monitor);
        request.setText(text);
        request.setCaseSensitive(caseSensitive);
        request.setRegularExpression(regularExpression);
        request.setListener(new ERipGrepProgressListener() {

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
                SeeAll.cache.clear();
              });
          }
        });
        Display.getDefault().asyncExec(() -> {
          SeeAll.cache.clear();
          Folder.cache.clear();
          treeViewer.setInput(ERipGrepEngine.searchFor(request));
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
      } else if (element instanceof RipGrepError) {
        return new StyledString(((RipGrepError) element).getError());
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
      } else if (element instanceof RipGrepError) {
        return errorImage;
      }
      return super.getImage(element);
    }

  }

  protected static Image createImageFromURL(String url) {
    ImageDescriptor imageDescriptor = createImageDescriptorFromURL(url);
    return imageDescriptor != null ? imageDescriptor.createImage() : null;
  }

  protected static ImageDescriptor createImageDescriptorFromURL(String url) {
    try {
      return ImageDescriptor.createFromURL(new URL(url));
    } catch (MalformedURLException e) {
    }
    return null;
  }

  private static class SeeAll {

    public static final int MAX_NUMBER = 50;

    private static final ConcurrentHashMap<Object, SeeAll> cache = new ConcurrentHashMap<>();

    private final Object element;
    private final List<?> objects;

    private SeeAll(Object element, List<?> objects) {
      this.element = element;
      this.objects = objects;
    }

    private Object[] toArray() {
      List<Object> otherObjects = new ArrayList<>();
      for (int i = MAX_NUMBER; i < objects.size(); i++) {
        otherObjects.add(objects.get(i));
      }
      if (alphabSort && element instanceof SearchProject) {
        otherObjects.sort(matchingFileComparator);
      }
      return otherObjects.toArray();
    }

    public List<?> getAllObjects() {
      return objects;
    }

    public static SeeAll getOrCreate(Object object, List<?> list) {
      return cache.computeIfAbsent(object, o -> new SeeAll(object, list));
    }
  }

  private static class Folder {

    private static final ConcurrentHashMap<Object, Folder> cache = new ConcurrentHashMap<>();

    private final SearchProject searchProject;
    private final File parentFile;
    private final String name;
    private final IFolder folder;

    private List<MatchingFile> matchingFiles;

    private Folder(SearchProject searchProject, File parentFile, String name, IFolder folder) {
      this.searchProject = searchProject;
      this.parentFile = parentFile;
      this.name = name;
      this.folder = folder;
    }

    public SearchProject getSearchProject() {
      return searchProject;
    }

    public File getParentFile() {
      return parentFile;
    }

    public String getName() {
      return name;
    }

    public IFolder getFolder() {
      return folder;
    }

    public List<MatchingFile> getMatchingFiles() {
      return matchingFiles;
    }

    public void setMatchingFiles(List<MatchingFile> matchingFiles) {
      this.matchingFiles = matchingFiles;
    }

    public static Folder getOrCreate(SearchProject searchProject, File parentFile, String name, IFolder folder) {
      return cache.computeIfAbsent(parentFile, o -> new Folder(searchProject, parentFile, name, folder));
    }
  }
}
