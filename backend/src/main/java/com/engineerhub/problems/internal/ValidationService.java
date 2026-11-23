package com.engineerhub.problems.internal;

import com.engineerhub.identity.UserDirectory;
import com.engineerhub.problems.ProblemValidationApi;
import com.engineerhub.problems.ValidationExecutor;
import com.engineerhub.problems.internal.ValidationDtos.ProgramView;
import com.engineerhub.problems.internal.ValidationDtos.RunView;
import com.engineerhub.problems.internal.ValidationDtos.ValidationView;
import com.engineerhub.shared.audit.AuditEvent;
import com.engineerhub.shared.audit.AuditLog;
import com.engineerhub.shared.error.ApiException;
import com.engineerhub.shared.error.ErrorCode;
import com.engineerhub.shared.id.Ids;
import com.engineerhub.shared.messaging.OutboxPublisher;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Validation harness: author-supplied programs, validation runs, and the trust boundary through
 * which a problem can become {@code verified}. See docs/10 §9 and ADR-046.
 *
 * <p>Invariants: a problem is verified only by an accepted, complete, passing outcome for the exact
 * test-set version and program hashes of its run; changing programs, tests, checker or limits marks
 * active runs stale and resets verification; at most one active run per problem (partial unique
 * index); runs are rate limited.
 */
@Service
class ValidationService implements ProblemValidationApi {

  static final Set<String> ROLES = Set.of("reference", "brute", "generator");
  private static final int MAX_RUNS_PER_HOUR = 10;
  private static final int MAX_FIELD = 2_000;
  private static final Logger log = LoggerFactory.getLogger(ValidationService.class);

  private final JdbcClient jdbc;
  private final ObjectMapper mapper;
  private final Clock clock;
  private final AuditLog audit;
  private final OutboxPublisher events;
  private final UserDirectory directory;
  private final ObjectProvider<ValidationExecutor> executors;

  ValidationService(
      JdbcClient jdbc,
      ObjectMapper mapper,
      Clock clock,
      AuditLog audit,
      OutboxPublisher events,
      UserDirectory directory,
      ObjectProvider<ValidationExecutor> executors) {
    this.jdbc = jdbc;
    this.mapper = mapper;
    this.clock = clock;
    this.audit = audit;
    this.events = events;
    this.directory = directory;
    this.executors = executors;
  }

  private Timestamp now() {
    return Timestamp.from(clock.instant());
  }

  // ================================================================= authoring side

  @Transactional
  ValidationView view(UUID problemId) {
    var p = problem(problemId, false);
    List<ProgramView> programs =
        jdbc.sql(
                "select role, language, source, sha256, updated_at from problems.validation_programs where problem_id = :p order by role")
            .param("p", problemId)
            .query(
                (rs, i) ->
                    new ProgramView(
                        rs.getString(1),
                        rs.getString(2),
                        rs.getString(3),
                        rs.getString(4),
                        rs.getTimestamp(5).toInstant()))
            .list();
    List<RunView> runs =
        jdbc.sql(
                "select id, status, testcase_version, generated_cases, tests_checked, generated_checked, executor, summary, counterexample, requested_at, finished_at from problems.validation_runs where problem_id = :p order by requested_at desc limit 10")
            .param("p", problemId)
            .query(
                (rs, i) ->
                    new RunView(
                        rs.getObject(1, UUID.class),
                        rs.getString(2),
                        rs.getInt(3),
                        rs.getInt(4),
                        (Integer) rs.getObject(5),
                        (Integer) rs.getObject(6),
                        rs.getString(7),
                        rs.getString(8),
                        rs.getString(9),
                        rs.getTimestamp(10).toInstant(),
                        ts(rs.getTimestamp(11))))
            .list();
    return new ValidationView(
        p.validationStatus,
        p.testcaseVersion,
        executors.orderedStream().anyMatch(ValidationExecutor::available),
        programs,
        runs);
  }

