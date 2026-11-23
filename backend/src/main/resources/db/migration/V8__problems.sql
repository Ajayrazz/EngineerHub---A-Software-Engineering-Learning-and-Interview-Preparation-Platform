-- V8: problem bank (docs/06 §problem, docs/20 §4, PROB-001..003, PROB-008).
--  * Same draft/publish model as the catalog: authors edit the live row, publishing stores an immutable
--    snapshot (published_snapshot) that the public API will serve; hidden tests are NEVER part of it.
--  * Test data is stored inline (size-capped) until the judge phase decides on object storage; every case keeps
--    a SHA-256 of its content. Test sets are versioned: replacing the set creates version+1 (old rows are kept so
--    older submissions stay explainable) and resets validation_status.
--  * validation_status = 'verified' can only be reached by the validation harness (reference solution vs brute
--    force on generated tests, M3.3 / executed by the judge in Phase 4). Publishing requires it (docs/20 §8).
--  * No cross-module foreign keys: topic_id points into catalog.topics and is checked through the catalog API.

CREATE SCHEMA IF NOT EXISTS problems;

CREATE TABLE problems.patterns (
    id   uuid         NOT NULL,
    slug varchar(60)  NOT NULL,
    name varchar(100) NOT NULL,
    CONSTRAINT pk_patterns PRIMARY KEY (id),
    CONSTRAINT uq_patterns_slug UNIQUE (slug)
);

CREATE TABLE problems.problems (
    id                        uuid          NOT NULL,
    slug                      varchar(80)   NOT NULL,
    title                     varchar(200)  NOT NULL,
    status                    varchar(10)   NOT NULL DEFAULT 'draft',
    difficulty                varchar(8)    NOT NULL,
    access_tier               varchar(8)    NOT NULL,
    statement                 text          NOT NULL DEFAULT '',
    input_format              text          NOT NULL DEFAULT '',
    output_format             text          NOT NULL DEFAULT '',
    constraints_text          text          NOT NULL DEFAULT '',
    hints                     jsonb         NOT NULL DEFAULT '[]'::jsonb,
    time_limit_ms             int           NOT NULL DEFAULT 2000,
    memory_limit_mb           int           NOT NULL DEFAULT 256,
    checker_type              varchar(8)    NOT NULL DEFAULT 'exact',
    version                   int           NOT NULL DEFAULT 0,
    testcase_version          int           NOT NULL DEFAULT 0,
    validation_status         varchar(10)   NOT NULL DEFAULT 'unverified',
    published_snapshot        jsonb,
    published_testcase_version int,
    published_at              timestamptz,
    created_by                uuid          NOT NULL,
    created_at                timestamptz   NOT NULL,
    updated_at                timestamptz   NOT NULL,
    CONSTRAINT pk_problems PRIMARY KEY (id),
    CONSTRAINT uq_problems_slug UNIQUE (slug),
    CONSTRAINT ck_problems_status CHECK (status IN ('draft','published','archived')),
    CONSTRAINT ck_problems_difficulty CHECK (difficulty IN ('easy','medium','hard')),
    CONSTRAINT ck_problems_tier CHECK (access_tier IN ('free','premium')),
    CONSTRAINT ck_problems_checker CHECK (checker_type IN ('exact','token','float')),
    CONSTRAINT ck_problems_validation CHECK (validation_status IN ('unverified','verified')),
    CONSTRAINT ck_problems_limits CHECK (time_limit_ms BETWEEN 100 AND 20000 AND memory_limit_mb BETWEEN 16 AND 2048),
    CONSTRAINT ck_problems_published CHECK (status <> 'published' OR (published_snapshot IS NOT NULL AND published_testcase_version IS NOT NULL))
);
CREATE INDEX idx_problems_status_difficulty ON problems.problems (status, difficulty); -- public listing

CREATE TABLE problems.problem_topics (
    problem_id uuid NOT NULL,
    topic_id   uuid NOT NULL,
    CONSTRAINT pk_problem_topics PRIMARY KEY (problem_id, topic_id),
    CONSTRAINT fk_problem_topics_problem FOREIGN KEY (problem_id) REFERENCES problems.problems (id) ON DELETE CASCADE
);
CREATE INDEX idx_problem_topics_topic ON problems.problem_topics (topic_id); -- filter by topic

CREATE TABLE problems.problem_patterns (
    problem_id uuid NOT NULL,
    pattern_id uuid NOT NULL,
    CONSTRAINT pk_problem_patterns PRIMARY KEY (problem_id, pattern_id),
    CONSTRAINT fk_problem_patterns_problem FOREIGN KEY (problem_id) REFERENCES problems.problems (id) ON DELETE CASCADE,
    CONSTRAINT fk_problem_patterns_pattern FOREIGN KEY (pattern_id) REFERENCES problems.patterns (id)
);
CREATE INDEX idx_problem_patterns_pattern ON problems.problem_patterns (pattern_id);

CREATE TABLE problems.test_cases (
    id            uuid        NOT NULL,
    problem_id    uuid        NOT NULL,
    version       int         NOT NULL,
    ordinal       int         NOT NULL,
    is_sample     boolean     NOT NULL,
    input_text    text        NOT NULL,
    expected_text text        NOT NULL,
    explanation   varchar(2000),
    sha256        char(64)    NOT NULL,
    CONSTRAINT pk_test_cases PRIMARY KEY (id),
    CONSTRAINT fk_test_cases_problem FOREIGN KEY (problem_id) REFERENCES problems.problems (id) ON DELETE CASCADE,
    CONSTRAINT uq_test_cases_ordinal UNIQUE (problem_id, version, ordinal)
);

-- Provenance (docs/20 §3), one row per problem. Publishing needs it plus an originality confirmation.
CREATE TABLE problems.problem_sources (
    problem_id  uuid         NOT NULL,
    kind        varchar(20)  NOT NULL,
    license     varchar(60),
    attribution varchar(300),
    source_url  varchar(500),
    reviewed_by uuid,
    reviewed_at timestamptz,
    updated_at  timestamptz  NOT NULL,
    CONSTRAINT pk_problem_sources PRIMARY KEY (problem_id),
    CONSTRAINT fk_problem_sources_problem FOREIGN KEY (problem_id) REFERENCES problems.problems (id) ON DELETE CASCADE,
    CONSTRAINT ck_problem_sources_kind CHECK (kind IN ('original','licensed','open_license','ai_assisted'))
);
