package org.eclipse.eripgrep.core;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.eclipse.eripgrep.model.Request;
import org.junit.jupiter.api.Test;

class RipGrepCommandTest {

  private static final RipGrepSettings SETTINGS = new RipGrepSettings("rg", 0, 0, List.of(), true);

  private static Request request(String text) {
    Request request = new Request();
    request.setText(text);
    return request;
  }

  @Test
  void literalSearch() {
    assertEquals(List.of("rg", "--json", "--ignore-case", "--fixed-strings", "--regexp", "-foo", "--", "/a", "/b"),
        RipGrepCommand.build(request("-foo"), SETTINGS, List.of("/a", "/b")));
  }

  @Test
  void allOptions() {
    Request request = request("fo+");
    request.setCaseSensitive(true);
    request.setRegularExpression(true);
    request.setWholeWord(true);
    request.setSearchHidden(true);
    request.setUseIgnoreFiles(false);
    request.setContextLines(2);
    request.setFileGlobs(".java, !target/");
    request.setReplacement("bar");
    RipGrepSettings settings = new RipGrepSettings("/bin/rg", 4, 100, List.of("--follow"), true);
    assertEquals(List.of("/bin/rg", "--json", "--case-sensitive", "--word-regexp", "--hidden", "--glob", "!.git/", "--no-ignore",
        "--context", "2", "--threads", "4", "--glob", "*.java", "--glob", "!target/", "--replace", "bar", "--follow",
        "--regexp", "fo+", "--", "/a"), RipGrepCommand.build(request, settings, List.of("/a")));
  }

  @Test
  void multilineIsDetected() {
    assertTrue(RipGrepCommand.build(request("a\nb"), SETTINGS, List.of()).contains("--multiline"));
    assertFalse(RipGrepCommand.build(request("a\\nb"), SETTINGS, List.of()).contains("--multiline"));
    Request regularExpression = request("a\\nb");
    regularExpression.setRegularExpression(true);
    assertTrue(RipGrepCommand.build(regularExpression, SETTINGS, List.of()).contains("--multiline"));
    regularExpression.setText("(?s)a.b");
    assertTrue(RipGrepCommand.build(regularExpression, SETTINGS, List.of()).contains("--multiline"));
    regularExpression.setText("a.b");
    assertFalse(RipGrepCommand.build(regularExpression, SETTINGS, List.of()).contains("--multiline"));
  }

  @Test
  void splitsArguments() {
    assertEquals(List.of(), RipGrepCommand.splitArguments(null));
    assertEquals(List.of(), RipGrepCommand.splitArguments("  "));
    assertEquals(List.of("--follow", "-g", "a b", "it's", ""),
        RipGrepCommand.splitArguments(" --follow  -g 'a b'\t\"it's\" ''"));
    assertEquals(List.of("--max-filesize=1M"), RipGrepCommand.splitArguments("--max-file\"size\"=1M"));
  }
}
