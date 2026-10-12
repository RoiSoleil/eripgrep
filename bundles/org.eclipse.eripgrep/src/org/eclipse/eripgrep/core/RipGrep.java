package org.eclipse.eripgrep.core;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.UnaryOperator;

/**
 * Finds the RipGrep binary.
 */
public final class RipGrep {

  private RipGrep() {
  }

  /**
   * Returns the configured binary or, if none is configured, the one found on the PATH or in the usual install
   * directories (Eclipse launched from a desktop shortcut does not inherit the PATH of the shell).
   *
   * @return <code>null</code> if RipGrep is not found
   */
  public static String locate(String configuredPath) {
    if (configuredPath != null && !configuredPath.isBlank()) {
      return Files.isRegularFile(Path.of(configuredPath.trim())) ? configuredPath.trim() : null;
    }
    String path = System.getenv("PATH");
    String found = findOnPath(path);
    if (found == null) {
      found = findOnPath(String.join(File.pathSeparator, installDirectories(File.separatorChar == '\\', System::getenv,
          System.getProperty("user.home"), path)));
    }
    return found;
  }

  /**
   * Returns the directories where the package managers install RipGrep.
   */
  static List<String> installDirectories(boolean windows, UnaryOperator<String> environment, String home, String path) {
    List<String> directories = new ArrayList<>();
    if (windows) {
      directories.addAll(gitForWindowsDirectories(path));
      addUnder(directories, environment.apply("ProgramFiles"), "Git", "usr", "bin");
      addUnder(directories, home, ".cargo", "bin");
      addUnder(directories, home, "scoop", "shims");
      addUnder(directories, environment.apply("LOCALAPPDATA"), "Microsoft", "WinGet", "Links");
      addUnder(directories, environment.apply("ProgramData"), "chocolatey", "bin");
    } else {
      addUnder(directories, home, ".cargo", "bin");
      addUnder(directories, home, ".local", "bin");
      addUnder(directories, home, ".nix-profile", "bin");
      directories.addAll(List.of("/opt/homebrew/bin", "/usr/local/bin", "/home/linuxbrew/.linuxbrew/bin", "/opt/local/bin",
          "/snap/bin", "/run/current-system/sw/bin", "/usr/bin", "/bin"));
    }
    return directories;
  }

  /**
   * Returns the <code>usr\\bin</code> directories of the Git for Windows installations found on the PATH (whose
   * <code>cmd</code>, <code>bin</code> or <code>mingw64\\bin</code> directory is on the PATH, unlike
   * <code>usr\\bin</code> which is only on the PATH of Git Bash).
   */
  static List<String> gitForWindowsDirectories(String path) {
    Set<String> directories = new LinkedHashSet<>();
    if (path == null) {
      return List.of();
    }
    for (String directory : path.split(File.pathSeparator)) {
      if (directory.isBlank()) {
        continue;
      }
      try {
        Path gitDirectory = Path.of(directory);
        if (!Files.isRegularFile(gitDirectory.resolve("git.exe"))) {
          continue;
        }
        Path root = gitDirectory.getParent();
        if (root != null && root.getFileName() != null && root.getFileName().toString().matches("(?i)mingw(32|64)|clangarm64")) {
          root = root.getParent();
        }
        if (root != null) {
          directories.add(root.resolve("usr").resolve("bin").toString());
        }
      } catch (InvalidPathException e) {
        // a broken entry of the PATH
      }
    }
    return new ArrayList<>(directories);
  }

  private static void addUnder(List<String> directories, String parent, String... children) {
    if (parent != null && !parent.isBlank()) {
      directories.add(String.join(File.separator, parent, String.join(File.separator, children)));
    }
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
