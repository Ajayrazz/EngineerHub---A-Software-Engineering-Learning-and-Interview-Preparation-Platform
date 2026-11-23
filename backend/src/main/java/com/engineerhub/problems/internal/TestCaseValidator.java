package com.engineerhub.problems.internal;

import com.engineerhub.problems.internal.ProblemDtos.TestInput;
import com.engineerhub.shared.error.ApiException;
import com.engineerhub.shared.error.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/** Normalises and validates a test set before it is stored. */
final class TestCaseValidator {

  static final int MAX_TESTS = 200;
  static final int MAX_SAMPLES = 10;
  static final int MAX_CHARS = 100_000;

  record Normalized(
      String input, String expected, boolean sample, String explanation, String sha256) {}

  private TestCaseValidator() {}

  static List<Normalized> validate(List<TestInput> tests) {
    if (tests == null || tests.size() > MAX_TESTS) {
      throw invalid("a problem can have at most " + MAX_TESTS + " test cases");
    }
    List<Normalized> out = new ArrayList<>();
    int samples = 0;
    int n = 0;
    for (TestInput t : tests) {
      n++;
      String input = clean(n, "input", t.input());
      String expected = clean(n, "expected output", t.expected());
      if (expected.isBlank()) {
        throw invalid("test " + n + ": the expected output is empty");
      }
      if (t.sample()) {
        samples++;
      }
      String explanation =
          t.explanation() == null || t.explanation().isBlank() ? null : t.explanation().strip();
      if (explanation != null && !t.sample()) {
        throw invalid(
            "test "
                + n
                + ": only sample tests can have an explanation (hidden tests are never shown)");
      }
      out.add(new Normalized(input, expected, t.sample(), explanation, sha256(input, expected)));
    }
    if (samples > MAX_SAMPLES) {
      throw invalid("at most " + MAX_SAMPLES + " sample tests");
    }
    return out;
  }

  /** Line endings are normalised to \n so a Windows editor cannot change what the judge sees. */
  private static String clean(int n, String what, String value) {
    String v = value.replace("\r\n", "\n").replace('\r', '\n');
    if (v.length() > MAX_CHARS) {
      throw invalid("test " + n + ": " + what + " is too long");
    }
    for (int i = 0; i < v.length(); i++) {
      char c = v.charAt(i);
      if (c < 0x20 && c != '\n' && c != '\t' || c == 0x7f) {
        throw invalid("test " + n + ": " + what + " contains a control character");
      }
    }
    return v;
  }

  static String sha256(String input, String expected) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      md.update(input.getBytes(StandardCharsets.UTF_8));
      md.update((byte) 0);
      md.update(expected.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(md.digest());
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private static ApiException invalid(String detail) {
    return new ApiException(ErrorCode.VALIDATION_FAILED, detail);
  }
}
