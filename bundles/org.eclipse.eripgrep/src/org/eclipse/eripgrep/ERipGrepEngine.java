package org.eclipse.eripgrep;

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
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.eripgrep.model.ERipGrepResponse;
import org.eclipse.eripgrep.model.ERipSearchRequest;
import org.eclipse.eripgrep.model.MatchingFile;
import org.eclipse.eripgrep.model.MatchingLine;
import org.eclipse.eripgrep.model.SearchProject;

public class ERipGrepEngine {

  private final static String FILEPATH_PREFIX = "[0m[35m";
  private final static String FILEPATH_SUFIX = "[0m";

  public static ERipGrepResponse searchFor(ERipSearchRequest request) {
    ERipGrepResponse eRipGrepResponse = new ERipGrepResponse();
    IEclipsePreferences preferences = InstanceScope.INSTANCE.getNode(Activator.PLUGIN_ID);
    File ripGrepFile = new File(preferences.get("RIPGREP_PATH", null));
    List<IProject> openedProjects = new ArrayList<>();
    List<IProject> closedProjects = new ArrayList<>();
    Arrays.asList(ResourcesPlugin.getWorkspace().getRoot().getProjects())
        .forEach(project -> (project.isOpen() ? openedProjects : closedProjects).add(project));
    ExecutorService executorService = Executors.newFixedThreadPool(1);
    try {
      List<IProject> pendindProjects = new ArrayList<>(openedProjects);
      openedProjects.forEach(project -> {
        executorService.submit(() -> {
          try {
            searchFor(eRipGrepResponse, ripGrepFile, project, request);
          } finally {
            pendindProjects.remove(project);
          }
          if (pendindProjects.isEmpty()) {
            request.getListener().done();
          }
        });
      });
    } catch (Exception e) {
    }
    executorService.shutdown();
    return eRipGrepResponse;
  }

  private static SearchProject searchFor(ERipGrepResponse response, File ripGrepFile, IProject project,
      ERipSearchRequest request) {
    SearchProject searchProject = new SearchProject();
    searchProject.setProject(project);
    IProgressMonitor progressMonitor = new NullProgressMonitor();
    File projectDirectory = new File(project.getLocation().toOSString());
    ProcessBuilder processBuilder = new ProcessBuilder(ripGrepFile.getAbsolutePath(), request.getText(),
        projectDirectory.getAbsolutePath(), "--color", "always", "--pretty");
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
                searchProject.getMatchingFiles().add(matchingFile);
                matchingFile = null;
                request.getListener().update(response);
              }
            } else if (line.startsWith(FILEPATH_PREFIX)) {
              matchingFile = new MatchingFile();
              matchingFile
                  .setFilePath(line.substring(FILEPATH_PREFIX.length(), line.length() - FILEPATH_SUFIX.length()));
            } else {
              MatchingLine matchingLine = new MatchingLine();
              matchingLine.setLine(line);
              matchingFile.getMatchingLines().add(matchingLine);
            }
          }
          if (matchingFile != null) {
            response.getSearchProjects().add(searchProject);
            searchProject.getMatchingFiles().add(matchingFile);
            matchingFile = null;
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
//      System.out.println(processBuilder.command());
      process.waitFor();
      request.getListener().update(searchProject);
    } catch (IOException | InterruptedException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    return searchProject;
  }

}
