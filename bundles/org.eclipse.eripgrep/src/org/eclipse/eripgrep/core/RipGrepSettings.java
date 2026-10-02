package org.eclipse.eripgrep.core;

import java.util.List;

/**
 * The settings of the preference page.
 *
 * @param ripGrepPath            the binary, <code>null</code> if RipGrep is not found
 * @param threads                the number of threads of RipGrep, 0 to let it choose
 * @param maxMatches             the number of matches after which the search stops, 0 for no limit
 * @param extraArguments         arguments added to the command line
 * @param searchInClosedProjects whether a search in the workspace includes the closed projects
 */
public record RipGrepSettings(String ripGrepPath, int threads, int maxMatches, List<String> extraArguments, boolean searchInClosedProjects) {
}
