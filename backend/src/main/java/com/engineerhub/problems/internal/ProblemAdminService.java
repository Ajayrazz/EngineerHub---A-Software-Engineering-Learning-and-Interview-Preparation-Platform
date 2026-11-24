package com.engineerhub.problems.internal;

import com.engineerhub.catalog.CatalogApi;
import com.engineerhub.identity.UserDirectory;
import com.engineerhub.problems.internal.ProblemDtos.PatternCreate;
import com.engineerhub.problems.internal.ProblemDtos.ProblemCreate;
import com.engineerhub.problems.internal.ProblemDtos.ProblemListItem;
import com.engineerhub.problems.internal.ProblemDtos.ProblemPatch;
import com.engineerhub.problems.internal.ProblemDtos.ProblemSourceRequest;
import com.engineerhub.problems.internal.ProblemDtos.ProblemSourceView;
import com.engineerhub.problems.internal.ProblemDtos.ProblemView;
import com.engineerhub.problems.internal.ProblemDtos.TagView;
import com.engineerhub.problems.internal.ProblemDtos.TagsRequest;
import com.engineerhub.problems.internal.ProblemDtos.TestInput;
import com.engineerhub.problems.internal.ProblemDtos.TestView;
import com.engineerhub.problems.internal.ProblemDtos.TestsView;
import com.engineerhub.shared.audit.AuditEvent;
import com.engineerhub.shared.audit.AuditLog;
import com.engineerhub.shared.error.ApiException;
import com.engineerhub.shared.error.ErrorCode;
import com.engineerhub.shared.id.Ids;
import com.engineerhub.shared.messaging.OutboxPublisher;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Problem authoring. <em>Who</em> may touch <em>which problem</em> is decided at the controller
 * ({@code @authz.can('problem.edit','problem',#id)}); everything here is keyed by that id.
 *
 * <p>Invariants: hidden tests never leave this service except through the authoring API; replacing
 * the test set bumps {@code testcase_version} and resets {@code validation_status}; publishing
 * needs a recorded source, an originality confirmation, sample + hidden tests and a
 * <em>verified</em> validation status.
 */
@Service
class ProblemAdminService {

  private final JdbcClient jdbc;
  private final ObjectMapper mapper;
  private final Clock clock;
  private final AuditLog audit;
  private final OutboxPublisher events;
  private final UserDirectory directory;
  private final CatalogApi catalog;
  private final ValidationService validation;

  ProblemAdminService(
      JdbcClient jdbc,
      ObjectMapper mapper,
      Clock clock,
      AuditLog audit,
      OutboxPublisher events,
      UserDirectory directory,
      CatalogApi catalog,
      ValidationService validation) {
    this.jdbc = jdbc;
    this.mapper = mapper;
    this.clock = clock;
    this.audit = audit;
    this.events = events;
    this.directory = directory;
    this.catalog = catalog;
    this.validation = validation;
  }

  private Timestamp now() {
    return Timestamp.from(clock.instant());
  }

  // ================================================================= patterns

  @Transactional(readOnly = true)
  List<TagView> patterns() {
    return jdbc.sql("select id, slug, name from problems.patterns order by name")
        .query(
            (rs, i) -> new TagView(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3)))
        .list();
  }

  @Transactional
  TagView createPattern(PatternCreate r) {
    UUID id = Ids.newId();
    try {
      jdbc.sql("insert into problems.patterns (id, slug, name) values (:id, :s, :n)")
          .param("id", id)
          .param("s", r.slug())
          .param("n", r.name().strip())
          .update();
    } catch (DuplicateKeyException e) {
      throw ApiException.conflict("A pattern with this slug already exists.");
    }
    return new TagView(id, r.slug(), r.name().strip());
  }

  // ================================================================= problems

  @Transactional
  ProblemView create(Authentication actor, ProblemCreate r) {
    UUID caller = directory.current(actor).id();
    UUID id = Ids.newId();
    try {
      jdbc.sql(
              """
              insert into problems.problems (id, slug, title, difficulty, access_tier, created_by, created_at, updated_at)
              values (:id, :slug, :title, :diff, :tier, :by, :now, :now)
              """)
          .param("id", id)
          .param("slug", r.slug())
          .param("title", r.title().strip())
          .param("diff", r.difficulty())
          .param("tier", r.accessTier())
          .param("by", caller)
          .param("now", now())
          .update();
    } catch (DuplicateKeyException e) {
      throw ApiException.conflict("A problem with this slug already exists.");
    }
    audit.record(
        actor,
        caller,
        AuditEvent.of("problem.created", "problem", id.toString())
            .withChange(null, Map.of("slug", r.slug(), "difficulty", r.difficulty())));
    return problem(id);
  }

  @Transactional(readOnly = true)
  List<ProblemListItem> list() {
    return jdbc.sql(
            "select id, slug, title, status, difficulty, access_tier, validation_status, updated_at from problems.problems order by updated_at desc, id limit 500")
        .query(
            (rs, i) ->
                new ProblemListItem(
                    rs.getObject(1, UUID.class),
                    rs.getString(2),
                    rs.getString(3),
                    rs.getString(4),
                    rs.getString(5),
                    rs.getString(6),
                    rs.getString(7),
                    rs.getTimestamp(8).toInstant()))
        .list();
  }

  @Transactional(readOnly = true)
  ProblemView problem(UUID id) {
    var row =
        jdbc.sql(
                """
                select slug, title, status, difficulty, access_tier, statement, input_format, output_format, constraints_text,
                       hints::text, time_limit_ms, memory_limit_mb, checker_type, version, testcase_version,
                       validation_status, published_at, updated_at, (published_at is not null and updated_at > published_at)
                from problems.problems where id = :id
                """)
            .param("id", id)
            .query(
                (rs, i) -> {
                  Object[] o = new Object[19];
                  for (int k = 1; k <= 19; k++) {
                    o[k - 1] = rs.getObject(k);
                  }
                  return o;
                })
            .optional()
            .orElseThrow(() -> ApiException.notFound("Problem not found."));
    int tcv = (Integer) row[14];
    long samples =
        count(
            "select count(*) from problems.test_cases where problem_id = :id and version = :v and is_sample",
            id,
            tcv);
    long hidden =
        count(
            "select count(*) from problems.test_cases where problem_id = :id and version = :v and not is_sample",
            id,
            tcv);
    List<TagView> topics = topicsOf(id);
    List<TagView> patterns =
        jdbc.sql(
                "select p.id, p.slug, p.name from problems.problem_patterns pp join problems.patterns p on p.id = pp.pattern_id where pp.problem_id = :id order by p.name")
            .param("id", id)
            .query(
                (rs, i) ->
                    new TagView(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3)))
            .list();
    ProblemSourceView source =
        jdbc.sql(
                "select kind, license, attribution, source_url, reviewed_by, reviewed_at from problems.problem_sources where problem_id = :id")
            .param("id", id)
            .query(
                (rs, i) ->
                    new ProblemSourceView(
                        rs.getString(1),
                        rs.getString(2),
                        rs.getString(3),
                        rs.getString(4),
                        rs.getObject(5, UUID.class),
                        ts(rs.getTimestamp(6))))
            .optional()
            .orElse(null);
    @SuppressWarnings("unchecked")
    List<String> hints = mapper.readValue((String) row[9], List.class);
    return new ProblemView(
        id,
        (String) row[0],
        (String) row[1],
        (String) row[2],
        (String) row[3],
        (String) row[4],
        (String) row[5],
        (String) row[6],
        (String) row[7],
        (String) row[8],
        hints,
        (Integer) row[10],
        (Integer) row[11],
        (String) row[12],
        (Integer) row[13],
        tcv,
        (int) samples,
        (int) hidden,
        (String) row[15],
        ts((Timestamp) row[16]),
        ts((Timestamp) row[17]),
        (Boolean) row[18],
        topics,
        patterns,
        source);
  }

  private List<TagView> topicsOf(UUID id) {
    List<UUID> ids =
        jdbc.sql("select topic_id from problems.problem_topics where problem_id = :id")
            .param("id", id)
            .query(UUID.class)
            .list();
    List<TagView> out = new ArrayList<>();
    catalog.topics(ids).values().forEach(t -> out.add(new TagView(t.id(), t.slug(), t.name())));
    out.sort(java.util.Comparator.comparing(TagView::name));
    return out;
  }

  @Transactional
  ProblemView patch(UUID id, ProblemPatch p) {
    checkText("statement", p.statement());
    checkText("inputFormat", p.inputFormat());
    checkText("outputFormat", p.outputFormat());
    checkText("constraints", p.constraints());
    if (p.hints() != null) {
      p.hints().forEach(h -> checkText("hint", h));
    }
    // A different checker or different limits change what "correct" means: verification no longer
    // applies.
    var before =
        jdbc.sql(
                "select checker_type, time_limit_ms, memory_limit_mb from problems.problems where id = :id")
            .param("id", id)
            .query((rs, i) -> new Object[] {rs.getString(1), rs.getInt(2), rs.getInt(3)})
            .optional();
    boolean semanticsChanged =
        before.isPresent()
            && ((p.checkerType() != null && !p.checkerType().equals(before.get()[0]))
                || (p.timeLimitMs() != null && !p.timeLimitMs().equals(before.get()[1]))
                || (p.memoryLimitMb() != null && !p.memoryLimitMb().equals(before.get()[2])));
    int n =
        jdbc.sql(
                """
                update problems.problems set
                  title = coalesce(:title, title), statement = coalesce(:statement, statement),
                  input_format = coalesce(:inf, input_format), output_format = coalesce(:outf, output_format),
                  constraints_text = coalesce(:cons, constraints_text),
                  hints = coalesce(cast(:hints as jsonb), hints),
                  time_limit_ms = coalesce(:tl, time_limit_ms), memory_limit_mb = coalesce(:ml, memory_limit_mb),
                  validation_status = case when cast(:reset as boolean) then 'unverified' else validation_status end,
                  checker_type = coalesce(:checker, checker_type),
                  difficulty = coalesce(:diff, difficulty), access_tier = coalesce(:tier, access_tier),
                  version = version + 1, updated_at = :now
                where id = :id and version = :v
                """)
            .param("title", p.title() == null ? null : p.title().strip())
            .param("statement", p.statement())
            .param("inf", p.inputFormat())
            .param("outf", p.outputFormat())
            .param("cons", p.constraints())
            .param("hints", p.hints() == null ? null : mapper.writeValueAsString(p.hints()))
            .param("tl", p.timeLimitMs())
            .param("ml", p.memoryLimitMb())
            .param("checker", p.checkerType())
            .param("reset", semanticsChanged)
            .param("diff", p.difficulty())
            .param("tier", p.accessTier())
            .param("now", now())
            .param("id", id)
            .param("v", p.expectedVersion())
            .update();
    if (n == 0) {
      throw conflictOrMissing(id);
    }
    if (semanticsChanged) {
      validation.markActiveStale(id, "The checker or the limits changed.");
    }
    return problem(id);
  }

  @Transactional
  ProblemView setTags(UUID id, TagsRequest r) {
    requireProblem(id);
    var topicIds = new LinkedHashSet<>(r.topicIds());
    var patternIds = new LinkedHashSet<>(r.patternIds());
    if (catalog.topics(topicIds).size() != topicIds.size()) {
      throw new ApiException(ErrorCode.VALIDATION_FAILED, "Unknown topic.");
    }
    if (!patternIds.isEmpty()) {
      long found =
          jdbc.sql("select count(*) from problems.patterns where id in (:ids)")
              .param("ids", List.copyOf(patternIds))
              .query(Long.class)
              .single();
      if (found != patternIds.size()) {
        throw new ApiException(ErrorCode.VALIDATION_FAILED, "Unknown pattern.");
      }
    }
    jdbc.sql("delete from problems.problem_topics where problem_id = :id").param("id", id).update();
    jdbc.sql("delete from problems.problem_patterns where problem_id = :id")
        .param("id", id)
        .update();
    topicIds.forEach(
        t ->
            jdbc.sql("insert into problems.problem_topics (problem_id, topic_id) values (:p, :t)")
                .param("p", id)
                .param("t", t)
                .update());
    patternIds.forEach(
        t ->
            jdbc.sql(
                    "insert into problems.problem_patterns (problem_id, pattern_id) values (:p, :t)")
                .param("p", id)
                .param("t", t)
                .update());
    touch(id);
    return problem(id);
  }

  // ================================================================= tests

  @Transactional(readOnly = true)
  TestsView tests(UUID id) {
    var row =
        jdbc.sql("select testcase_version, validation_status from problems.problems where id = :id")
            .param("id", id)
            .query((rs, i) -> new Object[] {rs.getInt(1), rs.getString(2)})
            .optional()
            .orElseThrow(() -> ApiException.notFound("Problem not found."));
    List<TestView> tests =
        jdbc.sql(
                "select ordinal, is_sample, input_text, expected_text, explanation from problems.test_cases where problem_id = :id and version = :v order by ordinal")
            .param("id", id)
            .param("v", (Integer) row[0])
            .query(
                (rs, i) ->
                    new TestView(
                        rs.getInt(1),
                        rs.getBoolean(2),
                        rs.getString(3),
                        rs.getString(4),
                        rs.getString(5)))
            .list();
    return new TestsView((Integer) row[0], (String) row[1], tests);
  }

  /** Replaces the whole test set: new version, verification reset, old versions kept. */
  @Transactional
  TestsView replaceTests(
      Authentication actor, UUID id, List<TestInput> input, int expectedVersion) {
    List<TestCaseValidator.Normalized> tests = TestCaseValidator.validate(input);
    int n =
        jdbc.sql(
                "update problems.problems set testcase_version = testcase_version + 1, validation_status = 'unverified', updated_at = :now where id = :id and testcase_version = :v")
            .param("now", now())
            .param("id", id)
            .param("v", expectedVersion)
            .update();
    if (n == 0) {
      throw conflictOrMissing(id);
    }
    validation.markActiveStale(id, "The test set changed.");
    int version = expectedVersion + 1;
    int ordinal = 1;
    for (var t : tests) {
      jdbc.sql(
              "insert into problems.test_cases (id, problem_id, version, ordinal, is_sample, input_text, expected_text, explanation, sha256) values (:id, :p, :v, :o, :s, :i, :e, :x, :h)")
          .param("id", Ids.newId())
          .param("p", id)
          .param("v", version)
          .param("o", ordinal++)
          .param("s", t.sample())
          .param("i", t.input())
          .param("e", t.expected())
          .param("x", t.explanation())
          .param("h", t.sha256())
          .update();
    }
    audit.record(
        actor,
        directory.current(actor).id(),
        AuditEvent.of("problem.tests.replaced", "problem", id.toString())
            .withChange(
                Map.of("testcaseVersion", expectedVersion),
                Map.of("testcaseVersion", version, "tests", tests.size())));
    return tests(id);
  }

  // ================================================================= source & publishing

  @Transactional
  ProblemView setSource(UUID id, ProblemSourceRequest r) {
    requireProblem(id);
    boolean needsLicense = r.kind().equals("licensed") || r.kind().equals("open_license");
    if (needsLicense && (blank(r.license()) || blank(r.attribution()))) {
      throw new ApiException(
          ErrorCode.VALIDATION_FAILED, "Licensed content needs a license and an attribution.");
    }
    jdbc.sql(
            """
            insert into problems.problem_sources (problem_id, kind, license, attribution, source_url, updated_at)
            values (:p, :k, :lic, :att, :url, :now)
            on conflict (problem_id) do update set
              kind = excluded.kind, license = excluded.license, attribution = excluded.attribution,
              source_url = excluded.source_url, reviewed_by = null, reviewed_at = null, updated_at = excluded.updated_at
            """)
        .param("p", id)
        .param("k", r.kind())
        .param("lic", blankToNull(r.license()))
        .param("att", blankToNull(r.attribution()))
        .param("url", blankToNull(r.sourceUrl()))
        .param("now", now())
        .update();
    return problem(id);
  }

  @Transactional
  ProblemView publish(Authentication actor, UUID id, boolean originalityConfirmed) {
    UUID caller = directory.current(actor).id();
    var cur =
        jdbc.sql(
                "select status, validation_status, testcase_version, statement, input_format, output_format from problems.problems where id = :id for update")
            .param("id", id)
            .query(
                (rs, i) ->
                    new Object[] {
                      rs.getString(1),
                      rs.getString(2),
                      rs.getInt(3),
                      rs.getString(4),
                      rs.getString(5),
                      rs.getString(6)
                    })
            .optional()
            .orElseThrow(() -> ApiException.notFound("Problem not found."));
    if (!originalityConfirmed) {
      throw new ApiException(
          ErrorCode.VALIDATION_FAILED,
          "Confirm that the problem is original or properly licensed.");
    }
    if (count("select count(*) from problems.problem_sources where problem_id = :id", id, null)
        == 0) {
      throw ApiException.conflict(
          "Record the content source (original, licensed, ...) before publishing.");
    }
    if (((String) cur[3]).isBlank() || ((String) cur[4]).isBlank() || ((String) cur[5]).isBlank()) {
      throw ApiException.conflict(
          "The statement, input format and output format are required before publishing.");
    }
    int tcv = (Integer) cur[2];
    long samples =
        count(
            "select count(*) from problems.test_cases where problem_id = :id and version = :v and is_sample",
            id,
            tcv);
    long hidden =
        count(
            "select count(*) from problems.test_cases where problem_id = :id and version = :v and not is_sample",
            id,
            tcv);
    if (samples == 0 || hidden == 0) {
      throw ApiException.conflict("A problem needs at least one sample test and one hidden test.");
    }
    if (!"verified".equals(cur[1])) {
      throw ApiException.conflict(
          "The test data is not verified. Publishing requires the validation harness (reference solution checked against a brute-force solution).");
    }

    ProblemView draft = problem(id);
    Map<String, Object> snapshot = new LinkedHashMap<>();
    snapshot.put("title", draft.title());
    snapshot.put("difficulty", draft.difficulty());
    snapshot.put("accessTier", draft.accessTier());
    snapshot.put("statement", draft.statement());
    snapshot.put("inputFormat", draft.inputFormat());
    snapshot.put("outputFormat", draft.outputFormat());
    snapshot.put("constraints", draft.constraints());
    snapshot.put("hints", draft.hints());
    snapshot.put("timeLimitMs", draft.timeLimitMs());
    snapshot.put("memoryLimitMb", draft.memoryLimitMb());
    snapshot.put("checkerType", draft.checkerType());
    snapshot.put(
        "topics",
        draft.topics().stream().map(t -> Map.of("slug", t.slug(), "name", t.name())).toList());
    snapshot.put(
        "patterns",
        draft.patterns().stream().map(t -> Map.of("slug", t.slug(), "name", t.name())).toList());
    // only SAMPLE tests are part of what readers can ever receive
    snapshot.put(
        "samples",
        tests(id).tests().stream()
            .filter(TestView::sample)
            .map(
                t -> {
                  Map<String, Object> m = new LinkedHashMap<>();
                  m.put("input", t.input());
                  m.put("expected", t.expected());
                  if (t.explanation() != null) {
                    m.put("explanation", t.explanation());
                  }
                  return m;
                })
            .toList());
    Timestamp ts = now();
    jdbc.sql(
            "update problems.problems set status = 'published', published_snapshot = cast(:s as jsonb), published_testcase_version = :v, published_at = :now, updated_at = :now where id = :id")
        .param("s", mapper.writeValueAsString(snapshot))
        .param("v", tcv)
        .param("now", ts)
        .param("id", id)
        .update();
    jdbc.sql(
            "update problems.problem_sources set reviewed_by = :u, reviewed_at = :now where problem_id = :id")
        .param("u", caller)
        .param("now", ts)
        .param("id", id)
        .update();
    audit.record(
        actor,
        caller,
        AuditEvent.of("problem.published", "problem", id.toString())
            .withChange(
                Map.of("status", (String) cur[0]),
                Map.of("status", "published", "testcaseVersion", tcv)));
    events.publish(
        "problems.problem.published",
        1,
        "problem",
        id.toString(),
        Map.of("problemId", id, "testcaseVersion", tcv));
    return problem(id);
  }

  @Transactional
  ProblemView setStatus(Authentication actor, UUID id, String target) {
    UUID caller = directory.current(actor).id();
    String from =
        jdbc.sql("select status from problems.problems where id = :id for update")
            .param("id", id)
            .query(String.class)
            .optional()
            .orElseThrow(() -> ApiException.notFound("Problem not found."));
    if (target.equals("draft") && !from.equals("published")) {
      throw ApiException.conflict("The problem is not published.");
    }
    if (target.equals("archived") && from.equals("archived")) {
      throw ApiException.conflict("The problem is already archived.");
    }
    jdbc.sql(
            "update problems.problems set status = :s, published_snapshot = case when cast(:s as varchar) = 'published' then published_snapshot else null end, published_testcase_version = case when cast(:s as varchar) = 'published' then published_testcase_version else null end, published_at = case when cast(:s as varchar) = 'published' then published_at else null end, updated_at = :now where id = :id")
        .param("s", target)
        .param("now", now())
        .param("id", id)
        .update();
    audit.record(
        actor,
        caller,
        AuditEvent.of(
                "problem." + (target.equals("archived") ? "archived" : "unpublished"),
                "problem",
                id.toString())
            .withChange(Map.of("status", from), Map.of("status", target)));
    events.publish(
        "problems.problem." + (target.equals("archived") ? "archived" : "unpublished"),
        1,
        "problem",
        id.toString(),
        Map.of("problemId", id));
    return problem(id);
  }

  // ================================================================= helpers

  private long count(String sql, UUID id, Integer version) {
    var q = jdbc.sql(sql).param("id", id);
    if (version != null) {
      q = q.param("v", version);
    }
    return q.query(Long.class).single();
  }

  private void touch(UUID id) {
    jdbc.sql("update problems.problems set updated_at = :now where id = :id")
        .param("now", now())
        .param("id", id)
        .update();
  }

  private void requireProblem(UUID id) {
    if (count("select count(*) from problems.problems where id = :id", id, null) == 0) {
      throw ApiException.notFound("Problem not found.");
    }
  }

  private ApiException conflictOrMissing(UUID id) {
    return count("select count(*) from problems.problems where id = :id", id, null) > 0
        ? ApiException.conflict("The problem was changed by someone else. Reload and try again.")
        : ApiException.notFound("Problem not found.");
  }

  private static void checkText(String field, String value) {
    if (value == null) {
      return;
    }
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (c < 0x20 && c != '\n' && c != '\r' && c != '\t' || c == 0x7f) {
        throw new ApiException(
            ErrorCode.VALIDATION_FAILED, field + " contains a control character.");
      }
    }
  }

  private static Instant ts(Timestamp t) {
    return t == null ? null : t.toInstant();
  }

  private static boolean blank(String s) {
    return s == null || s.isBlank();
  }

  private static String blankToNull(String s) {
    return blank(s) ? null : s.strip();
  }
}
