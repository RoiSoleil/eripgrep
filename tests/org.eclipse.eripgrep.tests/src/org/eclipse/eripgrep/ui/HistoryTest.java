package org.eclipse.eripgrep.ui;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.eclipse.eripgrep.model.Request;
import org.eclipse.eripgrep.model.Response;
import org.junit.jupiter.api.Test;

class HistoryTest {

  private static Request request(String text) {
    Request request = new Request();
    request.setText(text);
    return request;
  }

  private static List<String> texts(History history) {
    return history.getSearches().stream().map(search -> search.getKey().getText()).toList();
  }

  @Test
  void theMostRecentSearchIsFirst() {
    History history = new History();
    assertTrue(history.isEmpty());
    Response response = new Response();
    history.add(request("a"), null);
    history.add(request("b"), response);
    assertEquals(List.of("b", "a"), texts(history));
    assertSame(response, history.getSearches().get(0).getValue());
    assertNull(history.getSearches().get(1).getValue());
  }

  @Test
  void aSearchDoneAgainMovesFirst() {
    History history = new History();
    history.add(request("a"), null);
    history.add(request("b"), null);
    Request again = request("a");
    history.add(again, null);
    assertEquals(List.of("a", "b"), texts(history));
    assertSame(again, history.getSearches().get(0).getKey());
    Request caseSensitive = request("a");
    caseSensitive.setCaseSensitive(true);
    history.add(caseSensitive, null);
    assertEquals(List.of("a", "a", "b"), texts(history));
  }

  @Test
  void keepsTheLastSearches() {
    History history = new History();
    for (int i = 0; i < History.MAX_SIZE + 5; i++) {
      history.add(request("search " + i), null);
    }
    assertEquals(History.MAX_SIZE, history.getSearches().size());
    assertEquals("search " + (History.MAX_SIZE + 4), texts(history).get(0));
    assertEquals("search 5", texts(history).get(History.MAX_SIZE - 1));
  }

  @Test
  void removesSearches() {
    History history = new History();
    Request request = request("a");
    history.add(request, null);
    history.add(request("b"), null);
    history.remove(request);
    assertEquals(List.of("b"), texts(history));
    history.clear();
    assertTrue(history.isEmpty());
  }

  @Test
  void isPersisted() {
    History history = new History();
    Request request = request("a|b \"c\"");
    request.setRegularExpression(true);
    request.setFileGlobs("*.java");
    history.add(request, new Response());
    history.add(request("d"), null);
    History read = new History();
    read.read(history.write());
    assertEquals(List.of("d", "a|b \"c\""), texts(read));
    Request readRequest = read.getSearches().get(1).getKey();
    assertTrue(readRequest.isSameSearch(request));
    assertNull(read.getSearches().get(1).getValue());
  }

  @Test
  void readsTheHistoryOfTheVersion1() {
    History history = new History();
    history.read("last|[previous]|| |first");
    assertEquals(List.of("last", "[previous]", "first"), texts(history));
    assertTrue(history.getSearches().get(0).getKey().isCaseSensitive());
    history.read("[not json|other");
    assertEquals(List.of("[not json", "other"), texts(history));
    history.read("");
    assertTrue(history.isEmpty());
  }
}
