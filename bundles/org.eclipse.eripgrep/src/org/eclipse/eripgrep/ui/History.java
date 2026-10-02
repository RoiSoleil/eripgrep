package org.eclipse.eripgrep.ui;

import static org.eclipse.eripgrep.utils.PreferenceConstantes.HISTORY;

import java.util.*;

import org.eclipse.eripgrep.core.Json;
import org.eclipse.eripgrep.model.*;
import org.eclipse.eripgrep.utils.Utils;

/**
 * The last searches, the most recent last. The requests are kept between sessions, not their results.
 */
public class History {

  public static final int MAX_SIZE = 25;

  private final LinkedHashMap<Request, Response> searches = new LinkedHashMap<>();

  public static History load() {
    History history = new History();
    history.read(Utils.getPreferences().get(HISTORY, ""));
    return history;
  }

  void read(String value) {
    searches.clear();
    if (value.startsWith("[")) {
      try {
        Json.array(Json.parse(value)).forEach(request -> searches.put(Request.fromMap(Json.object(request)), null));
        return;
      } catch (IllegalArgumentException e) {
        // read as the old format
      }
    }
    // the format of the version 1: the texts, the most recent first
    List<String> texts = new ArrayList<>(Arrays.asList(value.split("\\|")));
    Collections.reverse(texts);
    for (String text : texts) {
      if (!text.isBlank()) {
        Request request = new Request();
        request.setText(text);
        request.setCaseSensitive(true);
        searches.put(request, null);
      }
    }
  }

  String write() {
    return Json.write(searches.keySet().stream().map(Request::toMap).toList());
  }

  public void save() {
    Utils.getPreferences().put(HISTORY, write());
    Utils.savePreferences();
  }

  /**
   * Adds a search, in place of the same search if it was already done.
   */
  public void add(Request request, Response response) {
    searches.keySet().removeIf(request::isSameSearch);
    searches.put(request, response);
    Iterator<Request> iterator = searches.keySet().iterator();
    while (searches.size() > MAX_SIZE) {
      iterator.next();
      iterator.remove();
    }
  }

  public void remove(Request request) {
    searches.remove(request);
  }

  public void clear() {
    searches.clear();
  }

  public boolean isEmpty() {
    return searches.isEmpty();
  }

  /**
   * The searches, the most recent first; the response is <code>null</code> for a search of a previous session.
   */
  public List<Map.Entry<Request, Response>> getSearches() {
    List<Map.Entry<Request, Response>> entries = new ArrayList<>(searches.entrySet());
    Collections.reverse(entries);
    return entries;
  }
}
