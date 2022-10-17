package org.eclipse.eripgrep.ui;

import static org.eclipse.eripgrep.ui.ERipGrepPreferencePage.ALPHABETICAL_SORT;

import java.io.*;
import java.net.*;
import java.nio.charset.Charset;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;

import org.eclipse.core.filesystem.*;
import org.eclipse.core.resources.*;
import org.eclipse.core.runtime.*;
import org.eclipse.core.runtime.jobs.*;
import org.eclipse.core.runtime.preferences.*;
import org.eclipse.eripgrep.*;
import org.eclipse.eripgrep.model.*;
import org.eclipse.eripgrep.utils.ExtendedBufferedReader;
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
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.*;
import org.eclipse.ui.*;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.part.ViewPart;
import org.eclipse.ui.texteditor.ITextEditor;
import org.mozilla.universalchardet.ReaderFactory;

/**
 * A view for RipGrep.
 */
public class ERipGrepViewPart extends ViewPart {

  public static final String ID = "org.eclipse.eripgrep.ERipGrepView";

  private static Object originalElement;

  private ImageDescriptor alphabSortImage = createImageDescriptorFromURL(
      "platform:/plugin/org.eclipse.jdt.ui/icons/full/elcl16/alphab_sort_co.png");

  private ImageDescriptor groupByFolderImage = createImageDescriptorFromURL(
      "platform:/plugin/org.eclipse.search/icons/full/etool16/group_by_folder.png");

  private static boolean alphabSort = InstanceScope.INSTANCE.getNode(Activator.PLUGIN_ID).getBoolean(ALPHABETICAL_SORT, false);
  private static boolean groupByFolder = InstanceScope.INSTANCE.getNode(Activator.PLUGIN_ID).getBoolean(ALPHABETICAL_SORT, false);
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
        treeViewer.refresh(matchingFile.getSearchProject());
      } else if (element instanceof MatchingLine) {
        MatchingLine matchingLine = (MatchingLine) element;
        matchingLine.getMatchingFile().getMatchingLines().remove(matchingLine);
        treeViewer.refresh(matchingLine.getMatchingFile());
      }
      if (element instanceof SeeAll) {
        SeeAll seeAll = (SeeAll) element;
        Arrays.asList(seeAll.toArray()).forEach(object -> seeAll.getAllObjects().remove(object));
        treeViewer.refresh(seeAll);
      }
    }

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
    parent.setLayout(new FillLayout(SWT.VERTICAL));
    treeViewer = new TreeViewer(parent, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL);
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
          return getMaxChildren(searchProject, searchProject.getMatchingFiles(), alphabSort ? matchingFileComparator : null);
        } else if (parentElement instanceof MatchingFile) {
          MatchingFile matchingFile = (MatchingFile) parentElement;
          return getMaxChildren(matchingFile, matchingFile.getMatchingLines(), null);
        } else if (parentElement instanceof SeeAll) {
          return ((SeeAll) parentElement).toArray();
        }
        return null;
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
        }
        return super.getStyledText(element);
      }

      @Override
      public Image getImage(Object element) {
        if (element instanceof SearchProject) {
          element = ((SearchProject) element).getProject();
        } else if (element instanceof MatchingFile && ((MatchingFile) element).getMatchingResource() != null) {
          element = ((MatchingFile) element).getMatchingResource();
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
        alphabSort = !alphabSort;
        IEclipsePreferences preferences = InstanceScope.INSTANCE.getNode(Activator.PLUGIN_ID);
        preferences.putBoolean(ALPHABETICAL_SORT, alphabSort);
        treeViewer.refresh();
      }
    };
    sortAlphabeticallyAction.setImageDescriptor(alphabSortImage);
    getViewSite().getActionBars().getToolBarManager().add(sortAlphabeticallyAction);
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

  public void searchFor(String text) {
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
        Display.getDefault().asyncExec(() -> treeViewer.setInput(ERipGrepEngine.searchFor(request)));
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
    private Image fileImage = createImageFromURL(
        "platform:/plugin/org.eclipse.ui.ide/icons/full/obj16/fileType_filter.png");
    private Image seeAllImage = createImageFromURL(
        "platform:/plugin/org.eclipse.search/icons/full/elcl16/hierarchicalLayout.png");

    public ERipGrepLabelProvider() {
      super(abstractTextSearchViewPage, SHOW_LABEL);
    }

    @Override
    public StyledString getStyledText(Object element) {
      if (element instanceof MatchingFile) {
        return new StyledString(((MatchingFile) element).getFileName());
      } else if (element instanceof MatchingLine) {
        return getStyledTextForMatchingLine((MatchingLine) element);
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
        System.out.println();
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

  private static class Directory {
    
  }
}
