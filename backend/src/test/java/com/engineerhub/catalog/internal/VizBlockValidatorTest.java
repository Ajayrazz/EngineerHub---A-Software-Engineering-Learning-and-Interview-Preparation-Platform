package com.engineerhub.catalog.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.engineerhub.shared.error.ApiException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class VizBlockValidatorTest {

  private static Map<String, Object> viz(String algorithm, String input, String param) {
    Map<String, Object> d = new LinkedHashMap<>();
    d.put("algorithm", algorithm);
    d.put("input", input);
    if (param != null) d.put("param", param);
    return Map.of("type", "viz", "data", d);
  }

  @SafeVarargs
  private static List<BlockValidator.Block> validate(Map<String, Object>... blocks) {
    return BlockValidator.validate(List.of(blocks));
  }

  private static void refused(Map<String, Object> block, String mention) {
    assertThatThrownBy(() -> validate(block))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining(mention);
  }

  @Test
  void theSharedCatalogAndTheServerAllowlistDescribeTheSameVisualizations() throws Exception {
    // frontend/src/lib/viz/catalog.json is the source of truth for the UI; the server must accept
    // exactly the same
    Path file = Path.of("..", "frontend", "src", "lib", "viz", "catalog.json");
    assertThat(file).exists();
    JsonNode catalog = new ObjectMapper().readTree(Files.readString(file));
    assertThat(catalog.size()).isEqualTo(BlockValidator.VIZ.size());
    for (JsonNode e : catalog) {
      String id = e.get("id").asString();
      var spec = BlockValidator.VIZ.get(id);
      assertThat(spec).as(id).isNotNull();
      assertThat(spec.inputPattern())
          .as(id + " input pattern")
          .isEqualTo(e.get("inputPattern").asString());
      String param = e.get("paramPattern").isNull() ? null : e.get("paramPattern").asString();
      assertThat(spec.paramPattern()).as(id + " param pattern").isEqualTo(param);
      // the catalog's own example must be accepted by the server
      String defaultParam = e.get("defaultParam").asString();
      var out =
          validate(
              viz(
                  id,
                  e.get("defaultInput").asString(),
                  defaultParam.isEmpty() ? null : defaultParam));
      assertThat(out.get(0).data().get("algorithm")).isEqualTo(id);
    }
  }

  @Test
  void aValidBlockIsStoredTrimmedAndWithOnlyItsOwnFields() {
    var out = validate(viz("binary-search", "  1, 3, 5  ", " 3 "));
    assertThat(out.get(0).data())
        .containsExactly(
            Map.entry("algorithm", "binary-search"),
            Map.entry("input", "1, 3, 5"),
            Map.entry("param", "3"));
    assertThat(validate(viz("bubble-sort", "3, 1, 2", null)).get(0).data())
        .doesNotContainKey("param");
    assertThat(validate(viz("bubble-sort", "3, 1, 2", "")).get(0).data())
        .doesNotContainKey("param"); // a blank value is simply absent
  }

  @Test
  void onlyAllowlistedAlgorithmsAreAccepted() {
    refused(viz("quick-sort", "3, 1, 2", null), "unknown visualization");
    refused(viz("Bubble-Sort", "3, 1, 2", null), "unknown visualization"); // exact match only
    refused(viz("../../etc/passwd", "3, 1, 2", null), "unknown visualization");
    refused(viz("", "3, 1, 2", null), "algorithm must be");
    refused(viz("bubble-sort ", "3, 1, 2", null), "unknown visualization");
  }

  @Test
  void theInputMustFitTheAlgorithmAndNothingElse() {
    for (String bad :
        new String[] {
          "<script>alert(1)</script>",
          "3, 1, x",
          "3;1;2",
          "1",
          "3, 1, 2\n4",
          "1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17",
          "1000, 2",
          "1.5, 2",
          "",
          "3, 1, 2'; drop table x; --"
        }) refused(viz("bubble-sort", bad, null), bad.isEmpty() ? "input must be" : "input");
    assertThat(validate(viz("bubble-sort", "1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16", null)))
        .hasSize(1); // exactly 16 is fine
    refused(
        viz("merge-sort", "1,2,3,4,5,6,7,8,9,10,11,12,13", null),
        "does not fit"); // merge sort allows 12
    assertThat(validate(viz("merge-sort", "1,2,3,4,5,6,7,8,9,10,11,12", null))).hasSize(1);
    refused(viz("valid-parentheses", "(a)", null), "does not fit");
    refused(viz("valid-parentheses", "(".repeat(25), null), "does not fit");
    refused(viz("bfs", "0-9", "0"), "does not fit");
    refused(viz("fibonacci-dp", "100", null), "does not fit");
    refused(viz("bubble-sort", "9".repeat(121), null), "input must be");
  }

  @Test
  void theExtraValueIsRequiredWhereNeededAndForbiddenWhereNot() {
    refused(viz("binary-search", "1, 2, 3", null), "param must be");
    refused(viz("binary-search", "1, 2, 3", "abc"), "value does not fit");
    refused(viz("sliding-window", "1, 2, 3", "123"), "value does not fit");
    refused(viz("bfs", "0-1", "8"), "value does not fit");
    refused(viz("bubble-sort", "3, 1, 2", "5"), "takes no extra value");
    Map<String, Object> notText =
        new LinkedHashMap<>(Map.of("algorithm", "bubble-sort", "input", "3, 1, 2"));
    notText.put("param", 5);
    refused(Map.of("type", "viz", "data", notText), "takes no extra value");
  }

  @Test
  void unknownFieldsAreRefused() {
    Map<String, Object> d =
        new LinkedHashMap<>(Map.of("algorithm", "bubble-sort", "input", "3, 1, 2"));
    d.put("html", "<b>");
    refused(Map.of("type", "viz", "data", d), "unexpected field");
    refused(Map.of("type", "viz", "data", Map.of("algorithm", "bubble-sort")), "input must be");
  }
}
