package org.eclipse.eripgrep;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

import org.eclipse.core.resources.*;
import org.eclipse.core.runtime.*;
import org.eclipse.core.runtime.preferences.*;

public class ERipGrepEngine {

  public static ERipGrepResponse searchFor(ERipSearchRequest request) {
    IEclipsePreferences preferences = InstanceScope.INSTANCE.getNode(Activator.PLUGIN_ID);
    File ripGrepFile = new File(preferences.get("RIPGREP_PATH", null));
    List<IProject> openedProjects = new ArrayList<>();
    List<IProject> closedProjects = new ArrayList<>();
    Arrays.asList(ResourcesPlugin.getWorkspace().getRoot().getProjects()).forEach(project -> (project.isOpen() ? openedProjects : closedProjects).add(project));
    ExecutorService executorService = Executors.newFixedThreadPool(1);
    try {
      openedProjects.forEach(project -> {
        executorService.submit(() -> searchFor(ripGrepFile, project, request));
      });
    } catch (Exception e) {
    }
    executorService.shutdown();
    return new ERipGrepResponse();
  }

  private static void searchFor(File ripGrepFile, IProject project, ERipSearchRequest request) {
    IProgressMonitor progressMonitor = new NullProgressMonitor();
    File projectDirectory = new File(project.getLocation().toOSString());
    ProcessBuilder processBuilder = new ProcessBuilder(ripGrepFile.getAbsolutePath(), request.getText() + " " + projectDirectory.getAbsolutePath());
    processBuilder.directory(projectDirectory);
    try {
      Process process = processBuilder.start();
      new Thread(() -> {
      try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
        String line;
        while (!progressMonitor.isCanceled() && (line = br.readLine()) != null) {
          System.out.println(line);
        }
      } catch (IOException e) {
        e.printStackTrace();
      }
      }).start();
      new Thread(() -> {
      try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
        String line;
        while (!progressMonitor.isCanceled() && (line = br.readLine()) != null) {
          System.err.println(line);
        }
      } catch (IOException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
      }).start();
      System.out.println(processBuilder.command());
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    System.out.println();
  }

}
