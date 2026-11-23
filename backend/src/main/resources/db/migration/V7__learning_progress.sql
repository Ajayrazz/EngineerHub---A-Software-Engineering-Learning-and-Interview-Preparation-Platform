-- V7: learner enrollments and lesson progress (docs/06 §learning).
--  * Rows are owned by user_id (taken from the security context, never from a request).
--  * No cross-module foreign keys (users live in identity, courses/lessons in catalog): ids are plain uuids and
--    the learning module checks them through the catalog API. A user purge removes these rows by event later.
--  * Progress counts only lessons that are published today; unpublishing a lesson simply stops counting it.

CREATE SCHEMA IF NOT EXISTS learning;

CREATE TABLE learning.enrollments (
    id               uuid        NOT NULL,
    user_id          uuid        NOT NULL,
    course_id        uuid        NOT NULL,
    enrolled_at      timestamptz NOT NULL,
    last_lesson_id   uuid,
    last_activity_at timestamptz,
    completed_at     timestamptz,
    CONSTRAINT pk_enrollments PRIMARY KEY (id),
    CONSTRAINT uq_enrollments_user_course UNIQUE (user_id, course_id)
);
CREATE INDEX idx_enrollments_user_activity ON learning.enrollments (user_id, last_activity_at DESC); -- "my courses"

CREATE TABLE learning.lesson_progress (
    user_id      uuid        NOT NULL,
    lesson_id    uuid        NOT NULL,
    course_id    uuid        NOT NULL,
    status       varchar(12) NOT NULL,
    completed_at timestamptz,
    scroll_pct   smallint    NOT NULL DEFAULT 0,
    updated_at   timestamptz NOT NULL,
    CONSTRAINT pk_lesson_progress PRIMARY KEY (user_id, lesson_id),
    CONSTRAINT ck_progress_status CHECK (status IN ('in_progress','completed')),
    CONSTRAINT ck_progress_scroll CHECK (scroll_pct BETWEEN 0 AND 100)
);
CREATE INDEX idx_progress_user_course ON learning.lesson_progress (user_id, course_id, status); -- course progress
