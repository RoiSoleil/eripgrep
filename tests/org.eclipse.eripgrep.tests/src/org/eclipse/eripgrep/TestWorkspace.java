package org.eclipse.eripgrep;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.eclipse.core.resources.*;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.eripgrep.core.RipGrep;
import org.eclipse.eripgrep.core.RipGrepSettings;
import org.eclipse.eripgrep.utils.Utils;
import org.eclipse.ltk.core.refactoring.*;

/**
 * The projects of the tests.
 */
public class TestWorkspace {

  public static IProject createProject(String name) throws CoreException {
    IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(name);
    project.create(null);
    project.open(null);
    return project;
  }

  /**
   * Creates a project in a directory, possibly in the directory of another project.
   */
  public static IProject createProject(String name, IPath location) throws CoreException {
    IWorkspace workspace = ResourcesPlugin.getWorkspace();
    IProject project = workspace.getRoot().getProject(name);
    IProjectDescription description = workspace.newProjectDescription(name);
    description.setLocation(location);
    project.create(description, null);
    project.open(null);
    return project;
  }

  public static IFile createFile(IProject project, String path, String content) throws CoreException {
    return createFile(project, path, content.getBytes(StandardCharsets.UTF_8));
  }

  public static IFile createFile(IProject project, String path, byte[] content) throws CoreException {
    IFile file = project.getFile(path);
    createFolders(file.getParent());
    file.create(new ByteArrayInputStream(content), true, null);
    return file;
  }

  private static void createFolders(IContainer container) throws CoreException {
    if (container instanceof IFolder folder && !folder.exists()) {
      createFolders(folder.getParent());
      folder.create(true, true, null);
    }
  }

  public static String read(IFile file) throws CoreException {
    file.refreshLocal(IResource.DEPTH_ZERO, null);
    try (var input = file.getContents()) {
      return new String(input.readAllBytes(), StandardCharsets.UTF_8);
    } catch (java.io.IOException e) {
      throw new IllegalStateException(e);
    }
  }

  public static void deleteProjects() throws CoreException {
    for (IProject project : ResourcesPlugin.getWorkspace().getRoot().getProjects()) {
      project.delete(true, true, null);
    }
  }

  /**
   * Checks the conditions of a refactoring and, if they allow it, applies its change.
   *
   * @return the operation, for its status and its undo
   */
  public static PerformChangeOperation perform(Refactoring refactoring) throws CoreException {
    PerformChangeOperation operation = new PerformChangeOperation(new CreateChangeOperation(
        new CheckConditionsOperation(refactoring, CheckConditionsOperation.ALL_CONDITIONS), RefactoringStatus.FATAL));
    ResourcesPlugin.getWorkspace().run(operation, null);
    return operation;
  }

  /**
   * The settings of the tests, which are skipped if RipGrep is not installed.
   */
  public static RipGrepSettings settings() {
    assumeTrue(RipGrep.locate(null) != null, "RipGrep is not installed");
    return Utils.getSettings();
  }
}
