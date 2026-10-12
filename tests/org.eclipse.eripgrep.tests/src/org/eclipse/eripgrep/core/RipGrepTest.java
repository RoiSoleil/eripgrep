package org.eclipse.eripgrep.core;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RipGrepTest {

  private static final String BINARY = File.separatorChar == '\\' ? "rg.exe" : "rg";

  @Test
  void findsTheBinaryOnThePath(@TempDir Path empty, @TempDir Path directory) throws IOException {
    Path binary = Files.createFile(directory.resolve(BINARY));
    binary.toFile().setExecutable(true);
    String path = empty + File.pathSeparator + File.pathSeparator + directory;
    assertEquals(binary.toString(), RipGrep.findOnPath(path));
    assertNull(RipGrep.findOnPath(empty.toString()));
    assertNull(RipGrep.findOnPath(null));
  }

  @Test
  void ignoresAFileWhichCanNotBeExecuted(@TempDir Path directory) throws IOException {
    assumeTrue(File.separatorChar == '/');
    Files.createFile(directory.resolve(BINARY)).toFile().setExecutable(false);
    assertNull(RipGrep.findOnPath(directory.toString()));
  }

  @Test
  void knowsTheUsualInstallDirectories() {
    String s = File.separator;
    Map<String, String> environment = Map.of("LOCALAPPDATA", "C:" + s + "AppData", "ProgramData", "C:" + s + "ProgramData",
        "ProgramFiles", "C:" + s + "Program Files");
    assertEquals(List.of("C:" + s + "Program Files" + s + "Git" + s + "usr" + s + "bin", "C:" + s + "home" + s + ".cargo" + s + "bin", "C:" + s + "home" + s + "scoop" + s + "shims",
        "C:" + s + "AppData" + s + "Microsoft" + s + "WinGet" + s + "Links", "C:" + s + "ProgramData" + s + "chocolatey" + s + "bin"),
        RipGrep.installDirectories(true, environment::get, "C:" + s + "home", null));
    List<String> unix = RipGrep.installDirectories(false, name -> null, "/home/me", null);
    assertTrue(unix.contains("/home/me" + s + ".cargo" + s + "bin"));
    assertTrue(unix.contains("/opt/homebrew/bin"));
    assertTrue(unix.contains("/usr/local/bin"));
    assertFalse(RipGrep.installDirectories(false, name -> null, null, null).stream().anyMatch(d -> d.contains("cargo")));
    assertEquals(List.of(), RipGrep.installDirectories(true, name -> null, null, null));
  }

  @Test
  void findsTheUsrBinOfGitForWindows(@TempDir Path programs, @TempDir Path other) throws IOException {
    Path git = programs.resolve("Git");
    Files.createDirectories(git.resolve("cmd"));
    Files.createFile(git.resolve("cmd").resolve("git.exe"));
    Files.createDirectories(git.resolve("mingw64").resolve("bin"));
    Files.createFile(git.resolve("mingw64").resolve("bin").resolve("git.exe"));
    String path = other + File.pathSeparator + git.resolve("cmd") + File.pathSeparator + git.resolve("mingw64").resolve("bin");
    assertEquals(List.of(git.resolve("usr").resolve("bin").toString()), RipGrep.gitForWindowsDirectories(path));
    assertEquals(List.of(), RipGrep.gitForWindowsDirectories(other.toString()));
    assertEquals(List.of(), RipGrep.gitForWindowsDirectories(null));
  }

  @Test
  void theConfiguredBinaryMustExist(@TempDir Path directory) throws IOException {
    Path binary = Files.createFile(directory.resolve("my-rg"));
    assertEquals(binary.toString(), RipGrep.locate(" " + binary + " "));
    assertNull(RipGrep.locate(directory.resolve("missing").toString()));
  }

  @Test
  void readsTheVersion() {
    String ripGrep = RipGrep.locate(null);
    assumeTrue(ripGrep != null, "RipGrep is not installed");
    assertTrue(RipGrep.version(ripGrep).startsWith("ripgrep "));
  }

  @Test
  void noVersionWithoutBinary(@TempDir Path directory) {
    assertNull(RipGrep.version(directory.resolve("missing").toString()));
  }
}
