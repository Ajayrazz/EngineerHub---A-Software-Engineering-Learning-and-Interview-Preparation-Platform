package com.engineerhub.learning.internal;

import com.engineerhub.catalog.CatalogApi.PublishedQuiz;
import com.engineerhub.catalog.CatalogApi.QuizQuestion;
import com.engineerhub.shared.error.ApiException;
import com.engineerhub.shared.error.ErrorCode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Grades one attempt against a published quiz's answer key. Pure and deterministic. Each question
 * is worth one mark and is all-or-nothing: the selected options must equal the correct set exactly
 * (no partial credit, so "select all that apply" cannot be gamed by ticking everything). An
 * unanswered question is simply wrong. Malformed input (an unknown question or option, a question
 * answered twice, several options for a single-choice question) is refused, never silently graded.
 */
final class QuizGrader {

  private QuizGrader() {}

  record Answer(UUID questionId, Set<UUID> optionIds) {}

  record QuestionResult(
      UUID questionId,
      boolean correct,
      List<UUID> selected,
      List<UUID> correctOptionIds,
      String explanation) {}

  record Grade(int score, int total, List<QuestionResult> results) {}

  static Grade grade(PublishedQuiz quiz, List<Answer> answers) {
    Map<UUID, QuizQuestion> byId = new HashMap<>();
    quiz.questions().forEach(q -> byId.put(q.id(), q));
    Map<UUID, Set<UUID>> given = new HashMap<>();
    for (Answer a : answers) {
      QuizQuestion q = byId.get(a.questionId());
      if (q == null) {
        throw invalid("This quiz has no such question.");
      }
      if (given.put(a.questionId(), a.optionIds()) != null) {
        throw invalid("A question was answered twice.");
      }
      if (!q.optionIds().containsAll(a.optionIds())) {
        throw invalid("An answer names an option that is not part of the question.");
      }
      if (!q.type().equals("multi") && a.optionIds().size() > 1) {
        throw invalid("This question takes a single answer.");
      }
    }
    List<QuestionResult> results = new ArrayList<>();
    int score = 0;
    for (QuizQuestion q : quiz.questions()) {
      Set<UUID> selected = given.getOrDefault(q.id(), Set.of());
      boolean correct = selected.equals(q.correct());
      if (correct) {
        score++;
      }
      // keep the author's option order for both lists
      List<UUID> selectedOrdered = q.optionIds().stream().filter(selected::contains).toList();
      List<UUID> correctOrdered = q.optionIds().stream().filter(q.correct()::contains).toList();
      results.add(
          new QuestionResult(q.id(), correct, selectedOrdered, correctOrdered, q.explanation()));
    }
    return new Grade(score, quiz.questions().size(), results);
  }

  static Set<UUID> distinct(List<UUID> optionIds) {
    Set<UUID> out = new LinkedHashSet<>(optionIds);
    if (out.size() != optionIds.size()) {
      throw invalid("An option was selected twice.");
    }
    return out;
  }

  private static ApiException invalid(String detail) {
    return new ApiException(ErrorCode.VALIDATION_FAILED, detail);
  }
}
