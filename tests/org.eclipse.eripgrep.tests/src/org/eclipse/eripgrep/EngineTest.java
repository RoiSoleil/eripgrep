package org.eclipse.eripgrep;

import static org.eclipse.eripgrep.TestWorkspace.*;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.eripgrep.core.RipGrepSettings;
import org.eclipse.eripgrep.core.Span;
import org.eclipse.eripgrep.model.*;
import org.junit.jupiter.api.*;

/**
 * Searches in real projects with the RipGrep of the machine.
 */
class EngineTest {

  private IProject project;

  @BeforeEach
  void createProjects() throws CoreException {
    deleteProjects();
    project = createProject("engine");
    createFile(project, "src/a/Hello.java", "class Hello {\n  String hello = \"Hello\";\n  // say hello_world\n}\n");
    createFile(project, "src/b/World.txt", "the world says HELLO\n");
    createFile(project, "notes.md", "nothing here\n");
  }

  @AfterEach
  void deleteAll() throws CoreException {
    deleteProjects();
  }

  private static Request request(String text) {
    Request request = new Request();
    request.setText(text);
    return request;
  }

  private static Response search(Request request) {
    return search(request, settings());
  }

  private static Response search(Request request, RipGrepSettings settings) {
    Response response = new Response();
    Engine.search(request, response, settings, () -> {
    }, new NullProgressMonitor());
    return response;
  }

  /**
   * The results as "file:line:matches", sorted.
   */
  private static List<String> lines(Response response) {
    List<String> lines = new ArrayList<>();
    for (SearchedProject searchedProject : response.getSearchedProjects()) {
      for (MatchingFile matchingFile : searchedProject.getMatchingFiles()) {
        for (MatchingLine matchingLine : matchingFile.getMatchingLines()) {
          List<String> matches = new ArrayList<>();
          for (Span span : matchingLine.getSpans()) {
            matches.add(matchingLine.getLine().substring(span.start(), span.end()));
          }
          lines.add(searchedProject.getName() + "/" + matchingFile.getRelativePath() + ":" + matchingLine.getLineNumber() + ":" + matches);
        }
      }
    }
    Collections.sort(lines);
    return lines;
  }

  @Test
  void searchesIgnoringCase() {
    Response response = search(request("hello"));
    assertEquals(List.of(
        "engine/src/a/Hello.java:1:[Hello]",
        "engine/src/a/Hello.java:2:[hello, Hello]",
        "engine/src/a/Hello.java:3:[hello]",
        "engine/src/b/World.txt:1:[HELLO]"), lines(response));
    assertEquals(Response.State.DONE, response.getState());
    assertEquals(5, response.getMatchCount());
    assertEquals(2, response.getFileCount());
    // the files without match are counted or not, depending on the version of RipGrep
    assertTrue(response.getSearchedFiles() >= 2);
    assertFalse(response.isLimitReached());
    assertTrue(response.getErrors().isEmpty());
    MatchingFile matchingFile = response.getSearchedProjects().peek().getMatchingFiles().stream()
        .filter(file -> file.getFileName().equals("Hello.java")).findFirst().orElseThrow();
    assertEquals(project.getFile("src/a/Hello.java"), matchingFile.getMatchingResource());
    assertEquals("src/a", matchingFile.getRelativeDirectory());
    assertEquals(4, matchingFile.getMatchCount());
    assertEquals(project, matchingFile.getSearchProject().getProject());
  }

  @Test
  void searchesWithCase() {
    Request request = request("Hello");
    request.setCaseSensitive(true);
    assertEquals(List.of("engine/src/a/Hello.java:1:[Hello]", "engine/src/a/Hello.java:2:[Hello]"), lines(search(request)));
  }

  @Test
  void searchesWholeWords() {
    Request request = request("hello");
    request.setCaseSensitive(true);
    request.setWholeWord(true);
    assertEquals(List.of("engine/src/a/Hello.java:2:[hello]"), lines(search(request)));
  }

  @Test
  void searchesARegularExpression() {
    Request request = request("h[a-z]+_w\\w+|HEL+O");
    request.setCaseSensitive(true);
    request.setRegularExpression(true);
    assertEquals(List.of("engine/src/a/Hello.java:3:[hello_world]", "engine/src/b/World.txt:1:[HELLO]"), lines(search(request)));
  }

  @Test
  void aLiteralIsNotARegularExpression() {
    assertEquals(List.of(), lines(search(request("h.llo"))));
  }

