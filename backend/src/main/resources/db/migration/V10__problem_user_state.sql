-- V10: per-user problem state (docs/06 §submission, PROB-005).
--  * bookmarks: owned by user_id (always from the security context).
--  * user_problem_status: "attempted"/"solved" per user and problem. It is written ONLY by the judge pipeline
--    (Phase 4 consumes submission results); until then it stays empty and every problem is "not attempted".
--    "solved" never reverts (enforced by the writer, documented here).
--  * No cross-module foreign keys: user_id points into identity.users.

CREATE TABLE problems.bookmarks (
    user_id    uuid        NOT NULL,
    problem_id uuid        NOT NULL,
    created_at timestamptz NOT NULL,
    CONSTRAINT pk_bookmarks PRIMARY KEY (user_id, problem_id),
    CONSTRAINT fk_bookmarks_problem FOREIGN KEY (problem_id) REFERENCES problems.problems (id) ON DELETE CASCADE
);
CREATE INDEX idx_bookmarks_user_created ON problems.bookmarks (user_id, created_at DESC); -- "my bookmarks"

CREATE TABLE problems.user_problem_status (
    user_id         uuid        NOT NULL,
    problem_id      uuid        NOT NULL,
    status          varchar(10) NOT NULL,
    first_solved_at timestamptz,
    attempts_count  int         NOT NULL DEFAULT 0,
    best_runtime_ms int,
    updated_at      timestamptz NOT NULL,
    CONSTRAINT pk_user_problem_status PRIMARY KEY (user_id, problem_id),
    CONSTRAINT fk_user_problem_status_problem FOREIGN KEY (problem_id) REFERENCES problems.problems (id) ON DELETE CASCADE,
    CONSTRAINT ck_user_problem_status CHECK (status IN ('attempted','solved'))
);
CREATE INDEX idx_user_problem_status_user ON problems.user_problem_status (user_id, status);
