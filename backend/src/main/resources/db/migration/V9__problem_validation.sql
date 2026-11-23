-- V9: problem validation harness (docs/10 §9, docs/20 §8, PROB-008).
--  * An author supplies three programs per problem: a REFERENCE solution (claimed correct), a BRUTE-force
--    solution (slow but obviously correct) and a GENERATOR (prints a random valid input for a seed).
--  * A validation run asks an executor (the judge, Phase 4) to (1) check the reference against every stored test,
--    (2) compare reference and brute force on N generated inputs. Only a matching, complete, successful outcome can
--    set problems.validation_status = 'verified' (ValidationService.accept), and only for the exact test-set
--    version and program hashes the run was created for.
--  * Programs are untrusted source text: stored and shown as text, executed only inside the judge sandbox.

CREATE TABLE problems.validation_programs (
    problem_id uuid        NOT NULL,
    role       varchar(10) NOT NULL,
    language   varchar(8)  NOT NULL,
    source     text        NOT NULL,
    sha256     char(64)    NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT pk_validation_programs PRIMARY KEY (problem_id, role),
    CONSTRAINT fk_validation_programs_problem FOREIGN KEY (problem_id) REFERENCES problems.problems (id) ON DELETE CASCADE,
    CONSTRAINT ck_validation_programs_role CHECK (role IN ('reference','brute','generator')),
    CONSTRAINT ck_validation_programs_language CHECK (language IN ('java','python','cpp'))
);

CREATE TABLE problems.validation_runs (
    id               uuid        NOT NULL,
    problem_id       uuid        NOT NULL,
    testcase_version int         NOT NULL,
    status           varchar(12) NOT NULL,
    generated_cases  int         NOT NULL,
    program_hashes   jsonb       NOT NULL,
    executor         varchar(40),
    requested_by     uuid        NOT NULL,
    requested_at     timestamptz NOT NULL,
    started_at       timestamptz,
    finished_at      timestamptz,
    tests_checked    int,
    generated_checked int,
    summary          varchar(500),
    counterexample   text,
    CONSTRAINT pk_validation_runs PRIMARY KEY (id),
    CONSTRAINT fk_validation_runs_problem FOREIGN KEY (problem_id) REFERENCES problems.problems (id) ON DELETE CASCADE,
    CONSTRAINT ck_validation_runs_status CHECK (status IN ('requested','running','passed','failed','unavailable','stale','error')),
    CONSTRAINT ck_validation_runs_cases CHECK (generated_cases BETWEEN 1 AND 500)
);
-- at most one active run per problem
CREATE UNIQUE INDEX uq_validation_runs_active ON problems.validation_runs (problem_id) WHERE status IN ('requested','running');
CREATE INDEX idx_validation_runs_problem ON problems.validation_runs (problem_id, requested_at DESC);
