package com.engineerhub.problems;

import java.util.Map;
import java.util.UUID;

/**
 * Implemented by whoever can run untrusted code safely (the judge, Phase 4). The problems module
 * asks it to verify a problem; it reports back through {@link ProblemValidationApi}. With no
 * available executor a validation run is recorded as {@code unavailable} and nothing can become
 * verified.
 */
public interface ValidationExecutor {

  record Program(String language, String source, String sha256) {}

  /**
   * What to run. Hidden test data is not embedded: the executor fetches it through {@link
   * ProblemValidationApi#tests(UUID, int)} for exactly {@code testcaseVersion}.
   *
   * <p>Required procedure: (1) run the {@code reference} program on every stored test and compare
   * with the expected output using {@code checkerType}; (2) for seeds 1..{@code generatedCases} run
   * the {@code generator} with the seed, feed its output to {@code reference} and {@code brute},
   * and compare the two outputs with {@code checkerType}; all programs run in the judge sandbox
   * with the problem's limits (the brute force may use a larger time limit). Any mismatch, crash or
   * limit violation fails the run.
   */
  record Job(
      UUID runId,
      UUID problemId,
      int testcaseVersion,
      String checkerType,
      int timeLimitMs,
      int memoryLimitMb,
      int generatedCases,
      Map<String, Program> programs) {}

  String name();

  boolean available();

  /**
   * Enqueue the job (do not run it on the caller's thread). Must report via ProblemValidationApi.
   */
  void submit(Job job);
}
