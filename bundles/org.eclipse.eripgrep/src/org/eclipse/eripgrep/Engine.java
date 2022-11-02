package org.eclipse.eripgrep;

import static org.eclipse.eripgrep.utils.PreferenceConstantes.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.*;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.eripgrep.model.*;
import org.eclipse.eripgrep.model.Error;
import org.eclipse.eripgrep.ui.UiUtils;
import org.eclipse.eripgrep.utils.Utils;
import org.eclipse.swt.widgets.Display;

public class Engine {

  public static Response searchFor(Request request) {
    IProgressMonitor progressMonitor = request.getProgressMonitor();
    Response response = new Response();
    String ripGrepFilePath = Utils.getPreferences().get(RIPGREP_PATH, null);
    if (ripGrepFilePath == null) {
      Display.getDefault().syncExec(() -> UiUtils.openPreferencePage());
    }
    File ripGrepFile = new File(ripGrepFilePath);
    boolean searchInClosedProject = Utils.getPreferences().getBoolean(SEARCH_IN_CLOSED_PROJECT, true);
    List<IProject> openedProjects = new ArrayList<>();
    List<IProject> closedProjects = new ArrayList<>();
    Arrays.asList(ResourcesPlugin.getWorkspace().getRoot().getProjects())
        .forEach(project -> (project.isOpen() ? openedProjects : closedProjects).add(project));
    ExecutorService executorService = Executors.newFixedThreadPool(Utils.getPreferences().getInt(THREAD_NUMBER, 5));
    Queue<IProject> projects = new ConcurrentLinkedQueue<>(openedProjects);
    if (searchInClosedProject) {
      projects.addAll(closedProjects);
    }
    LinkedHashMap<String, IProject> longerProjectByOSString = getLongerProjectByOSString(projects);
    removeSubDirectoryProject(projects);
    try {
      progressMonitor.beginTask("", projects.size());
      new ArrayList<>(projects).forEach(project -> executorService.submit(() -> {
        progressMonitor.subTask("Searching in " + project.getName());
        try {
          searchFor(request, response, ripGrepFile, project, longerProjectByOSString);
        } finally {
          projects.remove(project);
          progressMonitor.worked(1);
          if (projects.isEmpty()) {
            request.getListener().done();
            request.getProgressMonitor().done();
          }
        }
      }));
    } catch (Exception e) {
      Activator.error(e);
    }
    executorService.shutdown();
    return response;
  }

  private static LinkedHashMap<String, IProject> getLongerProjectByOSString(Queue<IProject> projects) {
    LinkedHashMap<String, IProject> longerProjectByOSString = new LinkedHashMap<>();
    List<IProject> longerProjects = new ArrayList<>(projects);
    longerProjects.sort((p1, p2) -> -p1.getLocation().toOSString().compareTo(p2.getLocation().toOSString()));
    longerProjects.forEach(project -> longerProjectByOSString.put(project.getLocation().toOSString(), project));
    return longerProjectByOSString;
  }

  private static void removeSubDirectoryProject(Queue<IProject> projects) {
    List<IProject> shorterProjects = new ArrayList<>(projects);
    shorterProjects.sort((p1, p2) -> p1.getLocation().toOSString().compareTo(p2.getLocation().toOSString()));
    List<IProject> longerProjects = new ArrayList<>(projects);
    longerProjects.sort((p1, p2) -> -p1.getLocation().toOSString().compareTo(p2.getLocation().toOSString()));
    for (IProject longerProject : longerProjects) {
      for (IProject shorterProject : shorterProjects) {
        if (longerProject != shorterProject && longerProject.getLocation().toOSString().contains(shorterProject.getLocation().toOSString())) {
          projects.remove(longerProject);
          shorterProjects.remove(longerProject);
          break;
        }
      }
    }
  }

  private static void searchFor(Request request, Response response, File ripGrepFile,
      IProject project, LinkedHashMap<String, IProject> longerProjectByOSString) {
    IProgressMonitor progressMonitor = request.getProgressMonitor();
    if (progressMonitor.isCanceled()) {
      return;
    }
    File projectDirectory = new File(project.getLocation().toOSString());
    List<String> commands = new ArrayList<>(Arrays.asList(ripGrepFile.getAbsolutePath(), request.getText(),
        projectDirectory.getAbsolutePath(), "--color", "always", "--pretty"));
    if (!request.isCaseSensitive()) {
      commands.add("-i");
    }
    if (!request.isRegularExpression()) {
      commands.add("--fixed-strings");
    }
    ProcessBuilder processBuilder = new ProcessBuilder(commands);
    processBuilder.directory(projectDirectory);
    try {
      Process process = processBuilder.start();
      new Thread(() -> {
        try (BufferedReader br = new BufferedReader(
            new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
          MatchingFile matchingFile = null;
          MatchingLine matchingLine = null;
          String line;
          while (!progressMonitor.isCanceled() && (line = br.readLine()) != null) {
            if (line.isEmpty()) {
              if (matchingFile != null) {
                matchingFile = null;
                request.getListener().update(response);
              }
            } else if (matchingFile == null) {
              String filePath = MatchingFile.getFilePath(line);
              SearchedProject searchProject = getOrCreateSearchProject(response, longerProjectByOSString, filePath);
              matchingFile = new MatchingFile(searchProject, line);
            } else {
              if (MatchingLine.isMatchingLine(line)) {
                matchingLine = new MatchingLine(matchingFile, line);
              } else {
                matchingFile.getMatchingLines().remove(matchingLine);
                matchingLine = new MatchingLine(matchingFile, matchingLine.getLine() + line);
              }
            }
          }
          if (matchingFile != null && !matchingFile.getMatchingLines().isEmpty()) {
            request.getListener().update(response);
          }
        } catch (IOException e) {
          Activator.error(e);
        }
        if (progressMonitor.isCanceled()) {
          process.destroyForcibly();
        }
      }).start();
      new Thread(() -> {
        try (BufferedReader br = new BufferedReader(
            new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
          String line;
          StringBuilder stringBuilder = new StringBuilder();
          while (!progressMonitor.isCanceled() && (line = br.readLine()) != null) {
            stringBuilder.append(line + "\n");
          }
          if (!stringBuilder.isEmpty()) {
            SearchedProject searchProject = getOrCreateSearchProject(response, longerProjectByOSString, project.getLocation().toOSString());
            new Error(searchProject, stringBuilder.toString());
          }
        } catch (IOException e) {
          Activator.error(e);
        }
      }).start();
      process.waitFor();
    } catch (IOException | InterruptedException e) {
      Activator.error(e);
    }
  }

  private static SearchedProject getOrCreateSearchProject(Response response, LinkedHashMap<String, IProject> longerProjectByOSString, String filePath) {
    IProject project = longerProjectByOSString.entrySet().stream()
        .filter(entry -> filePath.contains(entry.getKey()))
        .findFirst()
        .map(Entry::getValue)
        .orElse(null);
    return response.getSearchedProjects().stream()
        .filter(searchProject -> project.equals(searchProject.getProject()))
        .findFirst()
        .orElseGet(() -> new SearchedProject(response, project));
  }

}
