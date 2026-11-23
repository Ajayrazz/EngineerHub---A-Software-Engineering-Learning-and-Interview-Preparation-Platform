package com.engineerhub.problems.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.engineerhub.problems.internal.ProblemDtos.TestInput;
import com.engineerhub.shared.error.ApiException;
import com.engineerhub.shared.error.ErrorCode;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TestCaseValidatorTest {

  private static TestInput t(String in, String out, boolean sample, String expl) {
    return new TestInput(in, out, sample, expl);
  }

  private static void rejects(List<TestInput> tests, String part) {
    assertThatThrownBy(() -> TestCaseValidator.validate(tests))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> {
              assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
              assertThat(e.getMessage()).contains(part);
            });
  }

  @Test
  void normalisesLineEndingsAndHashesContent() {
    var out = TestCaseValidator.validate(List.of(t("1 2\r\n3\r", "6\r\n", false, null)));
    assertThat(out.get(0).input()).isEqualTo("1 2\n3\n");
    assertThat(out.get(0).expected()).isEqualTo("6\n");
    assertThat(out.get(0).sha256())
        .hasSize(64)
        .isEqualTo(TestCaseValidator.sha256("1 2\n3\n", "6\n"));
  }

  @Test
  void hashSeparatesInputFromExpectedOutput() {
    assertThat(TestCaseValidator.sha256("ab", "c"))
        .isNotEqualTo(TestCaseValidator.sha256("a", "bc"));
  }

  @Test
  void rejectsEmptyExpectedControlCharactersAndHiddenExplanations() {
    rejects(List.of(t("1", "   ", false, null)), "expected output is empty");
    rejects(List.of(t("1\u0000", "1", false, null)), "control character");
    rejects(List.of(t("1", "1\u0007", false, null)), "control character");
    rejects(List.of(t("1", "1", false, "why")), "only sample tests");
  }

  @Test
  void allowsEmptyInputTabsAndNewlines() {
    var out =
        TestCaseValidator.validate(
            List.of(t("", "yes\n", true, "No input needed."), t("a\tb\n", "ok", false, null)));
    assertThat(out).hasSize(2);
    assertThat(out.get(0).explanation()).isEqualTo("No input needed.");
  }

  @Test
  void enforcesCounts() {
    var many = new ArrayList<TestInput>();
    for (int i = 0; i <= TestCaseValidator.MAX_TESTS; i++) {
      many.add(t("1", "1", false, null));
    }
    rejects(many, "at most");
    var samples = new ArrayList<TestInput>();
    for (int i = 0; i <= TestCaseValidator.MAX_SAMPLES; i++) {
      samples.add(t("1", "1", true, null));
    }
    rejects(samples, "sample");
    rejects(List.of(t("x".repeat(TestCaseValidator.MAX_CHARS + 1), "1", false, null)), "too long");
  }
}
