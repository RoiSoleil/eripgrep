package org.eclipse.eripgrep.core;

import java.util.*;

import org.eclipse.eripgrep.model.Request;

/**
 * Builds the command line of RipGrep.
 */
public final class RipGrepCommand {

  private RipGrepCommand() {
  }

  public static List<String> build(Request request, RipGrepSettings settings, List<String> paths) {
    List<String> command = new ArrayList<>();
    command.add(settings.ripGrepPath());
    command.add("--json");
    // always explicit: the configuration file of the user may change the default
    command.add(request.isCaseSensitive() ? "--case-sensitive" : "--ignore-case");
    if (!request.isRegularExpression()) {
      command.add("--fixed-strings");
    }
    if (request.isWholeWord()) {
      command.add("--word-regexp");
    }
    if (isMultiline(request)) {
      command.add("--multiline");
    }
    if (request.isSearchHidden()) {
      command.add("--hidden");
      command.add("--glob");
      command.add("!.git/");
    }
    if (!request.isUseIgnoreFiles()) {
      command.add("--no-ignore");
    }
    if (request.getContextLines() > 0) {
      command.add("--context");
      command.add(String.valueOf(request.getContextLines()));
    }
    if (settings.threads() > 0) {
      command.add("--threads");
      command.add(String.valueOf(settings.threads()));
    }
    for (String glob : Globs.parse(request.getFileGlobs())) {
      command.add("--glob");
      command.add(glob);
    }
    if (request.getReplacement() != null) {
      command.add("--replace");
      command.add(request.getReplacement());
    }
    command.addAll(settings.extraArguments());
    command.add("--regexp");
    command.add(request.getText());
    command.add("--");
    command.addAll(paths);
    return command;
  }

  /**
   * RipGrep only matches over several lines when asked to.
   */
  private static boolean isMultiline(Request request) {
    String text = request.getText();
    return text.indexOf('\n') >= 0 || request.isRegularExpression() && (text.contains("\\n") || text.contains("(?s"));
  }

  /**
   * Splits arguments separated by spaces; an argument with spaces is quoted.
   */
  public static List<String> splitArguments(String arguments) {
    List<String> result = new ArrayList<>();
    if (arguments == null) {
      return result;
    }
    StringBuilder current = new StringBuilder();
    boolean started = false;
    char quote = 0;
    for (int i = 0; i < arguments.length(); i++) {
      char c = arguments.charAt(i);
      if (quote != 0) {
        if (c == quote) {
          quote = 0;
        } else {
          current.append(c);
        }
      } else if (c == '"' || c == '\'') {
        quote = c;
        started = true;
      } else if (Character.isWhitespace(c)) {
        if (started) {
          result.add(current.toString());
          current.setLength(0);
          started = false;
        }
      } else {
        current.append(c);
        started = true;
      }
    }
    if (started) {
      result.add(current.toString());
    }
    return result;
  }
}
