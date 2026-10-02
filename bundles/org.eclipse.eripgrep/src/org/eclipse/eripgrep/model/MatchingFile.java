package org.eclipse.eripgrep.model;

import java.nio.file.Path;
import java.util.*;

import org.eclipse.core.resources.*;

public class MatchingFile {

  private final SearchedProject searchProject;

  private final List<MatchingLine> matchingLines = Collections.synchronizedList(new ArrayList<>());

  private final IResource matchingResource;
  private final String filePath;
  private final String fileName;
  private final String relativePath;

  public MatchingFile(SearchedProject searchProject, String filePath) {
    this.searchProject = searchProject;
    this.filePath = filePath;
    Path path = Path.of(filePath);
    this.fileName = path.getFileName().toString();
    this.relativePath = path.startsWith(searchProject.getLocation())
        ? searchProject.getLocation().relativize(path).toString().replace('\\', '/')
        : filePath.replace('\\', '/');
    this.matchingResource = initMatchingResource();
    searchProject.getMatchingFiles().add(this);
  }

  private IResource initMatchingResource() {
    IProject project = searchProject.getProject();
    if (project != null && project.isOpen()) {
      return project.findMember(relativePath);
    }
    return null;
  }

  public SearchedProject getSearchProject() {
    return searchProject;
  }

  /**
   * The matching lines and the lines of context, a synchronized list: copy it to iterate.
   */
  public List<MatchingLine> getMatchingLines() {
    return matchingLines;
  }

  public int getMatchCount() {
    int count = 0;
    for (MatchingLine matchingLine : List.copyOf(matchingLines)) {
      count += matchingLine.getSpans().size();
    }
    return count;
  }

  /**
   * @return <code>null</code> if the file is not in an opened project
   */
  public IResource getMatchingResource() {
    return matchingResource;
  }

  public String getFilePath() {
    return filePath;
  }

  public String getFileName() {
    return fileName;
  }

  /**
   * The path in the project, separated by slashes.
   */
  public String getRelativePath() {
    return relativePath;
  }

  /**
   * The directory in the project, separated by slashes; empty for a file at the root of the project.
   */
  public String getRelativeDirectory() {
    int i = relativePath.lastIndexOf('/');
    return i < 0 ? "" : relativePath.substring(0, i);
  }
}
