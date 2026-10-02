package org.eclipse.eripgrep.core;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;

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
