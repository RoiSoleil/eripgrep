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
import java.util.List;
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
      List<IProject> pendindProjects = new ArrayList<>(openedProjects);
      if (searchInClosedProject) {
        pendindProjects.addAll(closedProjects);
      }
      progressMonitor.beginTask("", pendindProjects.size());
      new ArrayList<>(pendindProjects).forEach(project -> executorService.submit(() -> {
        progressMonitor.subTask("Searching in " + project.getName());
        try {
          searchFor(request, eRipGrepResponse, ripGrepFile, project);
        } finally {
          pendindProjects.remove(project);
          progressMonitor.worked(1);
        }
        if (pendindProjects.isEmpty()) {
          request.getListener().done();
          request.getProgressMonitor().done();
        }
      }));
    } catch (Exception e) {
    }
    executorService.shutdown();
    return eRipGrepResponse;
  }

  private static SearchProject searchFor(ERipSearchRequest request, ERipGrepResponse response, File ripGrepFile,
      IProject project) {
    IProgressMonitor progressMonitor = request.getProgressMonitor();
    SearchProject searchProject = new SearchProject();
    searchProject.setProject(project);
    if (progressMonitor.isCanceled()) {
      return searchProject;
    }
    File projectDirectory = new File(project.getLocation().toOSString());
    ProcessBuilder processBuilder = new ProcessBuilder(ripGrepFile.getAbsolutePath(), request.getText(),
        projectDirectory.getAbsolutePath(), "--color", "always", "--pretty", "--fixed-strings");
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
                if (!response.getSearchProjects().contains(searchProject))
                  response.getSearchProjects().add(searchProject);
                matchingFile = null;
                request.getListener().update(response);
              }
            } else if (matchingFile == null) {
              matchingFile = new MatchingFile(searchProject, line);
            } else {
              new MatchingLine(matchingFile, line);
            }
          }
          if (matchingFile != null && !matchingFile.getMatchingLines().isEmpty()) {
            if (!response.getSearchProjects().contains(searchProject))
              response.getSearchProjects().add(searchProject);
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
          while (!progressMonitor.isCanceled() && (line = br.readLine()) != null) {
            System.err.println(line);
          }
        } catch (IOException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        }
      }).start();
      process.waitFor();
      request.getListener().update(searchProject);
    } catch (IOException | InterruptedException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    return searchProject;
  }

}
