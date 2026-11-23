package com.engineerhub.catalog.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.engineerhub.shared.error.ApiException;
import com.engineerhub.shared.error.ErrorCode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BlockValidatorTest {

  private static Map<String, Object> block(String type, Map<String, Object> data) {
    return Map.of("type", type, "data", data);
  }

  private static void rejects(List<Map<String, Object>> blocks, String messagePart) {
    assertThatThrownBy(() -> BlockValidator.validate(blocks))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> {
              assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
              assertThat(e.getMessage()).contains(messagePart);
            });
  }

  @Test
  void acceptsTheThreeBlockTypes() {
    var out =
        BlockValidator.validate(
            List.of(
                block("markdown", Map.of("text", "# Title\n\nSome *text*.")),
                block("code", Map.of("language", "python", "code", "print(1)", "caption", "demo")),
                block("callout", Map.of("kind", "tip", "text", "Remember this."))));
    assertThat(out)
        .extracting(BlockValidator.Block::type)
        .containsExactly("markdown", "code", "callout");
  }

  @Test
  void rejectsUnknownTypesAndUnexpectedFields() {
    rejects(
        List.of(block("html", Map.of("text", "<script>alert(1)</script>"))), "unknown block type");
    rejects(List.of(block("markdown", Map.of("text", "x", "html", "<b>"))), "unexpected field");
    rejects(List.of(block("video", Map.of("url", "https://x"))), "unknown block type");
  }

  @Test
  void rejectsBadValues() {
    rejects(List.of(block("code", Map.of("language", "brainfuck", "code", "x"))), "language");
    rejects(List.of(block("callout", Map.of("kind", "danger", "text", "x"))), "kind");
    rejects(List.of(block("markdown", Map.of("text", "   "))), "text");
    rejects(List.of(block("markdown", Map.of("text", 5))), "must be text");
    rejects(List.of(block("markdown", Map.of("text", "a\u0000b"))), "control character");
  }

  @Test
  void enforcesSizeLimits() {
    rejects(
        List.of(block("markdown", Map.of("text", "x".repeat(BlockValidator.MAX_MARKDOWN + 1)))),
        "text");
    rejects(
        List.of(
            block(
                "code",
                Map.of("language", "java", "code", "x".repeat(BlockValidator.MAX_CODE + 1)))),
        "code");
    var many = new java.util.ArrayList<Map<String, Object>>();
    for (int i = 0; i <= BlockValidator.MAX_BLOCKS; i++) {
      many.add(block("markdown", Map.of("text", "x")));
    }
    rejects(many, "at most");
  }

  @Test
  void keepsMarkupAsPlainText() {
    var out =
        BlockValidator.validate(
            List.of(block("markdown", Map.of("text", "<img src=x onerror=alert(1)>"))));
    assertThat(out.get(0).data().get("text")).isEqualTo("<img src=x onerror=alert(1)>");
  }

  @Test
  void normalizesToKnownKeysOnly() {
    var out =
        BlockValidator.validate(
            List.of(block("code", Map.of("language", "java", "code", "int x;"))));
    assertThat(out.get(0).data()).containsOnlyKeys("language", "code");
  }
}
