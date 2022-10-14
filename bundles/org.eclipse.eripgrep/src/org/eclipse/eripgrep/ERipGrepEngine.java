package org.eclipse.eripgrep;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

import org.eclipse.core.resources.*;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.preferences.*;
import org.eclipse.eripgrep.model.*;

public class ERipGrepEngine {

  public static ERipGrepResponse searchFor(ERipSearchRequest request) {
    IProgressMonitor progressMonitor = request.getProgressMonitor();
    ERipGrepResponse eRipGrepResponse = new ERipGrepResponse();
    IEclipsePreferences preferences = InstanceScope.INSTANCE.getNode(Activator.PLUGIN_ID);
    File ripGrepFile = new File(preferences.get("RIPGREP_PATH", null));
    boolean searchInClosedProject = preferences.getBoolean("SEARCH_IN_CLOSED_PROJECT", true);
    List<IProject> openedProjects = new ArrayList<>();
    List<IProject> closedProjects = new ArrayList<>();
    Arrays.asList(ResourcesPlugin.getWorkspace().getRoot().getProjects())
        .forEach(project -> (project.isOpen() ? openedProjects : closedProjects).add(project));
    ExecutorService executorService = Executors.newFixedThreadPool(3);
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

  private static SearchProject searchFor(ERipSearchRequest request, ERipGrepResponse response, File ripGrepFile, IProject project) {
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
          if (matchingFile != null) {
            response.getSearchProjects().add(searchProject);
            request.getListener().update(response);
          }
        } catch (IOException e) {
          e.printStackTrace();
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
