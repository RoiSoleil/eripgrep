package org.eclipse.eripgrep;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.core.resources.*;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.eripgrep.core.*;
import org.eclipse.eripgrep.model.*;
import org.eclipse.eripgrep.model.Error;

public class Engine {

  private static final int MAX_ERRORS = 20;

  /**
   * Runs RipGrep and fills the response, which can be shown while the search runs. Returns at the end of the
   * search.
   */
  public static void search(Request request, Response response, RipGrepSettings settings, ProgressListener listener,
      IProgressMonitor monitor) {
    long start = System.nanoTime();
    try {
      monitor.beginTask("Searching for \"" + request.getText() + "\"", IProgressMonitor.UNKNOWN);
      if (settings.ripGrepPath() == null) {
        response.setRipGrepMissing(true);
        response.getErrors().add(new Error("RipGrep was not found: install it or set its location in the preferences."));
        return;
      }
      List<Path> roots = getRoots(request, settings);
      if (!roots.isEmpty()) {
        search(request, response, settings, listener, monitor, roots);
      }
    } finally {
      response.setElapsedMillis((System.nanoTime() - start) / 1_000_000);
      response.setState(monitor.isCanceled() ? Response.State.CANCELED : Response.State.DONE);
      monitor.done();
      listener.update();
    }
  }

  private static void search(Request request, Response response, RipGrepSettings settings, ProgressListener listener,
      IProgressMonitor monitor, List<Path> roots) {
    List<String> command = RipGrepCommand.build(request, settings, roots.stream().map(Path::toString).toList());
    Process process;
    try {
      process = new ProcessBuilder(command).start();
      process.getOutputStream().close();
    } catch (IOException e) {
      response.getErrors().add(new Error("RipGrep can not be run: " + e.getMessage()));
      return;
    }
    AtomicBoolean finished = new AtomicBoolean();
    Thread errorReader = new Thread(() -> readErrors(process, response), "ERipGrep errors");
    errorReader.setDaemon(true);
    errorReader.start();
    // the search thread waits for the output of RipGrep: another one watches the cancellation
    Thread canceler = new Thread(() -> {
      while (!finished.get()) {
        if (monitor.isCanceled()) {
          process.destroy();
          return;
        }
        try {
          Thread.sleep(50);
        } catch (InterruptedException e) {
          return;
        }
      }
    }, "ERipGrep cancellation");
    canceler.setDaemon(true);
    canceler.start();
    ResponseBuilder builder = new ResponseBuilder(response, settings.maxMatches(), listener);
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
      String line;
      while (!monitor.isCanceled() && !response.isLimitReached() && (line = reader.readLine()) != null) {
        try {
          RipGrepOutputParser.parse(line, builder);
        } catch (IllegalArgumentException e) {
          // not a message of RipGrep, e.g. the output of an extra argument such as --help
          addError(response, line);
        }
      }
    } catch (IOException e) {
      if (!monitor.isCanceled()) {
        Activator.error(e);
      }
    } finally {
      finished.set(true);
      process.destroy();
      try {
        process.waitFor();
        errorReader.join(1000);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }

  private static void readErrors(Process process, Response response) {
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        if (!line.isBlank()) {
          addError(response, line);
        }
      }
    } catch (IOException e) {
      // the process was stopped
    }
  }

  private static void addError(Response response, String message) {
    if (response.getErrors().size() < MAX_ERRORS) {
      response.getErrors().add(new Error(message));
    }
  }

  /**
   * The directories and files given to RipGrep.
   */
  static List<Path> getRoots(Request request, RipGrepSettings settings) {
    IWorkspaceRoot workspaceRoot = ResourcesPlugin.getWorkspace().getRoot();
    List<Path> paths = new ArrayList<>();
    if (request.getScope() == Scope.WORKSPACE) {
      for (IProject project : workspaceRoot.getProjects()) {
        if (project.isOpen() || settings.searchInClosedProjects()) {
          addLocation(paths, project);
        }
      }
    } else {
      for (String scopePath : request.getScopePaths()) {
        IResource resource = workspaceRoot.findMember(scopePath);
        if (resource != null) {
          addLocation(paths, resource);
        }
      }
    }
    return removeNested(paths);
  }

  private static void addLocation(List<Path> paths, IResource resource) {
    IPath location = resource.getLocation();
    if (location != null && location.toFile().exists()) {
      paths.add(location.toFile().toPath());
    }
  }

  /**
   * A project can be in the directory of another one: RipGrep must not search it twice.
   */
  static List<Path> removeNested(List<Path> paths) {
    List<Path> sorted = new ArrayList<>(new LinkedHashSet<>(paths));
    sorted.sort(Comparator.comparingInt(Path::getNameCount));
    List<Path> roots = new ArrayList<>();
    for (Path path : sorted) {
      if (roots.stream().noneMatch(path::startsWith)) {
        roots.add(path);
      }
    }
    return roots;
  }

  /**
   * Builds the response from the output of RipGrep.
   */
  private static class ResponseBuilder implements RipGrepOutputHandler {

    private final Response response;
    private final int maxMatches;
    private final ProgressListener listener;

    /**
     * The projects, the deepest first: a file belongs to the deepest project containing it.
     */
    private final List<Map.Entry<Path, IProject>> projects = new ArrayList<>();
    private final Map<Path, SearchedProject> searchedProjects = new HashMap<>();

    private MatchingFile matchingFile;
    private int matchCount;

    ResponseBuilder(Response response, int maxMatches, ProgressListener listener) {
      this.response = response;
      this.maxMatches = maxMatches;
      this.listener = listener;
      for (IProject project : ResourcesPlugin.getWorkspace().getRoot().getProjects()) {
        IPath location = project.getLocation();
        if (location != null) {
          projects.add(Map.entry(location.toFile().toPath(), project));
        }
      }
      projects.sort(Comparator.comparingInt(entry -> -entry.getKey().getNameCount()));
    }

    @Override
    public void begin(String path) {
      matchingFile = new MatchingFile(getSearchedProject(Path.of(path)), path);
    }

    @Override
    public void line(String path, long lineNumber, String text, List<Span> spans, List<String> replacements) {
      if (matchingFile == null || !matchingFile.getFilePath().equals(path)) {
        begin(path);
      }
      new MatchingLine(matchingFile, lineNumber, text, spans, replacements);
      matchCount += spans.size();
      if (maxMatches > 0 && matchCount >= maxMatches) {
        response.setLimitReached(true);
      }
    }

    @Override
    public void end(String path) {
      matchingFile = null;
      listener.update();
    }

    @Override
    public void summary(long searchedFiles) {
      response.setSearchedFiles(searchedFiles);
    }

    private SearchedProject getSearchedProject(Path file) {
      for (Map.Entry<Path, IProject> entry : projects) {
        if (file.startsWith(entry.getKey())) {
          return searchedProjects.computeIfAbsent(entry.getKey(), location -> new SearchedProject(response, entry.getValue(), location));
        }
      }
      Path root = file.getRoot() != null ? file.getRoot() : file;
      return searchedProjects.computeIfAbsent(root, location -> new SearchedProject(response, null, location));
    }
  }
}
