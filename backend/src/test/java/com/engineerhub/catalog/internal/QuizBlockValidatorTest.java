package com.engineerhub.catalog.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.engineerhub.shared.error.ApiException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class QuizBlockValidatorTest {

  private static String id() {
    return UUID.randomUUID().toString();
  }

  private static Map<String, Object> option(String text) {
    return new LinkedHashMap<>(Map.of("id", id(), "text", text));
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> single() {
    Map<String, Object> a = option("Right");
    Map<String, Object> b = option("Wrong");
    Map<String, Object> q = new LinkedHashMap<>();
    q.put("id", id());
    q.put("type", "single");
    q.put("prompt", "Pick one");
    q.put("options", new ArrayList<>(List.of(a, b)));
    q.put("correct", new ArrayList<>(List.of(a.get("id"))));
    q.put("explanation", "Because.");
    return q;
  }

  private static Map<String, Object> quiz(Map<String, Object>... questions) {
    Map<String, Object> d = new LinkedHashMap<>();
    d.put("quizId", id());
    d.put("title", "Check yourself");
    d.put("questions", new ArrayList<>(List.of(questions)));
    return d;
  }

  @SafeVarargs
  private static List<BlockValidator.Block> validate(Map<String, Object>... blocks) {
    return BlockValidator.validate(List.of(blocks));
  }

  private static Map<String, Object> block(Map<String, Object> data) {
    return Map.of("type", "quiz", "data", data);
  }

  @SuppressWarnings("unchecked")
  private static void rejected(Consumer<Map<String, Object>> breakIt, String mention) {
    Map<String, Object> q = single();
    breakIt.accept(q);
    assertThatThrownBy(() -> validate(block(quiz(q))))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining(mention);
  }

  @Test
  void aWellFormedQuizOfEveryTypeIsAccepted() {
    Map<String, Object> multi = single();
    multi.put("type", "multi");
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> opts = (List<Map<String, Object>>) multi.get("options");
    opts.add(option("Also right"));
    multi.put("correct", new ArrayList<>(List.of(opts.get(0).get("id"), opts.get(2).get("id"))));
    Map<String, Object> tf = single();
    tf.put("type", "true_false");
    var out = validate(block(quiz(single(), multi, tf)));
    assertThat(out).hasSize(1);
    assertThat(out.get(0).data()).containsKeys("quizId", "title", "questions");
  }

  @Test
  void idsAreNormalisedToLowerCaseAndKeptStable() {
    Map<String, Object> d = quiz(single());
    String upper = ((String) d.get("quizId")).toUpperCase();
    d.put("quizId", upper);
    var out = validate(block(d));
    assertThat(out.get(0).data().get("quizId")).isEqualTo(upper.toLowerCase());
  }

  @Test
  void questionCountsAreBounded() {
    assertThatThrownBy(() -> validate(block(quiz())))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("1-20 questions");
    List<Map<String, Object>> many = new ArrayList<>();
    for (int i = 0; i < 21; i++) many.add(single());
    Map<String, Object> d = new LinkedHashMap<>();
    d.put("quizId", id());
    d.put("questions", many);
    assertThatThrownBy(() -> validate(block(d))).hasMessageContaining("1-20 questions");
    many.remove(20);
    assertThat(validate(block(d))).hasSize(1); // exactly 20 is fine
  }

  @Test
  @SuppressWarnings("unchecked")
  void theAnswerKeyMustBeCompleteAndConsistent() {
    rejected(q -> q.put("type", "essay"), "type must be");
    rejected(q -> q.remove("correct"), "mark the correct answer");
    rejected(q -> q.put("correct", List.of()), "mark the correct answer");
    rejected(q -> q.put("correct", List.of(id())), "must be one of the options");
    rejected(
        q -> {
          var opts = (List<Map<String, Object>>) q.get("options");
          q.put("correct", List.of(opts.get(0).get("id"), opts.get(1).get("id")));
        },
        "exactly one option must be correct");
    rejected(
        q -> {
          var opts = (List<Map<String, Object>>) q.get("options");
          q.put("type", "multi");
          q.put("correct", List.of(opts.get(0).get("id"), opts.get(0).get("id")));
        },
        "twice");
  }

  @Test
  @SuppressWarnings("unchecked")
  void optionCountsDependOnTheType() {
    rejected(q -> ((List<Object>) q.get("options")).remove(1), "needs 2-6 options");
    rejected(
        q -> {
          var opts = (List<Object>) q.get("options");
          for (int i = 0; i < 5; i++) opts.add(option("x" + i));
        },
        "needs 2-6 options");
    rejected(
        q -> {
          q.put("type", "true_false");
          ((List<Object>) q.get("options")).add(option("Maybe"));
        },
        "exactly 2 options");
    rejected(
        q -> {
          q.put("type", "multi");
          var opts = (List<Object>) q.get("options");
          for (int i = 0; i < 7; i++) opts.add(option("x" + i));
        },
        "needs 2-8 options");
  }

  @Test
  @SuppressWarnings("unchecked")
  void idsMustBeUuidsAndUnique() {
    rejected(q -> q.put("id", "q1"), "UUID");
    rejected(
        q -> ((Map<String, Object>) ((List<Object>) q.get("options")).get(0)).put("id", "a"),
        "UUID");
    rejected(
        q -> {
          var opts = (List<Map<String, Object>>) q.get("options");
          opts.get(1).put("id", opts.get(0).get("id"));
        },
        "duplicate option id");
    Map<String, Object> a = single();
    Map<String, Object> b = single();
    b.put("id", a.get("id"));
    assertThatThrownBy(() -> validate(block(quiz(a, b))))
        .hasMessageContaining("duplicate question id");
    Map<String, Object> noId = quiz(single());
    noId.remove("quizId");
    assertThatThrownBy(() -> validate(block(noId))).hasMessageContaining("quizId must be a UUID");
    Map<String, Object> one = quiz(single());
    Map<String, Object> two = quiz(single());
    two.put("quizId", one.get("quizId"));
    assertThatThrownBy(() -> validate(block(one), block(two))).hasMessageContaining("another quiz");
  }

  @Test
  @SuppressWarnings("unchecked")
  void unknownKeysTextLimitsAndControlCharactersAreRefused() {
    rejected(q -> q.put("html", "<b>"), "unexpected field");
    rejected(
        q -> ((Map<String, Object>) ((List<Object>) q.get("options")).get(0)).put("correct", true),
        "unexpected field");
    rejected(q -> q.put("prompt", ""), "prompt must be");
    rejected(q -> q.put("prompt", "x".repeat(2001)), "prompt must be");
    rejected(q -> q.put("prompt", "bad\u0000prompt"), "control character");
    rejected(q -> q.put("explanation", "x".repeat(2001)), "explanation must be");
    rejected(
        q ->
            ((Map<String, Object>) ((List<Object>) q.get("options")).get(0))
                .put("text", "x".repeat(301)),
        "text must be");
    rejected(
        q ->
            ((Map<String, Object>) ((List<Object>) q.get("options")).get(0))
                .put("text", "line\nbreak"),
        "control character");
    Map<String, Object> d = quiz(single());
    d.put("extra", 1);
    assertThatThrownBy(() -> validate(block(d))).hasMessageContaining("unexpected field");
  }

  @Test
  void aQuestionWithoutAnExplanationIsFine() {
    Map<String, Object> q = single();
    q.remove("explanation");
    assertThat(validate(block(quiz(q)))).hasSize(1);
  }
}
