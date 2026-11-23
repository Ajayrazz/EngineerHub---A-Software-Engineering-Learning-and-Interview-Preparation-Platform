-- V6: catalog (courses, sections, lessons, content blocks, topics, content sources) — docs/06 §catalog, docs/20.
--  * Draft/publish: authors edit the live rows ("draft"); publishing copies a validated snapshot into
--    lessons.published_snapshot, which is the ONLY thing the reader API serves. Editing never changes
--    what learners see until the next publish.
--  * Ordering columns are unique per parent but DEFERRABLE, so a reorder is one transaction.
--  * No cross-module foreign keys (created_by is a plain user id; identity owns its table).

CREATE SCHEMA IF NOT EXISTS catalog;

CREATE TABLE catalog.topics (
    id        uuid         NOT NULL,
    slug      varchar(60)  NOT NULL,
    name      varchar(100) NOT NULL,
    parent_id uuid,
    position  int          NOT NULL,
    CONSTRAINT pk_topics PRIMARY KEY (id),
    CONSTRAINT uq_topics_slug UNIQUE (slug),
    CONSTRAINT fk_topics_parent FOREIGN KEY (parent_id) REFERENCES catalog.topics (id)
);

CREATE TABLE catalog.courses (
    id           uuid          NOT NULL,
    slug         varchar(80)   NOT NULL,
    title        varchar(200)  NOT NULL,
    description  varchar(2000) NOT NULL DEFAULT '',
    topic_id     uuid,
    difficulty   varchar(12)   NOT NULL,
    access_tier  varchar(8)    NOT NULL,
    status       varchar(10)   NOT NULL DEFAULT 'draft',
    est_minutes  int,
    version      int           NOT NULL DEFAULT 0,
    created_by   uuid          NOT NULL,
    created_at   timestamptz   NOT NULL,
    updated_at   timestamptz   NOT NULL,
    published_at timestamptz,
    CONSTRAINT pk_courses PRIMARY KEY (id),
    CONSTRAINT uq_courses_slug UNIQUE (slug),
    CONSTRAINT fk_courses_topic FOREIGN KEY (topic_id) REFERENCES catalog.topics (id),
    CONSTRAINT ck_courses_difficulty CHECK (difficulty IN ('beginner','intermediate','advanced')),
    CONSTRAINT ck_courses_tier CHECK (access_tier IN ('free','premium')),
    CONSTRAINT ck_courses_status CHECK (status IN ('draft','published','archived'))
);
CREATE INDEX idx_courses_status_topic ON catalog.courses (status, topic_id); -- public course listing

CREATE TABLE catalog.sections (
    id         uuid         NOT NULL,
    course_id  uuid         NOT NULL,
    title      varchar(200) NOT NULL,
    position   int          NOT NULL,
    CONSTRAINT pk_sections PRIMARY KEY (id),
    CONSTRAINT fk_sections_course FOREIGN KEY (course_id) REFERENCES catalog.courses (id),
    CONSTRAINT uq_sections_position UNIQUE (course_id, position) DEFERRABLE INITIALLY DEFERRED
);

CREATE TABLE catalog.lessons (
    id                 uuid         NOT NULL,
    course_id          uuid         NOT NULL,
    section_id         uuid         NOT NULL,
    slug               varchar(80)  NOT NULL,
    title              varchar(200) NOT NULL,
    position           int          NOT NULL,
    access_tier        varchar(8)   NOT NULL,
    free_preview       boolean      NOT NULL DEFAULT false,
    est_read_min       int,
    status             varchar(10)  NOT NULL DEFAULT 'draft',
    version            int          NOT NULL DEFAULT 0,
    published_snapshot jsonb,
    published_at       timestamptz,
    created_at         timestamptz  NOT NULL,
    updated_at         timestamptz  NOT NULL,
    CONSTRAINT pk_lessons PRIMARY KEY (id),
    CONSTRAINT fk_lessons_course FOREIGN KEY (course_id) REFERENCES catalog.courses (id),
    CONSTRAINT fk_lessons_section FOREIGN KEY (section_id) REFERENCES catalog.sections (id),
    CONSTRAINT uq_lessons_slug UNIQUE (course_id, slug),
    CONSTRAINT uq_lessons_position UNIQUE (section_id, position) DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT ck_lessons_tier CHECK (access_tier IN ('free','premium')),
    CONSTRAINT ck_lessons_status CHECK (status IN ('draft','published','archived')),
    CONSTRAINT ck_lessons_published CHECK (status <> 'published' OR published_snapshot IS NOT NULL)
);
CREATE INDEX idx_lessons_course_status ON catalog.lessons (course_id, status); -- outline of a course

