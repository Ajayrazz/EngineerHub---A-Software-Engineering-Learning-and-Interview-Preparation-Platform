-- V11: learner activity statistics (docs/06 §analytics): daily activity, streaks, counters.
--  * Filled ONLY by the analytics event consumer from domain events (learning.lesson.completed, ...), never by
--    request handlers, so it can be rebuilt from the event history and never slows a request.
--  * Each lesson counts once per user (lesson_completions): un-marking and re-completing a lesson, or a
--    redelivered event, cannot inflate the numbers or the streak.
--  * "Which day is it" is the USER's day (identity.users.timezone) at the time of the first completion.
--  * No cross-module foreign keys: ids are plain uuids.

CREATE SCHEMA IF NOT EXISTS analytics;

CREATE TABLE analytics.lesson_completions (
    user_id      uuid        NOT NULL,
    lesson_id    uuid        NOT NULL,
    course_id    uuid        NOT NULL,
    completed_at timestamptz NOT NULL,
    day          date        NOT NULL,
    CONSTRAINT pk_lesson_completions PRIMARY KEY (user_id, lesson_id)
);
CREATE INDEX idx_lesson_completions_user_day ON analytics.lesson_completions (user_id, day);

CREATE TABLE analytics.completed_courses (
    user_id      uuid        NOT NULL,
    course_id    uuid        NOT NULL,
    completed_at timestamptz NOT NULL,
    CONSTRAINT pk_completed_courses PRIMARY KEY (user_id, course_id)
);

CREATE TABLE analytics.daily_user_stats (
    user_id           uuid NOT NULL,
    day               date NOT NULL,
    lessons_completed int  NOT NULL DEFAULT 0,
    CONSTRAINT pk_daily_user_stats PRIMARY KEY (user_id, day),
    CONSTRAINT ck_daily_lessons CHECK (lessons_completed >= 0)
);

CREATE TABLE analytics.user_streaks (
    user_id         uuid        NOT NULL,
    current_streak  int         NOT NULL DEFAULT 0,
    longest_streak  int         NOT NULL DEFAULT 0,
    last_active_day date,
    updated_at      timestamptz NOT NULL,
    CONSTRAINT pk_user_streaks PRIMARY KEY (user_id),
    CONSTRAINT ck_streaks CHECK (current_streak >= 0 AND longest_streak >= current_streak)
);