  @Transactional
  ValidationView saveProgram(
      Authentication actor, UUID problemId, String role, String language, String source) {
    if (!ROLES.contains(role)) {
      throw ApiException.notFound("Unknown program role.");
    }
    problem(problemId, true);
    checkSource(source);
    String sha = sha256(language + "\n" + source);
    jdbc.sql(
            """
            insert into problems.validation_programs (problem_id, role, language, source, sha256, updated_at)
            values (:p, :r, :l, :s, :h, :now)
            on conflict (problem_id, role) do update set language = excluded.language, source = excluded.source,
              sha256 = excluded.sha256, updated_at = excluded.updated_at
            """)
        .param("p", problemId)
        .param("r", role)
        .param("l", language)
        .param("s", source.replace("\r\n", "\n").replace('\r', '\n'))
        .param("h", sha)
        .param("now", now())
        .update();
    invalidate(problemId, "A validation program changed.");
    // the audit entry records which program changed and its hash, never the source text
    audit.record(
        actor,
        directory.current(actor).id(),
        AuditEvent.of("problem.validation.program.updated", "problem", problemId.toString())
            .withChange(null, Map.of("role", role, "language", language, "sha256", sha)));
    return view(problemId);
  }

  @Transactional
  RunView requestRun(Authentication actor, UUID problemId, int generatedCases) {
    UUID caller = directory.current(actor).id();
    var p = problem(problemId, true);
    Map<String, String> hashes = programHashes(problemId);
    if (!hashes.keySet().containsAll(ROLES)) {
      throw ApiException.conflict(
          "Add the reference solution, the brute-force solution and the generator first.");
    }
    long tests =
        jdbc.sql("select count(*) from problems.test_cases where problem_id = :p and version = :v")
            .param("p", problemId)
            .param("v", p.testcaseVersion)
            .query(Long.class)
            .single();
    if (tests == 0) {
      throw ApiException.conflict("Save at least one test case first.");
    }
    long recent =
        jdbc.sql(
                "select count(*) from problems.validation_runs where problem_id = :p and requested_at > :since")
            .param("p", problemId)
            .param("since", Timestamp.from(clock.instant().minusSeconds(3600)))
            .query(Long.class)
            .single();
    if (recent >= MAX_RUNS_PER_HOUR) {
      throw new ApiException(
          ErrorCode.RATE_LIMITED, "Too many validation runs for this problem. Try again later.");
    }
    UUID runId = Ids.newId();
    try {
      jdbc.sql(
              "insert into problems.validation_runs (id, problem_id, testcase_version, status, generated_cases, program_hashes, requested_by, requested_at) values (:id, :p, :v, 'requested', :n, cast(:h as jsonb), :by, :now)")
          .param("id", runId)
          .param("p", problemId)
          .param("v", p.testcaseVersion)
          .param("n", generatedCases)
          .param("h", mapper.writeValueAsString(hashes))
          .param("by", caller)
          .param("now", now())
          .update();
    } catch (DuplicateKeyException e) {
      throw ApiException.conflict("A validation run is already in progress for this problem.");
    }
    audit.record(
        actor,
        caller,
        AuditEvent.of("problem.validation.requested", "problem", problemId.toString())
            .withChange(
                null,
                Map.of(
                    "runId",
                    runId.toString(),
                    "testcaseVersion",
                    p.testcaseVersion,
                    "generatedCases",
                    generatedCases)));

    Optional<ValidationExecutor> executor =
        executors.orderedStream().filter(ValidationExecutor::available).findFirst();
    if (executor.isEmpty()) {
      jdbc.sql(
              "update problems.validation_runs set status = 'unavailable', finished_at = :now, summary = :s where id = :id")
          .param("now", now())
          .param(
              "s",
              "No code execution backend is available yet. Verification runs once the judge is deployed.")
          .param("id", runId)
          .update();
    } else {
      executor.get().submit(job(runId).orElseThrow());
    }
    return run(runId);
  }

  private RunView run(UUID runId) {
    return jdbc.sql(
            "select id, status, testcase_version, generated_cases, tests_checked, generated_checked, executor, summary, counterexample, requested_at, finished_at from problems.validation_runs where id = :id")
        .param("id", runId)
        .query(
            (rs, i) ->
                new RunView(
                    rs.getObject(1, UUID.class),
                    rs.getString(2),
                    rs.getInt(3),
                    rs.getInt(4),
                    (Integer) rs.getObject(5),
                    (Integer) rs.getObject(6),
                    rs.getString(7),
                    rs.getString(8),
                    rs.getString(9),
                    rs.getTimestamp(10).toInstant(),
                    ts(rs.getTimestamp(11))))
        .single();
  }

