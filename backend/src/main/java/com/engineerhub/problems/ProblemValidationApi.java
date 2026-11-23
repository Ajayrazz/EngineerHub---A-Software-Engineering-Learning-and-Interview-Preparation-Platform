package com.engineerhub.problems;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Callback API for the executor (judge). This is the <em>only</em> way a problem becomes verified,
 * and it trusts nothing: an outcome counts only for the exact test-set version and program hashes
 * the run was created for, and only if it is complete (every stored test and every generated case
 * checked).
 */
public interface ProblemValidationApi {

  /** One stored test; hidden ones included, so callers must be the trusted executor only. */
  record TestData(int ordinal, boolean sample, String input, String expected) {}

  record Counterexample(String kind, String input, String expected, String actual) {}

  /**
   * @param passed every check passed
   * @param testsChecked stored tests the reference was checked against
   * @param generatedChecked generated inputs on which reference and brute force were compared
   */
  record Outcome(
      boolean passed,
      int testsChecked,
      int generatedChecked,
      String summary,
      Counterexample counterexample,
      String executor) {}

  /** The job of a run that is still active (null once finished, stale or unknown). */
  Optional<ValidationExecutor.Job> job(UUID runId);

  /** The stored tests of one version, in order. */
  List<TestData> tests(UUID problemId, int testcaseVersion);

  /** requested -> running (ignored for any other state). */
  void started(UUID runId, String executor);

  /**
   * @return true if the outcome was accepted (even a failing one), false if it was ignored or stale
   */
  boolean accept(UUID runId, Outcome outcome);
}
