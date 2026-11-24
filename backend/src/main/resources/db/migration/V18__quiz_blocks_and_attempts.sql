-- V18: quiz blocks and quiz attempts (QUIZ-001..003, docs/06 §quiz).
--  * 'quiz' becomes an allowed lesson block type. Backward compatible: existing rows are unaffected.
--  * A quiz lives inside a lesson block (so it is versioned and gated with the lesson); this table only stores a
--    learner's graded attempts. Rows are owned by user_id (taken from the token, never from a request).
--  * Grading happens on the server against the PUBLISHED lesson's answer key; the key never reaches the browser
--    before an attempt is graded. What the learner selected is kept (for review), the answer key is not copied.
--  * No cross-module foreign keys: lesson_id/course_id/quiz_id are plain ids checked through the catalog API.
--  * attempt_no is allocated per (user, quiz) under an advisory lock, and the unique constraint is the backstop.

CREATE TABLE learning.quiz_attempts (
    id         uuid        NOT NULL,
    user_id    uuid        NOT NULL,
    lesson_id  uuid        NOT NULL,
    course_id  uuid        NOT NULL,
    quiz_id    uuid        NOT NULL,
    attempt_no integer     NOT NULL,
    score      integer     NOT NULL,
    total      integer     NOT NULL,
    answers    jsonb       NOT NULL,
    created_at timestamptz NOT NULL,
    CONSTRAINT pk_quiz_attempts PRIMARY KEY (id),
    CONSTRAINT uq_quiz_attempts_no UNIQUE (user_id, quiz_id, attempt_no),
    CONSTRAINT ck_quiz_attempts_score CHECK (score >= 0 AND total >= 1 AND score <= total)
);
CREATE INDEX idx_quiz_attempts_user_quiz ON learning.quiz_attempts (user_id, quiz_id, created_at DESC);

-- 'quiz' joins the allowed lesson block types (the application validates the content strictly: BlockValidator)
ALTER TABLE catalog.content_blocks DROP CONSTRAINT ck_blocks_type;
ALTER TABLE catalog.content_blocks ADD CONSTRAINT ck_blocks_type CHECK (type IN ('markdown', 'code', 'callout', 'quiz'));