  @Test
  void reportsAnInvalidRegularExpression() {
    Request request = request("(hello");
    request.setRegularExpression(true);
    Response response = search(request);
    assertEquals(0, response.getMatchCount());
    assertFalse(response.getErrors().isEmpty());
    assertTrue(response.getErrors().stream().anyMatch(error -> error.getError().contains("regex")), response.getErrors().get(0).getError());
  }

  @Test
  void filtersTheFiles() {
    Request request = request("hello");
    request.setFileGlobs(".txt");
    assertEquals(List.of("engine/src/b/World.txt:1:[HELLO]"), lines(search(request)));
    request.setFileGlobs("!b/");
    assertEquals(3, lines(search(request)).size());
  }

  @Test
  void givesTheContext() {
    Request request = request("world");
    request.setCaseSensitive(true);
    request.setFileGlobs("*.java");
    request.setContextLines(1);
    Response response = search(request);
    assertEquals(List.of("engine/src/a/Hello.java:2:[]", "engine/src/a/Hello.java:3:[world]", "engine/src/a/Hello.java:4:[]"), lines(response));
    assertEquals(1, response.getMatchCount());
    MatchingFile matchingFile = response.getSearchedProjects().peek().getMatchingFiles().get(0);
    assertTrue(matchingFile.getMatchingLines().get(0).isContext());
    assertFalse(matchingFile.getMatchingLines().get(1).isContext());
  }

  @Test
  void searchesOverSeveralLines() {
    Response response = search(request("Hello {\n  String"));
    assertEquals(List.of("engine/src/a/Hello.java:1:[Hello {\n  String]"), lines(response));
    assertEquals(2, response.getSearchedProjects().peek().getMatchingFiles().get(0).getMatchingLines().get(0).getLineCount());
  }

  @Test
  void givesCharacterOffsets() throws CoreException {
    createFile(project, "unicode.txt", "été 😀 € needle é\n");
    Response response = search(request("needle"));
    MatchingLine matchingLine = response.getSearchedProjects().peek().getMatchingFiles().get(0).getMatchingLines().get(0);
    assertEquals(List.of(new Span(9, 15)), matchingLine.getSpans());
    assertEquals("été 😀 € needle é", matchingLine.getLine());
  }

  @Test
  void readsAFileWhichIsNotUtf8() throws CoreException {
    createFile(project, "latin1.txt", "déjà needle vu\n".getBytes(StandardCharsets.ISO_8859_1));
    Request request = request("needle");
    Response response = search(request);
    MatchingLine matchingLine = response.getSearchedProjects().peek().getMatchingFiles().get(0).getMatchingLines().get(0);
    Span span = matchingLine.getSpans().get(0);
    assertEquals("needle", matchingLine.getLine().substring(span.start(), span.end()));
  }

  @Test
  void usesIgnoreFilesAndHiddenFiles() throws CoreException {
    createFile(project, ".ignore", "ignored/\n");
    createFile(project, "ignored/a.txt", "needle\n");
    createFile(project, ".hidden/b.txt", "needle\n");
    createFile(project, ".git/c.txt", "needle\n");
    Request request = request("needle");
    assertEquals(List.of(), lines(search(request)));
    request.setUseIgnoreFiles(false);
    assertEquals(List.of("engine/ignored/a.txt:1:[needle]"), lines(search(request)));
    request.setSearchHidden(true);
    assertEquals(List.of("engine/.hidden/b.txt:1:[needle]", "engine/ignored/a.txt:1:[needle]"), lines(search(request)));
  }

  @Test
  void stopsAtTheLimit() throws CoreException {
    StringBuilder content = new StringBuilder();
    for (int i = 0; i < 5000; i++) {
      content.append("needle ").append(i).append('\n');
    }
    createFile(project, "big.txt", content.toString());
    RipGrepSettings settings = settings();
    Response response = search(request("needle"), new RipGrepSettings(settings.ripGrepPath(), 1, 100, List.of(), true));
    assertTrue(response.isLimitReached());
    assertEquals(100, response.getMatchCount());
    assertEquals(Response.State.DONE, response.getState());
  }

  @Test
  void aFileBelongsToTheDeepestProject() throws CoreException {
    IProject nested = createProject("nested", project.getLocation().append("src/b"));
    Response response = search(request("says"));
    assertEquals(List.of("nested/World.txt:1:[says]"), lines(response));
    SearchedProject searchedProject = response.getSearchedProjects().peek();
    assertEquals(nested, searchedProject.getProject());
    assertEquals(nested.getFile("World.txt"), searchedProject.getMatchingFiles().get(0).getMatchingResource());
    // the nested project is not searched twice
    assertEquals(List.of(project.getLocation().toFile().toPath()), Engine.getRoots(request("says"), settings()));
  }

