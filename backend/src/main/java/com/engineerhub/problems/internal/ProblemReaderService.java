package com.engineerhub.problems.internal;

import com.engineerhub.billing.EntitlementService;
import com.engineerhub.identity.UserDirectory;
import com.engineerhub.problems.internal.ProblemReaderDtos.ProblemState;
import com.engineerhub.problems.internal.ProblemReaderDtos.ProblemStates;
import com.engineerhub.problems.internal.ProblemReaderDtos.PublicPattern;
import com.engineerhub.problems.internal.ProblemReaderDtos.PublicProblem;
import com.engineerhub.problems.internal.ProblemReaderDtos.PublicProblemItem;
import com.engineerhub.problems.internal.ProblemReaderDtos.PublicProblemPage;
import com.engineerhub.problems.internal.ProblemReaderDtos.PublicSample;
import com.engineerhub.problems.internal.ProblemReaderDtos.PublicTag;
import com.engineerhub.shared.error.ApiException;
import com.engineerhub.shared.error.ErrorCode;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Reader side of the problem bank. Everything is derived from the published <em>snapshot</em> of
 * published problems (filters included), so draft edits, hidden tests and unverified work are never
 * visible. Premium statements are released only when {@link EntitlementService} says so; the list
 * shows metadata only.
 */
@Service
class ProblemReaderService {

  private static final Set<String> DIFFICULTIES = Set.of("easy", "medium", "hard");
  private static final Set<String> TIERS = Set.of("free", "premium");
  private static final Set<String> SORTS = Set.of("newest", "title", "difficulty");
  private static final Pattern SLUG = Pattern.compile(ProblemDtos.SLUG);
  private static final int MAX_BOOKMARKS = 1000;

  private final JdbcClient jdbc;
  private final ObjectMapper mapper;
  private final Clock clock;
  private final EntitlementService entitlements;
  private final UserDirectory directory;

  ProblemReaderService(
      JdbcClient jdbc,
      ObjectMapper mapper,
      Clock clock,
      EntitlementService entitlements,
      UserDirectory directory) {
    this.jdbc = jdbc;
    this.mapper = mapper;
    this.clock = clock;
    this.entitlements = entitlements;
    this.directory = directory;
  }

  // ------------------------------------------------------------------ filters

  record Filter(String difficulty, String topic, String pattern, String tier, String q) {}

  private record Where(String sql, Map<String, Object> params) {}

