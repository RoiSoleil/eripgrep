package org.eclipse.eripgrep.core;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;

/**
 * Finds the RipGrep binary.
 */
public final class RipGrep {

  private RipGrep() {
  }

  /**
   * Returns the configured binary or, if none is configured, the one found on the PATH.
   *
   * @return <code>null</code> if RipGrep is not found
   */
  public static String locate(String configuredPath) {
    if (configuredPath != null && !configuredPath.isBlank()) {
      return Files.isRegularFile(Path.of(configuredPath.trim())) ? configuredPath.trim() : null;
    }
    return findOnPath(System.getenv("PATH"));
  }

  static String findOnPath(String path) {
    if (path == null) {
      return null;
    }
    boolean windows = File.separatorChar == '\\';
    for (String directory : path.split(File.pathSeparator)) {
      if (directory.isBlank()) {
        continue;
      }
      try {
        Path candidate = Path.of(directory, windows ? "rg.exe" : "rg");
        if (Files.isRegularFile(candidate) && (windows || Files.isExecutable(candidate))) {
          return candidate.toString();
        }
      } catch (InvalidPathException e) {
        // a broken entry of the PATH
      }
    }
    return null;
  }

  /**
   * Returns the first line of <code>rg --version</code>, e.g. "ripgrep 15.1.0".
   *
   * @return <code>null</code> if the binary can not be run
   */
  public static String version(String ripGrepPath) {
    try {
      Process process = new ProcessBuilder(ripGrepPath, "--version").redirectErrorStream(true).start();
      process.getOutputStream().close();
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
        String line = reader.readLine();
        if (!process.waitFor(5, TimeUnit.SECONDS)) {
          process.destroyForcibly();
        }
        return line;
      }
    } catch (IOException e) {
      return null;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return null;
    }
  }
}
