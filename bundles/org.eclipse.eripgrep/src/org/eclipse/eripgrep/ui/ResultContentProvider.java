package org.eclipse.eripgrep.ui;

import java.util.*;

import org.eclipse.eripgrep.model.*;
import org.eclipse.eripgrep.ui.model.Folder;
import org.eclipse.eripgrep.ui.model.SeeAll;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.TreePath;

/**
 * The tree of the results: projects, folders if grouped by folder, files, lines.
 */
public class ResultContentProvider implements ITreeContentProvider {

  private static final Comparator<MatchingFile> MATCHINGFILE_COMPARATOR = Comparator.comparing(MatchingFile::getRelativePath);

  private boolean alphabeticalSort;
  private boolean groupByFolder;

  public boolean isAlphabeticalSort() {
    return alphabeticalSort;
  }

  public void setAlphabeticalSort(boolean alphabeticalSort) {
    this.alphabeticalSort = alphabeticalSort;
  }

  public boolean isGroupByFolder() {
    return groupByFolder;
  }

  public void setGroupByFolder(boolean groupByFolder) {
    this.groupByFolder = groupByFolder;
  }

  @Override
  public Object[] getElements(Object inputElement) {
    return getChildren(inputElement);
  }

  @Override
  public boolean hasChildren(Object element) {
    return !(element instanceof MatchingLine) && !(element instanceof org.eclipse.eripgrep.model.Error);
  }

  @Override
  public Object getParent(Object element) {
    if (element instanceof MatchingLine matchingLine) {
      return matchingLine.getMatchingFile();
    } else if (element instanceof SearchedProject searchedProject) {
      return searchedProject.getResponse();
    } else if (element instanceof SeeAll seeAll) {
      return seeAll.parent();
    }
    return null;
  }

  @Override
  public Object[] getChildren(Object parentElement) {
    if (parentElement instanceof SeeAll seeAll) {
      List<?> children = getAllChildren(seeAll.parent());
      return children.subList(Math.min(SeeAll.MAX_NUMBER, children.size()), children.size()).toArray();
    }
    List<?> children = getAllChildren(parentElement);
    if (children.size() <= SeeAll.MAX_NUMBER) {
      return children.toArray();
    }
    List<Object> firstChildren = new ArrayList<>(children.subList(0, SeeAll.MAX_NUMBER));
    firstChildren.add(new SeeAll(parentElement));
    return firstChildren.toArray();
  }

  /**
   * The number of children under a "See all" node.
   */
  public int getRemainingCount(SeeAll seeAll) {
    return Math.max(0, getAllChildren(seeAll.parent()).size() - SeeAll.MAX_NUMBER);
  }

  private List<?> getAllChildren(Object parentElement) {
    if (parentElement instanceof Response response) {
      List<Object> children = new ArrayList<>(response.getErrors());
      List<SearchedProject> searchedProjects = new ArrayList<>(response.getSearchedProjects());
      if (alphabeticalSort) {
        searchedProjects.sort(Comparator.comparing(SearchedProject::getName));
      }
      children.addAll(searchedProjects);
      return children;
    } else if (parentElement instanceof SearchedProject searchedProject) {
      List<MatchingFile> matchingFiles = getMatchingFiles(searchedProject);
      return groupByFolder ? Folder.getChildren(searchedProject, "", matchingFiles) : matchingFiles;
    } else if (parentElement instanceof Folder folder) {
      return Folder.getChildren(folder.searchProject(), folder.path(), getMatchingFiles(folder.searchProject()));
    } else if (parentElement instanceof MatchingFile matchingFile) {
      return new ArrayList<>(matchingFile.getMatchingLines());
    }
    return List.of();
  }

  private List<MatchingFile> getMatchingFiles(SearchedProject searchedProject) {
    List<MatchingFile> matchingFiles = new ArrayList<>(searchedProject.getMatchingFiles());
    if (alphabeticalSort) {
      matchingFiles.sort(MATCHINGFILE_COMPARATOR);
    }
    return matchingFiles;
  }

  /**
   * Returns the paths of the matching lines, in the order of the tree.
   */
  public List<TreePath> getMatchingLinePaths(Response response) {
    List<TreePath> paths = new ArrayList<>();
    if (response != null) {
      collectMatchingLinePaths(response, TreePath.EMPTY, paths);
    }
    return paths;
  }

  private void collectMatchingLinePaths(Object parent, TreePath parentPath, List<TreePath> paths) {
    for (Object child : getChildren(parent)) {
      TreePath path = parentPath.createChildPath(child);
      if (child instanceof MatchingLine matchingLine) {
        if (!matchingLine.isContext()) {
          paths.add(path);
        }
      } else {
        collectMatchingLinePaths(child, path, paths);
      }
    }
  }
}