  private Where where(Filter f) {
    check(f);
    StringBuilder w = new StringBuilder(" where p.status = 'published'");
    Map<String, Object> params = new LinkedHashMap<>();
    if (f.difficulty() != null) {
      w.append(" and p.published_snapshot->>'difficulty' = :difficulty");
      params.put("difficulty", f.difficulty());
    }
    if (f.tier() != null) {
      w.append(" and p.published_snapshot->>'accessTier' = :tier");
      params.put("tier", f.tier());
    }
    if (f.topic() != null) {
      w.append(" and p.published_snapshot->'topics' @> cast(:topic as jsonb)");
      params.put("topic", mapper.writeValueAsString(List.of(Map.of("slug", f.topic()))));
    }
    if (f.pattern() != null) {
      w.append(" and p.published_snapshot->'patterns' @> cast(:pattern as jsonb)");
      params.put("pattern", mapper.writeValueAsString(List.of(Map.of("slug", f.pattern()))));
    }
    if (f.q() != null && !f.q().isBlank()) {
      // user input is data: LIKE wildcards are escaped so "%" or "_" search literally
      w.append(" and p.published_snapshot->>'title' ilike :q escape '\\'");
      params.put(
          "q",
          "%" + f.q().strip().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%");
    }
    return new Where(w.toString(), params);
  }

  private static void check(Filter f) {
    if (f.difficulty() != null && !DIFFICULTIES.contains(f.difficulty())) {
      throw invalid("difficulty must be easy, medium or hard");
    }
    if (f.tier() != null && !TIERS.contains(f.tier())) {
      throw invalid("tier must be free or premium");
    }
    if (f.topic() != null && !SLUG.matcher(f.topic()).matches()) {
      throw invalid("topic is not a valid slug");
    }
    if (f.pattern() != null && !SLUG.matcher(f.pattern()).matches()) {
      throw invalid("pattern is not a valid slug");
    }
    if (f.q() != null && f.q().length() > 100) {
      throw invalid("q is too long");
    }
  }

  // ------------------------------------------------------------------ list

  @Transactional(readOnly = true)
  PublicProblemPage list(Filter f, String sort, int page, int size) {
    if (sort != null && !SORTS.contains(sort)) {
      throw invalid("sort must be newest, title or difficulty");
    }
    Where w = where(f);
    int pageSize = Math.max(1, Math.min(size, 50));
    int offset = Math.max(0, Math.min(page, 100_000)) * pageSize;
    String order =
        switch (sort == null ? "newest" : sort) {
          case "title" -> "lower(p.published_snapshot->>'title'), p.id";
          case "difficulty" ->
              "case p.published_snapshot->>'difficulty' when 'easy' then 1 when 'medium' then 2 else 3 end, lower(p.published_snapshot->>'title'), p.id";
          default -> "p.published_at desc, p.id";
        };
    var count = jdbc.sql("select count(*) from problems.problems p" + w.sql());
    w.params().forEach(count::param);
    long total = count.query(Long.class).single();

    var rows =
        jdbc.sql(
            "select p.slug, p.published_snapshot::text, p.published_at from problems.problems p"
                + w.sql()
                + " order by "
                + order
                + " limit :limit offset :offset");
    w.params().forEach(rows::param);
    List<PublicProblemItem> items =
        rows.param("limit", pageSize)
            .param("offset", offset)
            .query((rs, i) -> item(rs.getString(1), rs.getString(2), rs.getTimestamp(3)))
            .list();
    return new PublicProblemPage(items, offset / pageSize, pageSize, total);
  }

  private PublicProblemItem item(String slug, String snapshotJson, Timestamp publishedAt) {
    JsonNode s = mapper.readTree(snapshotJson);
    return new PublicProblemItem(
        slug,
        s.get("title").asString(),
        s.get("difficulty").asString(),
        s.get("accessTier").asString(),
        tags(s.get("topics")),
        tags(s.get("patterns")),
        publishedAt.toInstant());
  }

  private static List<PublicTag> tags(JsonNode arr) {
    List<PublicTag> out = new ArrayList<>();
    if (arr != null) {
      arr.forEach(t -> out.add(new PublicTag(t.get("slug").asString(), t.get("name").asString())));
    }
    return out;
  }

  // ------------------------------------------------------------------ detail

  /** The problem plus whether the response is identical for everyone (cacheable). */
  record DetailResult(PublicProblem problem, boolean sameForEveryone) {}

  @Transactional(readOnly = true)
  DetailResult detail(Authentication caller, String slug) {
    requireSlug(slug);
    var row =
        jdbc.sql(
                "select id, published_snapshot::text, published_at from problems.problems where slug = :s and status = 'published'")
            .param("s", slug)
            .query(
                (rs, i) ->
                    new Object[] {
                      rs.getObject(1, UUID.class), rs.getString(2), rs.getTimestamp(3).toInstant()
                    })
            .optional()
            .orElseThrow(() -> ApiException.notFound("Problem not found."));
    JsonNode s = mapper.readTree((String) row[1]);
    String tier = s.get("accessTier").asString();
    boolean open = tier.equals("free");
    if (!open && !entitlements.canReadPremium(caller, "problem", row[0])) {
      throw new ApiException(
          ErrorCode.ENTITLEMENT_REQUIRED, "This problem is part of the premium content.");
    }
    List<String> hints = new ArrayList<>();
    s.get("hints").forEach(h -> hints.add(h.asString()));
    List<PublicSample> samples = new ArrayList<>();
    s.get("samples")
        .forEach(
            x ->
                samples.add(
                    new PublicSample(
                        x.get("input").asString(),
                        x.get("expected").asString(),
                        x.hasNonNull("explanation") ? x.get("explanation").asString() : null)));
    PublicProblem p =
        new PublicProblem(
            slug,
            s.get("title").asString(),
            s.get("difficulty").asString(),
            tier,
            s.get("statement").asString(),
            s.get("inputFormat").asString(),
            s.get("outputFormat").asString(),
            s.get("constraints").asString(),
            hints,
            s.get("timeLimitMs").asInt(),
            s.get("memoryLimitMb").asInt(),
            tags(s.get("topics")),
            tags(s.get("patterns")),
            samples,
            (java.time.Instant) row[2]);
    return new DetailResult(p, open);
  }

  // ------------------------------------------------------------------ patterns & random

  @Transactional(readOnly = true)
  List<PublicPattern> patterns() {
    // only patterns that have at least one PUBLISHED problem (by snapshot) are advertised
    return jdbc.sql(
            """
            select pt.slug, pt.name, count(p.id)
            from problems.patterns pt
            join problems.problems p on p.status = 'published' and p.published_snapshot->'patterns' @> jsonb_build_array(jsonb_build_object('slug', pt.slug))
            group by pt.slug, pt.name order by pt.name
            """)
        .query((rs, i) -> new PublicPattern(rs.getString(1), rs.getString(2), rs.getLong(3)))
        .list();
  }

  @Transactional
  Optional<String> random(Authentication caller, Filter f, boolean unsolved) {
    Where w = where(f);
    String sql = "select p.slug from problems.problems p" + w.sql();
    if (unsolved && caller != null) {
      sql +=
          " and not exists (select 1 from problems.user_problem_status s where s.user_id = :me and s.problem_id = p.id and s.status = 'solved')";
    }
    var q = jdbc.sql(sql + " order by random() limit 1");
    w.params().forEach(q::param);
    if (unsolved && caller != null) {
      q = q.param("me", directory.current(caller).id());
    }
    return q.query(String.class).optional();
  }

  // ------------------------------------------------------------------ the caller's own state

  @Transactional
  void bookmark(Authentication caller, String slug) {
    requireSlug(slug);
    UUID user = directory.current(caller).id();
    UUID problem = publishedId(slug);
    if (jdbc.sql("select count(*) from problems.bookmarks where user_id = :u")
                .param("u", user)
                .query(Long.class)
                .single()
            >= MAX_BOOKMARKS
        && jdbc.sql(
                    "select count(*) from problems.bookmarks where user_id = :u and problem_id = :p")
                .param("u", user)
                .param("p", problem)
                .query(Long.class)
                .single()
            == 0) {
      throw ApiException.conflict("You have reached the bookmark limit.");
    }
    jdbc.sql(
            "insert into problems.bookmarks (user_id, problem_id, created_at) values (:u, :p, :now) on conflict do nothing")
        .param("u", user)
        .param("p", problem)
        .param("now", Timestamp.from(clock.instant()))
        .update();
  }

  @Transactional
  void unbookmark(Authentication caller, String slug) {
    requireSlug(slug);
    UUID user = directory.current(caller).id();
    jdbc.sql(
            "delete from problems.bookmarks b using problems.problems p where b.problem_id = p.id and p.slug = :s and b.user_id = :u")
        .param("s", slug)
        .param("u", user)
        .update();
  }

  @Transactional
  List<PublicProblemItem> bookmarks(Authentication caller) {
    UUID user = directory.current(caller).id();
    return jdbc.sql(
            "select p.slug, p.published_snapshot::text, p.published_at from problems.bookmarks b join problems.problems p on p.id = b.problem_id where b.user_id = :u and p.status = 'published' order by b.created_at desc limit 1000")
        .param("u", user)
        .query((rs, i) -> item(rs.getString(1), rs.getString(2), rs.getTimestamp(3)))
        .list();
  }

  @Transactional
  ProblemStates states(Authentication caller) {
    UUID user = directory.current(caller).id();
    // bookmarked and/or attempted/solved problems that are still published
    List<ProblemState> states =
        jdbc.sql(
                """
                select p.slug, coalesce(s.status, 'not_attempted'), (b.user_id is not null)
                from problems.problems p
                left join problems.user_problem_status s on s.problem_id = p.id and s.user_id = :u
                left join problems.bookmarks b on b.problem_id = p.id and b.user_id = :u
                where p.status = 'published' and (s.user_id is not null or b.user_id is not null)
                order by p.slug limit 2000
                """)
            .param("u", user)
            .query((rs, i) -> new ProblemState(rs.getString(1), rs.getString(2), rs.getBoolean(3)))
            .list();
    return new ProblemStates(states);
  }

  // ------------------------------------------------------------------ helpers

  private UUID publishedId(String slug) {
    return jdbc.sql("select id from problems.problems where slug = :s and status = 'published'")
        .param("s", slug)
        .query(UUID.class)
        .optional()
        .orElseThrow(() -> ApiException.notFound("Problem not found."));
  }

  private static void requireSlug(String slug) {
    if (slug == null || slug.length() > 80 || !SLUG.matcher(slug).matches()) {
      throw ApiException.notFound("Problem not found.");
    }
  }

  private static ApiException invalid(String detail) {
    return new ApiException(ErrorCode.VALIDATION_FAILED, detail);
  }
}
