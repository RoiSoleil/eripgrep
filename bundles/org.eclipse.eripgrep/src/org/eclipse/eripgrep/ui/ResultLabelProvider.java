package org.eclipse.eripgrep.ui;

import org.eclipse.core.resources.IResource;
import org.eclipse.eripgrep.core.LineExcerpt;
import org.eclipse.eripgrep.core.Span;
import org.eclipse.eripgrep.model.Error;
import org.eclipse.eripgrep.model.MatchingFile;
import org.eclipse.eripgrep.model.MatchingLine;
import org.eclipse.eripgrep.model.SearchedProject;
import org.eclipse.eripgrep.ui.model.Folder;
import org.eclipse.eripgrep.ui.model.SeeAll;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.resource.LocalResourceManager;
import org.eclipse.jface.resource.ResourceManager;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.DelegatingStyledCellLabelProvider.IStyledLabelProvider;
import org.eclipse.jface.viewers.ILabelDecorator;
import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.jface.viewers.LabelProviderChangedEvent;
import org.eclipse.jface.viewers.StyledString;
import org.eclipse.jface.viewers.StyledString.Styler;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.TextStyle;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.model.WorkbenchLabelProvider;

/**
 * The labels of the results.
 */
public class ResultLabelProvider extends ColumnLabelProvider implements IStyledLabelProvider {

  /**
   * The color of the matches in the Search view.
   */
  public static final String HIGHLIGHT_COLOR = "org.eclipse.search.ui.match.highlight";

  private static final Styler HIGHLIGHT_STYLER = new Styler() {
    @Override
    public void applyStyles(TextStyle textStyle) {
      textStyle.background = JFaceResources.getColorRegistry().get(HIGHLIGHT_COLOR);
      textStyle.foreground = getReadableColor(textStyle.background);
    }
  };
  private static final int MAX_LINE_LENGTH = 400;

  private final ResultContentProvider contentProvider;
  private final ResourceManager resourceManager = new LocalResourceManager(JFaceResources.getResources());
  private final WorkbenchLabelProvider workbenchLabelProvider = new WorkbenchLabelProvider();
  private final ILabelDecorator decorator = PlatformUI.getWorkbench().getDecoratorManager().getLabelDecorator();
  private final ILabelProviderListener decoratorListener = event -> fireLabelProviderChanged(new LabelProviderChangedEvent(this));

  private final Image lineImage = resourceManager.createImage(UiUtils.createImageDescriptorFromURL(
      "platform:/plugin/org.eclipse.search/icons/full/obj16/line_match.png"));
  private final Image seeAllImage = resourceManager.createImage(UiUtils.createImageDescriptorFromURL(
      "platform:/plugin/org.eclipse.search/icons/full/elcl16/hierarchicalLayout.png"));

  /**
   * The color of a text on a background: the highlight of a light theme stays readable in a dark window.
   *
   * @return <code>null</code> without background
   */
  public static Color getReadableColor(Color background) {
    if (background == null) {
      return null;
    }
    int luminance = (299 * background.getRed() + 587 * background.getGreen() + 114 * background.getBlue()) / 1000;
    return background.getDevice().getSystemColor(luminance > 128 ? SWT.COLOR_BLACK : SWT.COLOR_WHITE);
  }

  public ResultLabelProvider(ResultContentProvider contentProvider) {
    this.contentProvider = contentProvider;
    decorator.addListener(decoratorListener);
  }

  @Override
  public String getText(Object element) {
    return getStyledText(element).getString();
  }

  @Override
  public StyledString getStyledText(Object element) {
    if (element instanceof SearchedProject searchedProject) {
      int matchCount = searchedProject.getMatchCount();
      return new StyledString(searchedProject.getName())
          .append(" (" + matchCount + (matchCount == 1 ? " match)" : " matches)"), StyledString.COUNTER_STYLER);
    } else if (element instanceof Folder folder) {
      return new StyledString(folder.getName());
    } else if (element instanceof MatchingFile matchingFile) {
      StyledString styledString = new StyledString(matchingFile.getFileName());
      if (!contentProvider.isGroupByFolder() && !matchingFile.getRelativeDirectory().isEmpty()) {
        styledString.append(" - " + matchingFile.getRelativeDirectory(), StyledString.QUALIFIER_STYLER);
      }
      return styledString.append(" (" + matchingFile.getMatchCount() + ")", StyledString.COUNTER_STYLER);
    } else if (element instanceof MatchingLine matchingLine) {
      return getStyledTextForMatchingLine(matchingLine);
    } else if (element instanceof SeeAll seeAll) {
      return new StyledString("See all " + contentProvider.getRemainingCount(seeAll) + " remaining elements");
    } else if (element instanceof Error error) {
      return new StyledString(error.getError());
    }
    return new StyledString(String.valueOf(element));
  }

  private StyledString getStyledTextForMatchingLine(MatchingLine matchingLine) {
    LineExcerpt excerpt = LineExcerpt.of(matchingLine.getLine(), matchingLine.getSpans(), MAX_LINE_LENGTH);
    StyledString styledString = new StyledString();
    styledString.append(matchingLine.getLineNumber() + ": ", StyledString.QUALIFIER_STYLER);
    if (matchingLine.isContext()) {
      return styledString.append(excerpt.text(), StyledString.QUALIFIER_STYLER);
    }
    int i = 0;
    for (Span span : excerpt.spans()) {
      styledString.append(excerpt.text().substring(i, span.start()));
      styledString.append(excerpt.text().substring(span.start(), span.end()), HIGHLIGHT_STYLER);
      i = span.end();
    }
    return styledString.append(excerpt.text().substring(i));
  }

  @Override
  public Image getImage(Object element) {
    ISharedImages sharedImages = PlatformUI.getWorkbench().getSharedImages();
    if (element instanceof SearchedProject searchedProject) {
      if (searchedProject.getProject() == null) {
        return sharedImages.getImage(ISharedImages.IMG_OBJ_FOLDER);
      } else if (!searchedProject.getProject().isOpen()) {
        return sharedImages.getImage(IDE.SharedImages.IMG_OBJ_PROJECT_CLOSED);
      }
      return getResourceImage(searchedProject.getProject());
    } else if (element instanceof Folder) {
      return sharedImages.getImage(ISharedImages.IMG_OBJ_FOLDER);
    } else if (element instanceof MatchingFile matchingFile) {
      if (matchingFile.getMatchingResource() != null) {
        return getResourceImage(matchingFile.getMatchingResource());
      }
      ImageDescriptor descriptor = PlatformUI.getWorkbench().getEditorRegistry().getImageDescriptor(matchingFile.getFileName());
      return descriptor != null ? resourceManager.createImage(descriptor) : sharedImages.getImage(ISharedImages.IMG_OBJ_FILE);
    } else if (element instanceof MatchingLine matchingLine) {
      return matchingLine.isContext() ? null : lineImage;
    } else if (element instanceof SeeAll) {
      return seeAllImage;
    } else if (element instanceof Error) {
      return sharedImages.getImage(ISharedImages.IMG_OBJS_ERROR_TSK);
    }
    return null;
  }

  /**
   * The image of a resource with its decorations (problems, version control...).
   */
  private Image getResourceImage(IResource resource) {
    Image image = workbenchLabelProvider.getImage(resource);
    Image decorated = decorator.decorateImage(image, resource);
    return decorated != null ? decorated : image;
  }

  @Override
  public void dispose() {
    decorator.removeListener(decoratorListener);
    workbenchLabelProvider.dispose();
    resourceManager.dispose();
    super.dispose();
  }
}
