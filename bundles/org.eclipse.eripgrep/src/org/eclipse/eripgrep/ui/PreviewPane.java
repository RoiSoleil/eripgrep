package org.eclipse.eripgrep.ui;

import java.io.IOException;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.eripgrep.core.Preview;
import org.eclipse.eripgrep.core.Span;
import org.eclipse.eripgrep.model.MatchingFile;
import org.eclipse.eripgrep.model.MatchingLine;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.widgets.Composite;

/**
 * Shows the lines of the file around the selected match.
 */
public class PreviewPane {

  private static final int LINES_AROUND = 6;
  private static final long MAX_FILE_SIZE = 5_000_000;

  private final StyledText styledText;

  public PreviewPane(Composite parent) {
    styledText = new StyledText(parent, SWT.H_SCROLL | SWT.V_SCROLL | SWT.READ_ONLY | SWT.BORDER);
    styledText.setFont(JFaceResources.getTextFont());
    styledText.setMargins(4, 2, 4, 2);
  }

  public StyledText getControl() {
    return styledText;
  }

  public void show(MatchingLine matchingLine) {
    if (matchingLine == null) {
      styledText.setText("");
      return;
    }
    MatchingFile matchingFile = matchingLine.getMatchingFile();
    Map<Long, List<Span>> matches = new HashMap<>();
    for (MatchingLine line : List.copyOf(matchingFile.getMatchingLines())) {
      matches.put(line.getLineNumber(), line.getSpans());
    }
    List<String> lines = readLines(matchingFile);
    Preview preview;
    if (lines != null && lines.size() >= matchingLine.getLineNumber() + matchingLine.getLineCount() - 1) {
      preview = Preview.of(lines, matchingLine.getLineNumber(), matchingLine.getLineCount(), LINES_AROUND, matches);
    } else {
      // the file can not be read or it changed: only the line read by RipGrep is shown
      List<String> matchingLines = Arrays.asList(matchingLine.getLine().split("\r?\n", -1));
      preview = Preview.of(matchingLines, 1, matchingLines.size(), 0, Map.of(1L, matchingLine.getSpans()));
    }
    styledText.setText(preview.text());
    Color highlight = JFaceResources.getColorRegistry().get(ResultLabelProvider.HIGHLIGHT_COLOR);
    Color gray = blend(styledText.getForeground(), styledText.getBackground(), 0.5f);
    Color currentLine = blend(styledText.getForeground(), styledText.getBackground(), 0.9f);
    int currentLineOffset = 0;
    for (Preview.Range range : preview.ranges()) {
      switch (range.kind()) {
      case LINE_NUMBER -> styledText.setStyleRange(new StyleRange(range.start(), range.length(), gray, null));
      case MATCH -> styledText.setStyleRange(new StyleRange(range.start(), range.length(), ResultLabelProvider.getReadableColor(highlight), highlight));
      case CURRENT_LINE -> {
        if (currentLineOffset == 0) {
          currentLineOffset = range.start();
        }
        int line = styledText.getLineAtOffset(range.start());
        styledText.setLineBackground(line, 1, currentLine);
      }
      }
    }
    // centers the selected line
    int line = styledText.getLineAtOffset(currentLineOffset);
    int visibleLines = Math.max(1, styledText.getClientArea().height / Math.max(1, styledText.getLineHeight()));
    styledText.setTopIndex(Math.max(0, line - visibleLines / 2));
  }

  private static Color blend(Color color, Color other, float ratio) {
    return new Color(Math.round(color.getRed() + (other.getRed() - color.getRed()) * ratio),
        Math.round(color.getGreen() + (other.getGreen() - color.getGreen()) * ratio),
        Math.round(color.getBlue() + (other.getBlue() - color.getBlue()) * ratio));
  }

  private static List<String> readLines(MatchingFile matchingFile) {
    try {
      Path path = Path.of(matchingFile.getFilePath());
      if (Files.size(path) > MAX_FILE_SIZE) {
        return null;
      }
      byte[] bytes = Files.readAllBytes(path);
      String content = null;
      if (matchingFile.getMatchingResource() instanceof IFile file) {
        try {
          content = new String(bytes, Charset.forName(file.getCharset()));
        } catch (CoreException | IllegalArgumentException e) {
          // decoded as below
        }
      }
      if (content == null) {
        try {
          content = StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
          content = new String(bytes, StandardCharsets.ISO_8859_1);
        }
      }
      if (content.startsWith("﻿")) {
        content = content.substring(1);
      }
      if (content.endsWith("\n")) {
        content = content.substring(0, content.length() - 1);
      }
      List<String> lines = new ArrayList<>();
      for (String line : content.split("\n", -1)) {
        lines.add(line.endsWith("\r") ? line.substring(0, line.length() - 1) : line);
      }
      return lines;
    } catch (IOException | InvalidPathException e) {
      return null;
    }
  }
}
