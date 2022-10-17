package org.eclipse.eripgrep;

import static org.eclipse.eripgrep.ui.ERipGrepPreferencePage.RIPGREP_PATH;
import static org.eclipse.eripgrep.ui.ERipGrepPreferencePage.SEARCH_IN_CLOSED_PROJECT;
import static org.eclipse.eripgrep.ui.ERipGrepPreferencePage.THREAD_NUMBER;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.eripgrep.model.ERipGrepResponse;
import org.eclipse.eripgrep.model.ERipSearchRequest;
import org.eclipse.eripgrep.model.MatchingFile;
import org.eclipse.eripgrep.model.MatchingLine;
import org.eclipse.eripgrep.model.RipGrepError;
import org.eclipse.eripgrep.model.SearchProject;
import org.eclipse.jface.preference.PreferenceDialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.dialogs.PreferencesUtil;

public class ERipGrepEngine {

  public static ERipGrepResponse searchFor(ERipSearchRequest request) {
    IProgressMonitor progressMonitor = request.getProgressMonitor();
    ERipGrepResponse eRipGrepResponse = new ERipGrepResponse();
    IEclipsePreferences preferences = InstanceScope.INSTANCE.getNode(Activator.PLUGIN_ID);
    String ripGrepFilePath = preferences.get(RIPGREP_PATH, null);
    if (ripGrepFilePath == null) {
      Display.getDefault().syncExec(() -> {
        PreferenceDialog dialog = PreferencesUtil.createPreferenceDialogOn(Display.getDefault().getActiveShell(),
            "org.eclipse.eripgrep.PreferencePage", new String[] { "org.eclipse.eripgrep.PreferencePage" }, null);
        dialog.open();
      });
    }
    File ripGrepFile = new File(ripGrepFilePath);
    boolean searchInClosedProject = preferences.getBoolean(SEARCH_IN_CLOSED_PROJECT, true);
    List<IProject> openedProjects = new ArrayList<>();
    List<IProject> closedProjects = new ArrayList<>();
    Arrays.asList(ResourcesPlugin.getWorkspace().getRoot().getProjects())
        .forEach(project -> (project.isOpen() ? openedProjects : closedProjects).add(project));
    ExecutorService executorService = Executors.newFixedThreadPool(preferences.getInt(THREAD_NUMBER, 5));
    try {
      List<IProject> projects = new ArrayList<>(openedProjects);
      if (searchInClosedProject) {
        projects.addAll(closedProjects);
      }
      LinkedHashMap<String, IProject> longerProjectByOSString = getLongerProjectByOSString(projects);
      removeSubDirectoryProject(projects);
      progressMonitor.beginTask("", projects.size());
      new ArrayList<>(projects).forEach(project -> executorService.submit(() -> {
        progressMonitor.subTask("Searching in " + project.getName());
        try {
          searchFor(request, eRipGrepResponse, ripGrepFile, project, longerProjectByOSString);
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
    }
    executorService.shutdown();
    return eRipGrepResponse;
  }

  private static LinkedHashMap<String, IProject> getLongerProjectByOSString(List<IProject> projects) {
    LinkedHashMap<String, IProject> longerProjectByOSString = new LinkedHashMap<>();
    List<IProject> longerProjects = new ArrayList<>(projects);
    longerProjects.sort((p1, p2) -> -p1.getLocation().toOSString().compareTo(p2.getLocation().toOSString()));
    longerProjects.forEach(project -> longerProjectByOSString.put(project.getLocation().toOSString(), project));
    return longerProjectByOSString;
  }

  private static void removeSubDirectoryProject(List<IProject> projects) {
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

  private static void searchFor(ERipSearchRequest request, ERipGrepResponse response, File ripGrepFile,
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
          String line;
          while (!progressMonitor.isCanceled() && (line = br.readLine()) != null) {
            if (line.isEmpty()) {
              if (matchingFile != null) {
                matchingFile = null;
                request.getListener().update(response);
              }
            } else if (matchingFile == null) {
              String filePath = MatchingFile.getFilePath(line);
              SearchProject searchProject = getOrCreateSearchProject(response, longerProjectByOSString, filePath);
              matchingFile = new MatchingFile(searchProject, line);
            } else {
              new MatchingLine(matchingFile, line);
            }
          }
          if (matchingFile != null && !matchingFile.getMatchingLines().isEmpty()) {
            request.getListener().update(response);
          }
        } catch (IOException e) {
          e.printStackTrace();
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
            SearchProject searchProject = getOrCreateSearchProject(response, longerProjectByOSString, project.getLocation().toOSString());
            new RipGrepError(searchProject, stringBuilder.toString());
          }
        } catch (IOException e) {
        }
      }).start();
      process.waitFor();
    } catch (IOException | InterruptedException e) {
    }

  }

  private static SearchProject getOrCreateSearchProject(ERipGrepResponse response, LinkedHashMap<String, IProject> longerProjectByOSString, String filePath) {
    IProject project = longerProjectByOSString.entrySet().stream()
        .filter(entry -> filePath.contains(entry.getKey()))
        .findFirst()
        .map(Entry::getValue)
        .orElse(null);
    return response.getSearchProjects().stream()
        .filter(searchProject -> project.equals(searchProject.getProject()))
        .findFirst()
        .orElseGet(() -> new SearchProject(response, project));
  }

}
