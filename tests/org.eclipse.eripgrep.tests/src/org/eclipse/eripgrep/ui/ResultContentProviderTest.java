package org.eclipse.eripgrep.ui;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.*;

import org.eclipse.eripgrep.core.Span;
import org.eclipse.eripgrep.model.*;
import org.eclipse.eripgrep.model.Error;
import org.eclipse.eripgrep.ui.model.Folder;
import org.eclipse.eripgrep.ui.model.SeeAll;
import org.eclipse.jface.viewers.TreePath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ResultContentProviderTest {

  private static final Path LOCATION = Path.of(System.getProperty("java.io.tmpdir"), "eripgrep-project").toAbsolutePath();

  private final ResultContentProvider provider = new ResultContentProvider();
  private Response response;
  private SearchedProject project;

  @BeforeEach
  void createResponse() {
    response = new Response();
    project = new SearchedProject(response, null, LOCATION);
  }

  private MatchingFile file(String relativePath, int... lineNumbers) {
    MatchingFile matchingFile = new MatchingFile(project, LOCATION.resolve(relativePath).toString());
    for (int lineNumber : lineNumbers) {
      new MatchingLine(matchingFile, lineNumber, "match", List.of(new Span(0, 5)), List.of());
    }
    return matchingFile;
  }

  private List<String> labels(Object parent) {
    List<String> labels = new ArrayList<>();
    for (Object child : provider.getChildren(parent)) {
      labels.add(label(child));
    }
    return labels;
  }

  private static String label(Object element) {
    if (element instanceof Folder folder) {
      return folder.getName() + "/";
    } else if (element instanceof MatchingFile matchingFile) {
      return matchingFile.getFileName();
    } else if (element instanceof MatchingLine matchingLine) {
      return String.valueOf(matchingLine.getLineNumber());
    } else if (element instanceof SearchedProject searchedProject) {
      return searchedProject.getName();
    } else if (element instanceof Error error) {
      return error.getError();
    }
    return element.getClass().getSimpleName();
  }

  private Object child(Object parent, String label) {
    return Arrays.stream(provider.getChildren(parent)).filter(child -> label(child).equals(label)).findFirst().orElseThrow();
  }

  @Test
  void showsProjectsFilesAndLines() {
    MatchingFile b = file("src/b.txt", 3, 1);
    file("a.txt", 7);
    response.getErrors().add(new Error("an error"));
    assertEquals(List.of("an error", LOCATION.toString()), labels(response));
    assertArrayEquals(provider.getChildren(response), provider.getElements(response));
    assertEquals(List.of("b.txt", "a.txt"), labels(project));
    assertEquals(List.of("3", "1"), labels(b));
    assertTrue(provider.hasChildren(project));
    assertTrue(provider.hasChildren(b));
    assertFalse(provider.hasChildren(b.getMatchingLines().get(0)));
    assertFalse(provider.hasChildren(response.getErrors().get(0)));
    assertSame(b, provider.getParent(b.getMatchingLines().get(0)));
    assertSame(response, provider.getParent(project));
    assertNull(provider.getParent(b));
    assertEquals(0, provider.getChildren("unknown").length);
  }

  @Test
  void sortsAlphabetically() {
    file("src/b.txt", 1);
    file("a.txt", 1);
    file("src/a.txt", 1);
    SearchedProject first = new SearchedProject(response, null, Path.of(LOCATION.getParent().toString(), "a-first"));
    provider.setAlphabeticalSort(true);
    assertTrue(provider.isAlphabeticalSort());
    assertEquals(List.of(first.getName(), project.getName()), labels(response));
    assertEquals(List.of("a.txt", "a.txt", "b.txt"), labels(project));
    assertEquals("a.txt", ((MatchingFile) provider.getChildren(project)[0]).getRelativePath());
  }

  @Test
  void groupsByFolder() {
    file("src/org/eclipse/b/B.java", 1);
    file("src/org/eclipse/A.java", 1);
    file("root.txt", 1);
    file("doc/readme.md", 1);
    file("src/org/eclipse/b/c/d/D.java", 1);
    provider.setGroupByFolder(true);
    assertTrue(provider.isGroupByFolder());
    // the folders first, a chain of folders is a single node
    assertEquals(List.of("doc/", "src/org/eclipse/", "root.txt"), labels(project));
    Object eclipse = child(project, "src/org/eclipse/");
    assertEquals(List.of("b/", "A.java"), labels(eclipse));
    Object b = child(eclipse, "b/");
    assertEquals(List.of("c/d/", "B.java"), labels(b));
    assertEquals(List.of("D.java"), labels(child(b, "c/d/")));
    assertEquals(new Folder(project, "src/org/eclipse", "src/org/eclipse/b"), b);
    assertTrue(((Folder) b).contains(project.getMatchingFiles().get(4)));
    assertFalse(((Folder) b).contains(project.getMatchingFiles().get(1)));
  }

  @Test
  void aFolderIsNotAPrefixOfAnotherName() {
    file("src/A.java", 1);
    file("src2/B.java", 1);
    provider.setGroupByFolder(true);
    assertEquals(List.of("src/", "src2/"), labels(project));
    assertEquals(List.of("A.java"), labels(child(project, "src/")));
  }

  @Test
  void shortensLongLists() {
    int[] lineNumbers = new int[SeeAll.MAX_NUMBER + 10];
    for (int i = 0; i < lineNumbers.length; i++) {
      lineNumbers[i] = i + 1;
    }
    MatchingFile matchingFile = file("a.txt", lineNumbers);
    Object[] children = provider.getChildren(matchingFile);
    assertEquals(SeeAll.MAX_NUMBER + 1, children.length);
    SeeAll seeAll = (SeeAll) children[SeeAll.MAX_NUMBER];
    assertEquals(10, provider.getRemainingCount(seeAll));
    assertEquals(new SeeAll(matchingFile), seeAll);
    assertEquals(new SeeAll(matchingFile).hashCode(), seeAll.hashCode());
    assertNotEquals(new SeeAll(project), seeAll);
    assertSame(matchingFile, provider.getParent(seeAll));
    assertTrue(provider.hasChildren(seeAll));
    List<String> remaining = labels(seeAll);
    assertEquals(10, remaining.size());
    assertEquals(String.valueOf(SeeAll.MAX_NUMBER + 1), remaining.get(0));
    // exactly the maximum: no node
    MatchingFile other = file("b.txt", Arrays.copyOf(lineNumbers, SeeAll.MAX_NUMBER));
    assertEquals(SeeAll.MAX_NUMBER, provider.getChildren(other).length);
    assertEquals(0, provider.getRemainingCount(new SeeAll(other)));
  }

  @Test
  void listsTheMatchingLines() {
    MatchingFile b = file("src/b.txt", 3);
    new MatchingLine(b, 4, "context", List.of(), List.of());
    MatchingFile a = file("a.txt", 7, 9);
    provider.setGroupByFolder(true);
    List<TreePath> paths = provider.getMatchingLinePaths(response);
    assertEquals(3, paths.size());
    assertEquals(4, paths.get(0).getSegmentCount());
    assertSame(project, paths.get(0).getFirstSegment());
    assertEquals(new Folder(project, "", "src"), paths.get(0).getSegment(1));
    assertSame(b.getMatchingLines().get(0), paths.get(0).getLastSegment());
    assertSame(a.getMatchingLines().get(0), paths.get(1).getLastSegment());
    assertSame(a.getMatchingLines().get(1), paths.get(2).getLastSegment());
    assertEquals(List.of(), provider.getMatchingLinePaths(null));
  }
}
