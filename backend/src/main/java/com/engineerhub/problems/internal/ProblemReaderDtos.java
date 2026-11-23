package com.engineerhub.problems.internal;

import java.time.Instant;
import java.util.List;

/**
 * Public (learner-facing) shapes: built from the published snapshot only, never from drafts or
 * hidden tests.
 */
final class ProblemReaderDtos {

  private ProblemReaderDtos() {}

  record PublicTag(String slug, String name) {}

  record PublicProblemItem(
      String slug,
      String title,
      String difficulty,
      String accessTier,
      List<PublicTag> topics,
      List<PublicTag> patterns,
      Instant publishedAt) {}

  record PublicProblemPage(List<PublicProblemItem> items, int page, int size, long total) {}

  record PublicSample(String input, String expected, String explanation) {}

  record PublicProblem(
      String slug,
      String title,
      String difficulty,
      String accessTier,
      String statement,
      String inputFormat,
      String outputFormat,
      String constraints,
      List<String> hints,
      int timeLimitMs,
      int memoryLimitMb,
      List<PublicTag> topics,
      List<PublicTag> patterns,
      List<PublicSample> samples,
      Instant publishedAt) {}

  record PublicPattern(String slug, String name, long problemCount) {}

  record RandomPick(String slug) {}

  /**
   * The caller's own state for one problem; absent entries mean "not attempted, not bookmarked".
   */
  record ProblemState(String slug, String status, boolean bookmarked) {}

  record ProblemStates(List<ProblemState> states) {}
}