  @Test
  void searchesInClosedProjects() throws CoreException {
    project.close(null);
    Response response = search(request("says"));
    assertEquals(List.of("engine/src/b/World.txt:1:[says]"), lines(response));
    assertNull(response.getSearchedProjects().peek().getMatchingFiles().get(0).getMatchingResource());
    RipGrepSettings settings = settings();
    RipGrepSettings openedOnly = new RipGrepSettings(settings.ripGrepPath(), 0, 0, List.of(), false);
    assertEquals(List.of(), lines(search(request("says"), openedOnly)));
  }

  @Test
  void searchesInAScope() throws CoreException {
    IProject other = createProject("other");
    IFile file = createFile(other, "c.txt", "hello\n");
    Request request = request("hello");
    request.setScope(Scope.SELECTION, List.of("/engine/src/b", file.getFullPath().toString(), "/missing"));
    assertEquals(List.of("engine/src/b/World.txt:1:[HELLO]", "other/c.txt:1:[hello]"), lines(search(request)));
    request.setScope(Scope.PROJECT, List.of("/other"));
    assertEquals(List.of("other/c.txt:1:[hello]"), lines(search(request)));
    request.setScope(Scope.SELECTION, List.of());
    assertEquals(List.of(), lines(search(request)));
  }

  @Test
  void removesNestedPaths() {
    assertEquals(List.of(Path.of("/a"), Path.of("/ab"), Path.of("/b/c")),
        Engine.removeNested(List.of(Path.of("/a/b"), Path.of("/b/c"), Path.of("/a"), Path.of("/ab"), Path.of("/a"), Path.of("/b/c/d/e"))));
  }

  @Test
  void passesTheAdditionalArguments() {
    RipGrepSettings settings = settings();
    Request request = request("hello");
    Response response = search(request, new RipGrepSettings(settings.ripGrepPath(), 0, 0, List.of("--max-count", "1"), true));
    assertEquals(List.of("engine/src/a/Hello.java:1:[Hello]", "engine/src/b/World.txt:1:[HELLO]"), lines(response));
    // an argument which makes RipGrep write something else than its results
    response = search(request, new RipGrepSettings(settings.ripGrepPath(), 0, 0, List.of("--version"), true));
    assertEquals(0, response.getMatchCount());
    assertTrue(response.getErrors().get(0).getError().startsWith("ripgrep "));
  }

  @Test
  void reportsAMissingRipGrep() {
    Response response = search(request("hello"), new RipGrepSettings(null, 0, 0, List.of(), true));
    assertTrue(response.isRipGrepMissing());
    assertEquals(1, response.getErrors().size());
    assertEquals(Response.State.DONE, response.getState());
  }

  @Test
  void reportsABinaryWhichCanNotBeRun() {
    Response response = search(request("hello"), new RipGrepSettings(project.getLocation().append("notes.md").toOSString(), 0, 0, List.of(), true));
    assertFalse(response.isRipGrepMissing());
    assertTrue(response.getErrors().get(0).getError().startsWith("RipGrep can not be run"));
  }

  @Test
  void isCanceled() {
    NullProgressMonitor monitor = new NullProgressMonitor();
    monitor.setCanceled(true);
    Response response = new Response();
    AtomicInteger updates = new AtomicInteger();
    Engine.search(request("hello"), response, settings(), updates::incrementAndGet, monitor);
    assertEquals(Response.State.CANCELED, response.getState());
    assertEquals(0, response.getMatchCount());
    assertEquals(1, updates.get());
  }

  @Test
  void notifiesTheListener() {
    Response response = new Response();
    AtomicInteger updates = new AtomicInteger();
    Engine.search(request("hello"), response, settings(), updates::incrementAndGet, new NullProgressMonitor());
    // once for each file and once at the end
    assertEquals(3, updates.get());
  }

  @Test
  void anEmptyWorkspaceHasNoResult() throws CoreException {
    deleteProjects();
    Response response = search(request("hello"));
    assertEquals(0, response.getFileCount());
    assertEquals(-1, response.getSearchedFiles());
    assertTrue(response.getErrors().isEmpty());
  }
}
