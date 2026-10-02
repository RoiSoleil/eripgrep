package org.eclipse.eripgrep.model;

import java.util.*;

import org.eclipse.eripgrep.core.Json;

public class Request {

  private long time = -1;
  private String text = "";
  private boolean caseSensitive;
  private boolean regularExpression;
  private boolean wholeWord;
  private String fileGlobs = "";
  private boolean searchHidden;
  private boolean useIgnoreFiles = true;
  private int contextLines;
  private Scope scope = Scope.WORKSPACE;
  private List<String> scopePaths = List.of();
  private String replacement;

  public Request() {
  }

  /**
   * A copy of a request, to run it again.
   */
  public Request(Request request) {
    this.text = request.text;
    this.caseSensitive = request.caseSensitive;
    this.regularExpression = request.regularExpression;
    this.wholeWord = request.wholeWord;
    this.fileGlobs = request.fileGlobs;
    this.searchHidden = request.searchHidden;
    this.useIgnoreFiles = request.useIgnoreFiles;
    this.contextLines = request.contextLines;
    this.scope = request.scope;
    this.scopePaths = request.scopePaths;
    this.replacement = request.replacement;
  }

  public long getTime() {
    return time;
  }

  public void setTime(long time) {
    this.time = time;
  }

  public String getText() {
    return text;
  }

  public void setText(String text) {
    this.text = text;
  }

  public boolean isCaseSensitive() {
    return caseSensitive;
  }

  public void setCaseSensitive(boolean caseSensitive) {
    this.caseSensitive = caseSensitive;
  }

  public boolean isRegularExpression() {
    return regularExpression;
  }

  public void setRegularExpression(boolean regularExpression) {
    this.regularExpression = regularExpression;
  }

  public boolean isWholeWord() {
    return wholeWord;
  }

  public void setWholeWord(boolean wholeWord) {
    this.wholeWord = wholeWord;
  }

  public String getFileGlobs() {
    return fileGlobs;
  }

  public void setFileGlobs(String fileGlobs) {
    this.fileGlobs = fileGlobs == null ? "" : fileGlobs;
  }

  public boolean isSearchHidden() {
    return searchHidden;
  }

  public void setSearchHidden(boolean searchHidden) {
    this.searchHidden = searchHidden;
  }

  public boolean isUseIgnoreFiles() {
    return useIgnoreFiles;
  }

  public void setUseIgnoreFiles(boolean useIgnoreFiles) {
    this.useIgnoreFiles = useIgnoreFiles;
  }

  public int getContextLines() {
    return contextLines;
  }

  public void setContextLines(int contextLines) {
    this.contextLines = contextLines;
  }

  public Scope getScope() {
    return scope;
  }

  /**
   * The workspace paths of the resources to search in, for a scope other than the workspace.
   */
  public List<String> getScopePaths() {
    return scopePaths;
  }

  public void setScope(Scope scope, List<String> scopePaths) {
    this.scope = scope;
    this.scopePaths = List.copyOf(scopePaths);
  }

  /**
   * The text replacing each match, <code>null</code> for a plain search.
   */
  public String getReplacement() {
    return replacement;
  }

  public void setReplacement(String replacement) {
    this.replacement = replacement;
  }

  /**
   * Whether running this request gives the results of the other one.
   */
  public boolean isSameSearch(Request other) {
    return other != null && toMap().equals(other.toMap());
  }

  /**
   * The persisted form of the request, see {@link #fromMap(Map)}.
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("text", text);
    map.put("caseSensitive", caseSensitive);
    map.put("regularExpression", regularExpression);
    map.put("wholeWord", wholeWord);
    map.put("fileGlobs", fileGlobs);
    map.put("searchHidden", searchHidden);
    map.put("useIgnoreFiles", useIgnoreFiles);
    map.put("contextLines", (long) contextLines);
    map.put("scope", scope.name());
    map.put("scopePaths", scopePaths);
    return map;
  }

  public static Request fromMap(Map<String, Object> map) {
    Request request = new Request();
    request.text = Json.string(map.get("text"), "");
    request.caseSensitive = Json.bool(map.get("caseSensitive"), false);
    request.regularExpression = Json.bool(map.get("regularExpression"), false);
    request.wholeWord = Json.bool(map.get("wholeWord"), false);
    request.fileGlobs = Json.string(map.get("fileGlobs"), "");
    request.searchHidden = Json.bool(map.get("searchHidden"), false);
    request.useIgnoreFiles = Json.bool(map.get("useIgnoreFiles"), true);
    request.contextLines = (int) Json.number(map.get("contextLines"), 0);
    try {
      request.scope = Scope.valueOf(Json.string(map.get("scope"), Scope.WORKSPACE.name()));
    } catch (IllegalArgumentException e) {
      request.scope = Scope.WORKSPACE;
    }
    request.scopePaths = Json.array(map.get("scopePaths")).stream().map(String::valueOf).toList();
    return request;
  }
}
