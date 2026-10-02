package org.eclipse.eripgrep.core;

import java.util.*;

/**
 * The file filter typed by the user: globs separated by commas, a leading "!" excludes.
 * <p>
 * Example: <code>*.java, .xml, !target/</code>
 */
public final class Globs {

  private Globs() {
  }

  public static List<String> parse(String filter) {
    List<String> globs = new ArrayList<>();
    if (filter == null) {
      return globs;
    }
    for (String token : filter.split(",")) {
      String glob = token.trim();
      boolean excluded = glob.startsWith("!");
      if (excluded) {
        glob = glob.substring(1).trim();
      }
      if (glob.isEmpty()) {
        continue;
      }
      // ".java" is a shortcut for "*.java"
      if (glob.startsWith(".") && glob.indexOf('/') < 0 && glob.indexOf('*') < 0 && glob.lastIndexOf('.') == 0) {
        glob = "*" + glob;
      }
      globs.add(excluded ? "!" + glob : glob);
    }
    return globs;
  }
}
