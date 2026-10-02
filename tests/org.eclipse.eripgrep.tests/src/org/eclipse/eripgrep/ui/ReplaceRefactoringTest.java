package org.eclipse.eripgrep.ui;

import static org.eclipse.eripgrep.TestWorkspace.*;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.function.Predicate;

import org.eclipse.core.resources.*;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.eripgrep.core.RipGrepSettings;
import org.eclipse.eripgrep.model.MatchingLine;
import org.eclipse.eripgrep.model.Request;
import org.eclipse.ltk.core.refactoring.*;
import org.junit.jupiter.api.*;

class ReplaceRefactoringTest {

  private IProject project;

  @BeforeEach
  void createTestProject() throws CoreException {
    deleteProjects();
    project = createProject("replace");
  }

  @AfterEach
  void deleteAll() throws CoreException {
    deleteProjects();
  }

  private static Request request(String text, String replacement) {
    Request request = new Request();
    request.setText(text);
    request.setCaseSensitive(true);
    request.setReplacement(replacement);
    return request;
  }

  private static RefactoringStatus perform(ReplaceRefactoring refactoring) throws CoreException {
    return org.eclipse.eripgrep.TestWorkspace.perform(refactoring).getConditionCheckingStatus();
  }

  private static RefactoringStatus perform(Request request, Predicate<MatchingLine> filter) throws CoreException {
    return perform(new ReplaceRefactoring(request, settings(), filter));
  }

  @Test
  void replacesAllTheMatches() throws CoreException {
    IFile a = createFile(project, "a.txt", "foo and foo\nbar\n  foo\n");
    IFile b = createFile(project, "src/b.txt", "été foo € foo\r\nFoo\r\n");
    ReplaceRefactoring refactoring = new ReplaceRefactoring(request("foo", "quux"), settings(), line -> true);
    assertEquals("Replace \"foo\" with \"quux\"", refactoring.getName());
    RefactoringStatus status = perform(refactoring);
    assertTrue(status.isOK(), status.toString());
    assertEquals(5, refactoring.getReplacementCount());
    assertEquals("quux and quux\nbar\n  quux\n", read(a));
    assertEquals("été quux € quux\r\nFoo\r\n", read(b));
  }

  @Test
  void usesTheGroupsOfARegularExpression() throws CoreException {
    IFile a = createFile(project, "a.txt", "name=John\nname=Jane Doe\n");
    Request request = request("name=(?P<first>\\w+)( \\w+)?", "$first:[$2]");
    request.setRegularExpression(true);
    assertTrue(perform(request, line -> true).isOK());
    assertEquals("John:[]\nJane:[ Doe]\n", read(a));
  }

  @Test
  void replacesTheFilteredLines() throws CoreException {
    IFile a = createFile(project, "a.txt", "foo\nfoo\nfoo\n");
    IFile b = createFile(project, "b.txt", "foo\n");
    assertTrue(perform(request("foo", ""), line -> line.getLineNumber() == 2).isOK());
    assertEquals("foo\n\nfoo\n", read(a));
    assertEquals("foo\n", read(b));
  }

  @Test
  void replacesAMultilineMatch() throws CoreException {
    IFile a = createFile(project, "a.txt", "if (a) {\n}\nend\n");
    assertTrue(perform(request("{\n}", "{ }"), line -> true).isOK());
    assertEquals("if (a) { }\nend\n", read(a));
  }

  @Test
  void keepsAByteOrderMark() throws CoreException {
    IFile a = createFile(project, "a.txt", "﻿foo\nfoo\n".getBytes(StandardCharsets.UTF_8));
    assertTrue(perform(request("foo", "bar"), line -> true).isOK());
    assertEquals("﻿bar\nbar\n", read(a));
  }

  @Test
  void nothingToReplace() throws CoreException {
    IFile a = createFile(project, "a.txt", "foo\n");
    RefactoringStatus status = perform(request("missing", "bar"), line -> true);
    assertTrue(status.hasFatalError());
    assertEquals("Nothing to replace.", status.getEntryWithHighestSeverity().getMessage());
    assertTrue(perform(request("foo", "bar"), line -> false).hasFatalError());
    assertEquals("foo\n", read(a));
  }

  @Test
  void reportsTheErrorOfRipGrep() throws CoreException {
    createFile(project, "a.txt", "foo\n");
    Request request = request("(foo", "bar");
    request.setRegularExpression(true);
    RefactoringStatus status = perform(request, line -> true);
    assertTrue(status.hasFatalError());
    RipGrepSettings missing = new RipGrepSettings(null, 0, 0, java.util.List.of(), true);
    assertTrue(perform(new ReplaceRefactoring(request("foo", "bar"), missing, line -> true)).hasFatalError());
  }

  @Test
  void leavesTheFilesOfAClosedProject() throws CoreException {
    IFile a = createFile(project, "a.txt", "foo\n");
    IProject closed = createProject("closed");
    createFile(closed, "b.txt", "foo\n");
    closed.close(null);
    RefactoringStatus status = perform(request("foo", "bar"), line -> true);
    assertFalse(status.hasFatalError());
    assertTrue(status.hasWarning());
    assertEquals("bar\n", read(a));
    closed.open(null);
    assertEquals("foo\n", read(closed.getFile("b.txt")));
  }

  @Test
  void isUndone() throws CoreException {
    IFile a = createFile(project, "a.txt", "foo\n");
    PerformChangeOperation operation = org.eclipse.eripgrep.TestWorkspace
        .perform(new ReplaceRefactoring(request("foo", "bar"), settings(), line -> true));
    assertEquals("bar\n", read(a));
    Change undo = operation.getUndoChange();
    undo.initializeValidationData(new NullProgressMonitor());
    ResourcesPlugin.getWorkspace().run(new PerformChangeOperation(undo), null);
    assertEquals("foo\n", read(a));
  }
}
