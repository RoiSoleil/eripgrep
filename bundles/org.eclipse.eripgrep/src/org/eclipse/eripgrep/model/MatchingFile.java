package org.eclipse.eripgrep.model;

import java.io.File;
import java.util.*;

import org.eclipse.core.resources.*;

public class MatchingFile {

  private final static int FILEPATH_PREFIX_LENGTH = 9;
  private final static int FILEPATH_SUFIX_LENGTH = 4;

  private final SearchedProject searchProject;
  private final String filePathString;

  private final List<MatchingLine> matchingLines = new ArrayList<>();

  private final IResource matchingResource;
  private final String filePath;
  private final String fileName;

  public MatchingFile(SearchedProject searchProject, String filePathString) {
    this.searchProject = searchProject;
    this.filePathString = filePathString;
    this.filePath = getFilePath(filePathString);
    this.matchingResource = initMatchingResource(filePath);
    this.fileName = new File(filePath).getName();
    searchProject.getMatchingFiles().add(this);
  }

  public static String getFilePath(String filePathString) {
    return filePathString.substring(FILEPATH_PREFIX_LENGTH, filePathString.length() - FILEPATH_SUFIX_LENGTH);
  }

  private IResource initMatchingResource(String filePath) {
    IProject project = searchProject.getProject();
    if (project.isOpen()) {
      return project.findMember(filePath.substring(project.getLocation().toOSString().length()));
    }
    return null;
  }

  public SearchedProject getSearchProject() {
    return searchProject;
  }

  public String getFilePathString() {
    return filePathString;
  }

  public List<MatchingLine> getMatchingLines() {
    return matchingLines;
  }

  public IResource getMatchingResource() {
    return matchingResource;
  }

  public String getFilePath() {
    return filePath;
  }

  public String getFileName() {
    return fileName;
  }
}
