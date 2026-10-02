package org.eclipse.eripgrep.ui;

import java.util.*;
import java.util.function.Predicate;

import org.eclipse.core.filebuffers.*;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.*;
import org.eclipse.eripgrep.Engine;
import org.eclipse.eripgrep.core.*;
import org.eclipse.eripgrep.model.*;
import org.eclipse.ltk.core.refactoring.*;
import org.eclipse.text.edits.*;

/**
 * Replaces the matches of a search. RipGrep computes the replacements, so that the references to the groups of a
 * regular expression have its syntax.
 */
public class ReplaceRefactoring extends Refactoring {

  private final Request request;
  private final RipGrepSettings settings;
  private final Predicate<MatchingLine> filter;

  private final List<Change> changes = new ArrayList<>();
  private int replacementCount;

  /**
   * @param request the search, with its replacement
   * @param filter  the lines to replace among the matching ones
   */
  public ReplaceRefactoring(Request request, RipGrepSettings settings, Predicate<MatchingLine> filter) {
    this.request = request;
    // all the matches are replaced, even after the limit of the view
    this.settings = new RipGrepSettings(settings.ripGrepPath(), settings.threads(), 0, settings.extraArguments(),
        settings.searchInClosedProjects());
    this.filter = filter;
  }

  @Override
  public String getName() {
    return "Replace \"" + request.getText() + "\" with \"" + request.getReplacement() + "\"";
  }

  public int getReplacementCount() {
    return replacementCount;
  }

  @Override
  public RefactoringStatus checkInitialConditions(IProgressMonitor pm) {
    return new RefactoringStatus();
  }

  @Override
  public RefactoringStatus checkFinalConditions(IProgressMonitor pm) throws CoreException {
    RefactoringStatus status = new RefactoringStatus();
    changes.clear();
    replacementCount = 0;
    Response response = new Response();
    Engine.search(request, response, settings, () -> {
    }, pm);
    if (pm.isCanceled()) {
      throw new OperationCanceledException();
    }
    if (!response.getErrors().isEmpty()) {
      status.addFatalError(response.getErrors().get(0).getError());
      return status;
    }
    int outsideWorkspace = 0;
    int modified = 0;
    for (SearchedProject searchedProject : response.getSearchedProjects()) {
      for (MatchingFile matchingFile : List.copyOf(searchedProject.getMatchingFiles())) {
        List<MatchingLine> matchingLines = List.copyOf(matchingFile.getMatchingLines()).stream()
            .filter(matchingLine -> !matchingLine.isContext() && filter.test(matchingLine)).toList();
        if (matchingLines.isEmpty()) {
          continue;
        }
        if (!(matchingFile.getMatchingResource() instanceof IFile file)) {
          outsideWorkspace++;
          continue;
        }
        modified += addChange(file, matchingLines);
      }
    }
    if (outsideWorkspace > 0) {
      status.addWarning(outsideWorkspace + " file(s) are not in an opened project: they are left unchanged.");
    }
    if (modified > 0) {
      status.addWarning(modified + " match(es) are left unchanged: the file changed since RipGrep read it.");
    }
    if (changes.isEmpty()) {
      status.addFatalError("Nothing to replace.");
    }
    return status;
  }

  /**
   * @return the number of matches which can not be replaced
   */
  private int addChange(IFile file, List<MatchingLine> matchingLines) throws CoreException {
    String content = getContent(file);
    MultiTextEdit edit = new MultiTextEdit();
    int skipped = 0;
    for (MatchingLine matchingLine : matchingLines) {
      int lineOffset = TextOffsets.lineOffset(content, matchingLine.getLineNumber());
      for (int i = 0; i < matchingLine.getSpans().size(); i++) {
        Span span = matchingLine.getSpans().get(i);
        if (matchingLine.getReplacements().size() <= i || lineOffset < 0
            || !content.regionMatches(lineOffset + span.start(), matchingLine.getLine(), span.start(), span.length())) {
          skipped++;
          continue;
        }
        edit.addChild(new ReplaceEdit(lineOffset + span.start(), span.length(), matchingLine.getReplacements().get(i)));
        replacementCount++;
      }
    }
    if (edit.hasChildren()) {
      TextFileChange change = new TextFileChange(file.getFullPath().makeRelative().toString(), file);
      change.setEdit(edit);
      change.setTextType(file.getFileExtension() != null ? file.getFileExtension() : "txt");
      changes.add(change);
    }
    return skipped;
  }

  /**
   * The text the change is applied on: the one of the editor if the file is edited.
   */
  private static String getContent(IFile file) throws CoreException {
    ITextFileBufferManager manager = FileBuffers.getTextFileBufferManager();
    IPath path = file.getFullPath();
    manager.connect(path, LocationKind.IFILE, null);
    try {
      return manager.getTextFileBuffer(path, LocationKind.IFILE).getDocument().get();
    } finally {
      manager.disconnect(path, LocationKind.IFILE, null);
    }
  }

  @Override
  public Change createChange(IProgressMonitor pm) {
    return new CompositeChange(getName(), changes.toArray(Change[]::new));
  }
}
