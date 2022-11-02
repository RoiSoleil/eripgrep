package org.eclipse.eripgrep.ui.model;

import java.io.File;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.core.resources.IFolder;
import org.eclipse.eripgrep.model.*;

public class Folder {

  private static final ConcurrentHashMap<Object, Folder> cache = new ConcurrentHashMap<>();

  private final SearchedProject searchProject;
  private final File parentFile;
  private final String name;
  private final IFolder folder;

  private List<MatchingFile> matchingFiles;

  private Folder(SearchedProject searchProject, File parentFile, String name, IFolder folder) {
    this.searchProject = searchProject;
    this.parentFile = parentFile;
    this.name = name;
    this.folder = folder;
  }

  public SearchedProject getSearchProject() {
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

  public static Folder getOrCreate(SearchedProject searchProject, File parentFile, String name, IFolder folder) {
    return cache.computeIfAbsent(new File(parentFile, name), o -> new Folder(searchProject, parentFile, name, folder));
  }

  public static void clear() {
    cache.clear();
  }
}