  /**
   * Programs, tests or limits changed: whatever was verified no longer is, and running work is
   * void.
   */
  void invalidate(UUID problemId, String reason) {
    jdbc.sql(
            "update problems.problems set validation_status = 'unverified', updated_at = :now where id = :p")
        .param("now", now())
        .param("p", problemId)
        .update();
    markActiveStale(problemId, reason);
  }

  void markActiveStale(UUID problemId, String reason) {
    jdbc.sql(
            "update problems.validation_runs set status = 'stale', finished_at = :now, summary = :s where problem_id = :p and status in ('requested','running')")
        .param("now", now())
        .param("s", reason)
        .param("p", problemId)
        .update();
  }

  // ================================================================= executor side (trust
  // boundary)

  @Override
  @Transactional(readOnly = true)
  public Optional<ValidationExecutor.Job> job(UUID runId) {
    var row =
        jdbc.sql(
                "select problem_id, testcase_version, generated_cases from problems.validation_runs where id = :id and status in ('requested','running')")
            .param("id", runId)
            .query(
                (rs, i) -> new Object[] {rs.getObject(1, UUID.class), rs.getInt(2), rs.getInt(3)})
            .optional();
    if (row.isEmpty()) {
      return Optional.empty();
    }
    UUID problemId = (UUID) row.get()[0];
    var p = problem(problemId, false);
    Map<String, ValidationExecutor.Program> programs = new LinkedHashMap<>();
    jdbc.sql(
            "select role, language, source, sha256 from problems.validation_programs where problem_id = :p")
        .param("p", problemId)
        .query(
            (rs, i) ->
                programs.put(
                    rs.getString(1),
                    new ValidationExecutor.Program(
                        rs.getString(2), rs.getString(3), rs.getString(4))))
        .list();
    return Optional.of(
        new ValidationExecutor.Job(
            runId,
            problemId,
            (Integer) row.get()[1],
            p.checkerType,
            p.timeLimitMs,
            p.memoryLimitMb,
            (Integer) row.get()[2],
            programs));
  }

  @Override
  @Transactional(readOnly = true)
  public List<TestData> tests(UUID problemId, int testcaseVersion) {
    return jdbc.sql(
            "select ordinal, is_sample, input_text, expected_text from problems.test_cases where problem_id = :p and version = :v order by ordinal")
        .param("p", problemId)
        .param("v", testcaseVersion)
        .query(
            (rs, i) ->
                new TestData(rs.getInt(1), rs.getBoolean(2), rs.getString(3), rs.getString(4)))
        .list();
  }

  @Override
  @Transactional
  public void started(UUID runId, String executor) {
    jdbc.sql(
            "update problems.validation_runs set status = 'running', started_at = :now, executor = :e where id = :id and status = 'requested'")
        .param("now", now())
        .param("e", clip(executor, 40))
        .param("id", runId)
        .update();
  }

  @Override
  @Transactional
  public boolean accept(UUID runId, Outcome outcome) {
    var run =
        jdbc.sql(
                "select problem_id, testcase_version, generated_cases, program_hashes::text from problems.validation_runs where id = :id and status in ('requested','running') for update")
            .param("id", runId)
            .query(
                (rs, i) ->
                    new Object[] {
                      rs.getObject(1, UUID.class), rs.getInt(2), rs.getInt(3), rs.getString(4)
                    })
            .optional();
    if (run.isEmpty()) {
      return false; // unknown, finished or stale: results are never applied twice or late
    }
    UUID problemId = (UUID) run.get()[0];
    var p =
        problem(
            problemId, true); // row lock: no test/program edit can slip in between check and write
    @SuppressWarnings("unchecked")
    Map<String, String> runHashes = mapper.readValue((String) run.get()[3], Map.class);
    if (p.testcaseVersion != (Integer) run.get()[1]
        || !runHashes.equals(programHashes(problemId))) {
      finish(
          runId,
          "stale",
          outcome,
          "The tests or programs changed while the run was in progress; the result was discarded.");
      return false;
    }
    String summary = clip(outcome.summary(), 500);
    if (!outcome.passed()) {
      finish(runId, "failed", outcome, summary == null ? "Validation failed." : summary);
      jdbc.sql("update problems.problems set validation_status = 'unverified' where id = :p")
          .param("p", problemId)
          .update();
      events.publish(
          "problems.validation.completed",
          1,
          "problem",
          problemId.toString(),
          Map.of("problemId", problemId, "runId", runId, "passed", false));
      return true;
    }
    long expectedTests =
        jdbc.sql("select count(*) from problems.test_cases where problem_id = :p and version = :v")
            .param("p", problemId)
            .param("v", p.testcaseVersion)
            .query(Long.class)
            .single();
    boolean complete =
        expectedTests > 0
            && outcome.testsChecked() == expectedTests
            && outcome.generatedChecked() == (Integer) run.get()[2]
            && outcome.counterexample() == null;
    if (!complete) {
      // a "pass" that did not check everything proves nothing
      log.warn("Validation run {} reported success with an incomplete result; not verified", runId);
      finish(
          runId,
          "error",
          outcome,
          "The executor reported success but not every test and generated case was checked.");
      return true;
    }
    finish(runId, "passed", outcome, summary == null ? "All checks passed." : summary);
    jdbc.sql(
            "update problems.problems set validation_status = 'verified', updated_at = :now where id = :p")
        .param("now", now())
        .param("p", problemId)
        .update();
    events.publish(
        "problems.validation.completed",
        1,
        "problem",
        problemId.toString(),
        Map.of("problemId", problemId, "runId", runId, "passed", true));
    return true;
  }

