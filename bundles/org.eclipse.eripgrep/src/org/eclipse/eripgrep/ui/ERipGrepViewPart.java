package org.eclipse.eripgrep.ui;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.ISafeRunnable;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.SafeRunner;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.IJobChangeEvent;
import org.eclipse.core.runtime.jobs.IJobChangeListener;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.eripgrep.ERipGrepEngine;
import org.eclipse.eripgrep.ERipGrepProgressListener;
import org.eclipse.eripgrep.model.ERipGrepResponse;
import org.eclipse.eripgrep.model.ERipSearchRequest;
import org.eclipse.eripgrep.model.MatchingFile;
import org.eclipse.eripgrep.model.MatchingLine;
import org.eclipse.eripgrep.model.SearchProject;
import org.eclipse.eripgrep.utils.ExtendedBufferedReader;
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
import org.eclipse.search2.internal.ui.basic.views.ShowNextResultAction;
import org.eclipse.search2.internal.ui.basic.views.ShowPreviousResultAction;
import org.eclipse.search2.internal.ui.basic.views.TreeViewerNavigator;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
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

  private static Object originalElement;

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
          // TODO Auto-generated catch block
          e.printStackTrace();
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
          // TODO Auto-generated catch block
          e.printStackTrace();
        }
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

  public ERipGrepViewPart() {
  }

  @Override
  public void createPartControl(Composite parent) {
    getViewSite().getActionBars().getToolBarManager().add(new ShowNextResultAction(abstractTextSearchViewPage));
    getViewSite().getActionBars().getToolBarManager().add(new ShowPreviousResultAction(abstractTextSearchViewPage));
    getViewSite().getActionBars().getToolBarManager().add(new Separator());
    ExpandAllAction expandAllAction = new ExpandAllAction();
    getViewSite().getActionBars().getToolBarManager().add(expandAllAction);
    CollapseAllAction collapseAllAction = new CollapseAllAction();
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
          return ((ERipGrepResponse) inputElement).getSearchProjects().toArray();
        }
        return null;
      }

      @Override
      public Object[] getChildren(Object parentElement) {
        if (parentElement instanceof SearchProject) {
          SearchProject searchProject = (SearchProject) parentElement;
          return getMaxChildren(searchProject, searchProject.getMatchingFiles());
        } else if (parentElement instanceof MatchingFile) {
          MatchingFile matchingFile = (MatchingFile) parentElement;
          return getMaxChildren(matchingFile, matchingFile.getMatchingLines());
        } else if (parentElement instanceof SeeAll) {
          return ((SeeAll) parentElement).toArray();
        }
        return null;
      }

      private Object[] getMaxChildren(Object element, List<?> collection) {
        List<Object> objects = new ArrayList<>();
        for (int i = 0; i < collection.size() && i < SeeAll.MAX_NUMBER; i++) {
          objects.add(collection.get(i));
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

          /**
           * @throws Exception
           */
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
    currentJob.addJobChangeListener(new IJobChangeListener() {

      @Override
      public void sleeping(IJobChangeEvent event) {
      }

      @Override
      public void scheduled(IJobChangeEvent event) {
      }

      @Override
      public void running(IJobChangeEvent event) {
      }

      @Override
      public void done(IJobChangeEvent event) {
        Display.getDefault().asyncExec(() -> {
          searchAgainAction.setEnabled(true);
          cancelSearchAction.setEnabled(false);
          getViewSite().getActionBars().updateActionBars();
        });
      }

      @Override
      public void awake(IJobChangeEvent event) {
      }

      @Override
      public void aboutToRun(IJobChangeEvent event) {
      }
    });
    ;
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
    try {
      return ImageDescriptor.createFromURL(new URL(url)).createImage();
    } catch (MalformedURLException e) {
      // Rien é faire.
    }
    return null;
  }

  private static class SeeAll {

    public static final int MAX_NUMBER = 50;
    private static final ConcurrentHashMap<Object, SeeAll> cache = new ConcurrentHashMap<>();
    private List<?> objects;

    private SeeAll(List<?> objects) {
      this.objects = objects;
    }

    private Object[] toArray() {
      List<Object> otherObjects = new ArrayList<>();
      for (int i = MAX_NUMBER; i < objects.size(); i++) {
        otherObjects.add(objects.get(i));
      }
      return otherObjects.toArray();
    }

    public static SeeAll getOrCreate(Object object, List<?> list) {
      return cache.computeIfAbsent(object, o -> new SeeAll(list));
    }
  }

}
