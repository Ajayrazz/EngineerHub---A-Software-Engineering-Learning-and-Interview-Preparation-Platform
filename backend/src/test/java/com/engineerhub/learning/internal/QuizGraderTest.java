package com.engineerhub.learning.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.engineerhub.catalog.CatalogApi.PublishedQuiz;
import com.engineerhub.catalog.CatalogApi.QuizQuestion;
import com.engineerhub.learning.internal.QuizGrader.Answer;
import com.engineerhub.shared.error.ApiException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class QuizGraderTest {

  private static final UUID A = UUID.randomUUID(), B = UUID.randomUUID(), C = UUID.randomUUID();
  private static final UUID Q1 = UUID.randomUUID(), Q2 = UUID.randomUUID(), Q3 = UUID.randomUUID();

  // Q1 single (A right), Q2 multi (A and C right), Q3 true/false (B right)
  private static final PublishedQuiz QUIZ =
      new PublishedQuiz(
          UUID.randomUUID(),
          UUID.randomUUID(),
          UUID.randomUUID(),
          List.of(
              new QuizQuestion(Q1, "single", List.of(A, B, C), Set.of(A), "why one"),
              new QuizQuestion(Q2, "multi", List.of(A, B, C), Set.of(A, C), null),
              new QuizQuestion(Q3, "true_false", List.of(A, B), Set.of(B), "why three")));

  private static Answer a(UUID q, UUID... options) {
    return new Answer(q, Set.of(options));
  }

  @Test
  void everyQuestionRightScoresFullMarks() {
    var g = QuizGrader.grade(QUIZ, List.of(a(Q1, A), a(Q2, A, C), a(Q3, B)));
    assertThat(g.score()).isEqualTo(3);
    assertThat(g.total()).isEqualTo(3);
    assertThat(g.results()).allMatch(QuizGrader.QuestionResult::correct);
  }

  @Test
  void aMultipleChoiceQuestionIsAllOrNothing() {
    assertThat(QuizGrader.grade(QUIZ, List.of(a(Q2, A))).results().get(1).correct())
        .isFalse(); // missing C
    assertThat(QuizGrader.grade(QUIZ, List.of(a(Q2, A, B, C))).results().get(1).correct())
        .isFalse(); // ticked everything
    assertThat(QuizGrader.grade(QUIZ, List.of(a(Q2, A, B))).results().get(1).correct()).isFalse();
    assertThat(QuizGrader.grade(QUIZ, List.of(a(Q2, C, A))).results().get(1).correct())
        .isTrue(); // order is irrelevant
  }

  @Test
  void anUnansweredOrWrongQuestionScoresNothingButIsStillReportedWithItsKey() {
    var g = QuizGrader.grade(QUIZ, List.of(a(Q1, B)));
    assertThat(g.score()).isZero();
    assertThat(g.results()).hasSize(3);
    var r1 = g.results().get(0);
    assertThat(r1.selected()).containsExactly(B);
    assertThat(r1.correctOptionIds()).containsExactly(A);
    assertThat(r1.explanation()).isEqualTo("why one");
    assertThat(g.results().get(1).selected()).isEmpty(); // never answered
    assertThat(g.results().get(1).explanation()).isNull();
  }

  @Test
  void aPartlyRightAttemptCountsOnlyTheRightQuestions() {
    var g = QuizGrader.grade(QUIZ, List.of(a(Q1, A), a(Q2, A), a(Q3, B)));
    assertThat(g.score()).isEqualTo(2);
  }

  @Test
  void resultsFollowTheQuestionAndOptionOrderOfTheQuizNotTheAnswers() {
    var g = QuizGrader.grade(QUIZ, List.of(a(Q3, B), a(Q2, C, A), a(Q1, A)));
    assertThat(g.results().stream().map(QuizGrader.QuestionResult::questionId))
        .containsExactly(Q1, Q2, Q3);
    assertThat(g.results().get(1).selected()).containsExactly(A, C);
    assertThat(g.results().get(1).correctOptionIds()).containsExactly(A, C);
  }

  @Test
  void malformedAttemptsAreRefusedNeverSilentlyGraded() {
    assertThatThrownBy(() -> QuizGrader.grade(QUIZ, List.of(a(UUID.randomUUID(), A))))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("no such question");
    assertThatThrownBy(() -> QuizGrader.grade(QUIZ, List.of(a(Q1, A), a(Q1, B))))
        .hasMessageContaining("answered twice");
    assertThatThrownBy(() -> QuizGrader.grade(QUIZ, List.of(a(Q1, UUID.randomUUID()))))
        .hasMessageContaining("not part of the question");
    assertThatThrownBy(() -> QuizGrader.grade(QUIZ, List.of(a(Q1, A, B))))
        .hasMessageContaining("single answer");
    assertThatThrownBy(() -> QuizGrader.grade(QUIZ, List.of(a(Q3, A, B))))
        .hasMessageContaining("single answer");
    assertThatThrownBy(() -> QuizGrader.distinct(List.of(A, A))).hasMessageContaining("twice");
  }
}