CREATE TABLE catalog.content_blocks (
    id             uuid        NOT NULL,
    lesson_id      uuid        NOT NULL,
    position       int         NOT NULL,
    type           varchar(20) NOT NULL,
    schema_version smallint    NOT NULL DEFAULT 1,
    data           jsonb       NOT NULL,
    CONSTRAINT pk_content_blocks PRIMARY KEY (id),
    CONSTRAINT fk_blocks_lesson FOREIGN KEY (lesson_id) REFERENCES catalog.lessons (id) ON DELETE CASCADE,
    CONSTRAINT uq_blocks_position UNIQUE (lesson_id, position) DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT ck_blocks_type CHECK (type IN ('markdown','code','callout'))
);

-- Provenance (docs/20 §3). One row per lesson for now; a lesson cannot be published without it.
CREATE TABLE catalog.content_sources (
    id           uuid         NOT NULL,
    entity_type  varchar(20)  NOT NULL,
    entity_id    uuid         NOT NULL,
    kind         varchar(20)  NOT NULL,
    license      varchar(60),
    attribution  varchar(300),
    source_url   varchar(500),
    reviewed_by  uuid,
    reviewed_at  timestamptz,
    updated_at   timestamptz  NOT NULL,
    CONSTRAINT pk_content_sources PRIMARY KEY (id),
    CONSTRAINT uq_content_sources_entity UNIQUE (entity_type, entity_id),
    CONSTRAINT ck_sources_kind CHECK (kind IN ('original','licensed','open_license','ai_assisted')),
    CONSTRAINT ck_sources_entity CHECK (entity_type IN ('lesson'))
);

-- Seed taxonomy (docs/16: 31 DSA topics). Names are generic computer-science concepts; the owner may
-- rename or extend them through the admin UI later. Ids are UUIDv7 (PostgreSQL 18).
INSERT INTO catalog.topics (id, slug, name, position)
SELECT uuidv7(), slug, name, ord FROM (VALUES
  (1,'arrays','Arrays'), (2,'strings','Strings'), (3,'hashing','Hashing'),
  (4,'two-pointers','Two Pointers'), (5,'sliding-window','Sliding Window'), (6,'prefix-sums','Prefix Sums'),
  (7,'sorting','Sorting'), (8,'binary-search','Binary Search'), (9,'linked-lists','Linked Lists'),
  (10,'stacks','Stacks'), (11,'queues','Queues'), (12,'monotonic-stacks','Monotonic Stacks'),
  (13,'recursion','Recursion'), (14,'backtracking','Backtracking'), (15,'binary-trees','Binary Trees'),
  (16,'binary-search-trees','Binary Search Trees'), (17,'heaps','Heaps and Priority Queues'), (18,'tries','Tries'),
  (19,'graphs','Graphs'), (20,'breadth-first-search','Breadth-First Search'), (21,'depth-first-search','Depth-First Search'),
  (22,'shortest-paths','Shortest Paths'), (23,'union-find','Union-Find'), (24,'topological-sort','Topological Sort'),
  (25,'dynamic-programming','Dynamic Programming'), (26,'greedy','Greedy Algorithms'), (27,'intervals','Intervals'),
  (28,'bit-manipulation','Bit Manipulation'), (29,'math','Math and Number Theory'), (30,'divide-and-conquer','Divide and Conquer'),
  (31,'range-queries','Range Queries')
) AS t(ord, slug, name);
