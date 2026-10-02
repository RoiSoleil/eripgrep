package org.eclipse.eripgrep.ui.model;

import java.util.*;

import org.eclipse.eripgrep.model.*;

/**
 * A folder of the results grouped by folder. A chain of folders with a single child is shown as one folder.
 *
 * @param parentPath the path, in the project, of the folder it is shown in; empty for the project
 * @param path       the path in the project, separated by slashes
 */
public record Folder(SearchedProject searchProject, String parentPath, String path) {

  /**
   * The name of the folder relative to its parent, e.g. "src/org/eclipse" under the project.
   */
  public String getName() {
    return parentPath.isEmpty() ? path : path.substring(parentPath.length() + 1);
  }

  public boolean contains(MatchingFile matchingFile) {
    return matchingFile.getSearchProject() == searchProject && matchingFile.getRelativePath().startsWith(path + "/");
  }

  /**
   * Returns the folders, sorted, then the files which are directly in a folder of a project.
   *
   * @param path          the path of the folder, empty for the project
   * @param matchingFiles the matching files of the project
   */
  public static List<Object> getChildren(SearchedProject searchProject, String path, List<MatchingFile> matchingFiles) {
    String prefix = path.isEmpty() ? "" : path + "/";
    Map<String, List<MatchingFile>> filesByFolder = new TreeMap<>();
    List<MatchingFile> files = new ArrayList<>();
    for (MatchingFile matchingFile : matchingFiles) {
      String relativePath = matchingFile.getRelativePath();
      if (!relativePath.startsWith(prefix)) {
        continue;
      }
      int i = relativePath.indexOf('/', prefix.length());
      if (i < 0) {
        files.add(matchingFile);
      } else {
        filesByFolder.computeIfAbsent(relativePath.substring(0, i), folder -> new ArrayList<>()).add(matchingFile);
      }
    }
    List<Object> children = new ArrayList<>();
    filesByFolder.forEach((folder, folderFiles) -> children.add(new Folder(searchProject, path, compact(folder, folderFiles))));
    children.addAll(files);
    return children;
  }

  /**
   * Extends the path of a folder while it only contains one folder.
   */
  private static String compact(String path, List<MatchingFile> matchingFiles) {
    while (true) {
      String prefix = path + "/";
      String folder = null;
      for (MatchingFile matchingFile : matchingFiles) {
        String relativePath = matchingFile.getRelativePath();
        int i = relativePath.indexOf('/', prefix.length());
        if (i < 0) {
          return path;
        }
        String fileFolder = relativePath.substring(0, i);
        if (folder == null) {
          folder = fileFolder;
        } else if (!folder.equals(fileFolder)) {
          return path;
        }
      }
      path = folder;
    }
  }
}
