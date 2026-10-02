package org.eclipse.eripgrep.model;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.eclipse.eripgrep.core.Json;
import org.junit.jupiter.api.Test;

class RequestTest {

  private static Request request() {
    Request request = new Request();
    request.setText("a \"text\"\n|");
    request.setCaseSensitive(true);
    request.setRegularExpression(true);
    request.setWholeWord(true);
    request.setFileGlobs("*.java");
    request.setSearchHidden(true);
    request.setUseIgnoreFiles(false);
    request.setContextLines(3);
    request.setScope(Scope.SELECTION, List.of("/project/src"));
    return request;
  }

  @Test
  void isPersisted() {
    Request request = request();
    Request read = Request.fromMap(Json.object(Json.parse(Json.write(request.toMap()))));
    assertTrue(read.isSameSearch(request));
    assertEquals("a \"text\"\n|", read.getText());
    assertTrue(read.isCaseSensitive());
    assertTrue(read.isRegularExpression());
    assertTrue(read.isWholeWord());
    assertEquals("*.java", read.getFileGlobs());
    assertTrue(read.isSearchHidden());
    assertFalse(read.isUseIgnoreFiles());
    assertEquals(3, read.getContextLines());
    assertEquals(Scope.SELECTION, read.getScope());
    assertEquals(List.of("/project/src"), read.getScopePaths());
    assertEquals(-1, read.getTime());
  }

  @Test
  void hasDefaults() {
    Request request = Request.fromMap(Map.of("text", "foo", "scope", "MOON"));
    assertEquals("foo", request.getText());
    assertFalse(request.isCaseSensitive());
    assertTrue(request.isUseIgnoreFiles());
    assertEquals(Scope.WORKSPACE, request.getScope());
    assertEquals(List.of(), request.getScopePaths());
    assertNull(request.getReplacement());
  }

  @Test
  void isCopied() {
    Request request = request();
    request.setTime(12);
    request.setReplacement("b");
    Request copy = new Request(request);
    assertTrue(copy.isSameSearch(request));
    assertEquals("b", copy.getReplacement());
    assertEquals(-1, copy.getTime());
    copy.setText("other");
    assertFalse(copy.isSameSearch(request));
    assertFalse(copy.isSameSearch(null));
    copy.setFileGlobs(null);
    assertEquals("", copy.getFileGlobs());
  }
}
