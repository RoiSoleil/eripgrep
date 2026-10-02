package org.eclipse.eripgrep.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

class GlobsTest {

  @Test
  void splitsOnCommas() {
    assertEquals(List.of("*.java", "src/**/*.xml"), Globs.parse(" *.java , src/**/*.xml,, "));
  }

  @Test
  void keepsExclusions() {
    assertEquals(List.of("!target/", "!*.class"), Globs.parse("!target/, ! *.class, !"));
  }

  @Test
  void extensionIsAShortcut() {
    assertEquals(List.of("*.java", "!*.md", ".settings/", "*.tar.gz", ".git*"), Globs.parse(".java, !.md, .settings/, *.tar.gz, .git*"));
  }

  @Test
  void emptyFilter() {
    assertEquals(List.of(), Globs.parse(null));
    assertEquals(List.of(), Globs.parse("  "));
  }
}