  private void finish(UUID runId, String status, Outcome o, String summary) {
    String counter = null;
    if (o.counterexample() != null) {
      var c = o.counterexample();
      counter =
          mapper.writeValueAsString(
              Map.of(
                  "kind",
                  nz(clip(clean(c.kind()), 40)),
                  "input",
                  nz(clip(clean(c.input()), MAX_FIELD)),
                  "expected",
                  nz(clip(clean(c.expected()), MAX_FIELD)),
                  "actual",
                  nz(clip(clean(c.actual()), MAX_FIELD))));
    }
    jdbc.sql(
            "update problems.validation_runs set status = :s, finished_at = :now, tests_checked = :tc, generated_checked = :gc, executor = coalesce(:e, executor), summary = :sum, counterexample = :c where id = :id")
        .param("s", status)
        .param("now", now())
        .param("tc", Math.max(0, o.testsChecked()))
        .param("gc", Math.max(0, o.generatedChecked()))
        .param("e", clip(o.executor(), 40))
        .param("sum", clip(clean(summary), 500))
        .param("c", counter)
        .param("id", runId)
        .update();
  }

  // ================================================================= helpers

  private record P(
      int testcaseVersion,
      String validationStatus,
      String checkerType,
      int timeLimitMs,
      int memoryLimitMb) {}

  private P problem(UUID id, boolean lock) {
    return jdbc.sql(
            "select testcase_version, validation_status, checker_type, time_limit_ms, memory_limit_mb from problems.problems where id = :id"
                + (lock ? " for update" : ""))
        .param("id", id)
        .query(
            (rs, i) ->
                new P(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getInt(4), rs.getInt(5)))
        .optional()
        .orElseThrow(() -> ApiException.notFound("Problem not found."));
  }

  private Map<String, String> programHashes(UUID problemId) {
    Map<String, String> m = new LinkedHashMap<>();
    jdbc.sql(
            "select role, sha256 from problems.validation_programs where problem_id = :p order by role")
        .param("p", problemId)
        .query((rs, i) -> m.put(rs.getString(1), rs.getString(2)))
        .list();
    return m;
  }

  private static void checkSource(String source) {
    for (int i = 0; i < source.length(); i++) {
      char c = source.charAt(i);
      if (c < 0x20 && c != '\n' && c != '\r' && c != '\t' || c == 0x7f) {
        throw new ApiException(
            ErrorCode.VALIDATION_FAILED, "The source contains a control character.");
      }
    }
  }

  /** Strips control characters from executor-supplied text before it is stored or shown. */
  private static String clean(String s) {
    return s == null ? null : s.replaceAll("[\\x00-\\x08\\x0b\\x0c\\x0e-\\x1f\\x7f]", "");
  }

  private static String clip(String s, int max) {
    if (s == null) {
      return null;
    }
    return s.length() > max ? s.substring(0, max) : s;
  }

  private static String nz(String s) {
    return s == null ? "" : s;
  }

  private static Instant ts(Timestamp t) {
    return t == null ? null : t.toInstant();
  }

  static String sha256(String text) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
