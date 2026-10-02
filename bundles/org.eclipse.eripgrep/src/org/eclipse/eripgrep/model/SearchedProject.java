package org.eclipse.eripgrep.model;

import java.nio.file.Path;
import java.util.*;

import org.eclipse.core.resources.IProject;

public class SearchedProject {

  private final Response response;
  private final IProject project;
  private final Path location;

  private final List<MatchingFile> matchingFiles = Collections.synchronizedList(new ArrayList<>());

  /**
   * @param project  <code>null</code> for the files which are not in a project
   * @param location the directory of the project
   */
  public SearchedProject(Response response, IProject project, Path location) {
    this.response = response;
    this.project = project;
    this.location = location;
    response.getSearchedProjects().add(this);
  }

  public Response getResponse() {
    return response;
  }

  public IProject getProject() {
    return project;
  }

  public String getName() {
    return project != null ? project.getName() : location.toString();
  }

  public Path getLocation() {
    return location;
  }

  /**
   * A synchronized list: copy it to iterate.
   */
  public List<MatchingFile> getMatchingFiles() {
    return matchingFiles;
  }

  public int getMatchCount() {
    int count = 0;
    for (MatchingFile matchingFile : List.copyOf(matchingFiles)) {
      count += matchingFile.getMatchCount();
    }
    return count;
  }
}
