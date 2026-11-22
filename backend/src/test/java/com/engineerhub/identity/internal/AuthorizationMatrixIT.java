package com.engineerhub.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.engineerhub.identity.Authorizer;
import com.engineerhub.identity.UserDirectory;
import com.engineerhub.shared.id.Ids;
import com.engineerhub.testsupport.IntegrationTest;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * The authorization matrix of docs/08 §3, cell by cell, against the seeded database; plus
 * scoped-grant rules and a build-time guard against typos in {@code @authz.can('…')} expressions.
 */
@IntegrationTest
class AuthorizationMatrixIT {

  @Autowired Authorizer authz;
  @Autowired UserDirectory directory;
  @Autowired JdbcClient jdbc;
  @Autowired MockMvc mvc;
  @Autowired PlatformTransactionManager txm;

  @Autowired
  @org.springframework.beans.factory.annotation.Qualifier("requestMappingHandlerMapping")
  RequestMappingHandlerMapping handlerMapping;

  /**
   * Global (unscoped) capabilities per staff role — mirrors docs/08 §3. Everything else must be
   * denied.
   */
  private static final Map<String, Set<String>> GLOBAL =
      Map.of(
          "ADMIN",
              Set.of(
                  "content.premium.read",
                  "course.edit",
                  "course.publish",
                  "problem.edit",
                  "problem.publish",
                  "moderation.manage",
                  "user.read",
                  "user.suspend",
                  "role.grant.standard",
                  "billing.manage",
                  "audit.read",
                  "settings.manage.partial"),
          "CONTENT_MANAGER", Set.of("content.premium.read", "course.edit", "course.publish"),
          "MODERATOR", Set.of("moderation.manage", "user.mute"),
          "INSTRUCTOR", Set.of(),
          "PROBLEM_SETTER", Set.of());

  /** Capabilities that exist only on explicitly granted resources. */
  private static final Map<String, Set<String>> SCOPED =
      Map.of(
          "INSTRUCTOR", Set.of("course.edit", "content.premium.read"),
          "PROBLEM_SETTER", Set.of("problem.edit", "content.premium.read"));

  private static final List<String> ROLES =
      List.of(
          "SUPER_ADMIN", "ADMIN", "CONTENT_MANAGER", "MODERATOR", "INSTRUCTOR", "PROBLEM_SETTER");

  private static Authentication as(String sub, String... roles) {
    Jwt jwt =
        Jwt.withTokenValue("t")
            .header("alg", "none")
            .subject(sub)
            .claim("preferred_username", sub)
            .build();
    return new JwtAuthenticationToken(
        jwt,
        AuthorityUtils.createAuthorityList(
            java.util.Arrays.stream(roles).map(r -> "ROLE_" + r).toArray(String[]::new)),
        sub);
  }

  private Set<String> allPermissions() {
    return new TreeSet<>(
        jdbc.sql("select code from identity.permissions").query(String.class).list());
  }

  // ----------------------------------------------------------------------- the matrix

  @Test
  void everyRoleAndPermissionCellMatchesTheDocumentedMatrix() {
    Set<String> all = allPermissions();
    assertThat(all).hasSize(15);
    for (String role : ROLES) {
      Set<String> expected = role.equals("SUPER_ADMIN") ? all : GLOBAL.get(role);
      for (String permission : all) {
        assertThat(authz.can(as("u-" + role, role), permission))
            .as("%s -> %s (global)", role, permission)
            .isEqualTo(expected.contains(permission));
      }
    }
  }

  @Test
  void superAdminCanDoEverythingIncludingPermissionsAddedLater() {
    for (String permission : allPermissions()) {
      assertThat(authz.can(as("root", "SUPER_ADMIN"), permission)).as(permission).isTrue();
    }
  }

  @Test
  void aUserWithoutStaffRolesCanDoNothing() {
    Authentication learner = as("learner");
    for (String permission : allPermissions()) {
      assertThat(authz.can(learner, permission)).as(permission).isFalse();
      assertThat(authz.can(learner, permission, "course", "c1"))
          .as(permission + " scoped")
          .isFalse();
    }
  }

  @Test
  void premiumAndFreeAreNotRolesAndSpoofedRolesGrantNothing() {
    for (String fake :
        new String[] {"PREMIUM_USER", "FREE_USER", "USER", "ROOT", "ADMINISTRATOR", "admin"}) {
      Authentication a = as("x", fake);
      for (String permission : allPermissions()) {
        assertThat(authz.can(a, permission)).as("%s %s", fake, permission).isFalse();
      }
    }
    // an authority that merely LOOKS like a staff role but is not prefixed ROLE_ is ignored too
    Authentication unprefixed =
        new JwtAuthenticationToken(
            Jwt.withTokenValue("t").header("alg", "none").subject("y").build(),
            List.of(new SimpleGrantedAuthority("ADMIN")),
            "y");
    assertThat(authz.can(unprefixed, "user.read")).isFalse();
  }

  @Test
  void rolesCombineAsAUnion() {
    Authentication both = as("u", "MODERATOR", "CONTENT_MANAGER");
    assertThat(authz.can(both, "moderation.manage")).isTrue();
    assertThat(authz.can(both, "course.publish")).isTrue();
    assertThat(authz.can(both, "user.suspend")).isFalse();
  }

  @Test
  void denyByDefaultForAnonymousNullUnknownAndBlankInputs() {
    Authentication anonymous =
        new AnonymousAuthenticationToken(
            "k", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));
    assertThat(authz.can(anonymous, "user.read")).isFalse();
    assertThat(authz.can((Authentication) null, "user.read")).isFalse();
    Authentication admin = as("adm", "SUPER_ADMIN");
    for (String bad :
        new String[] {
          null, "", "  ", "nonexistent.permission", "user.read ", "USER.READ", "user.*", "*"
        }) {
      assertThat(authz.can(admin, bad))
          .as("permission %s", bad)
          .isFalse(); // even SUPER_ADMIN: unknown codes are denied
    }
    assertThat(authz.can(admin, "course.edit", null, "c1")).isFalse();
    assertThat(authz.can(admin, "course.edit", "course", null)).isFalse();
  }

  // ----------------------------------------------------------------------- scoped grants

  /** Provision a real user (so grants can reference it) and return its authentication. */
  private Authentication provisioned(String role) {
    Authentication a = as("kc-" + Ids.newId(), role);
    new TransactionTemplate(txm).executeWithoutResult(s -> directory.current(a));
    return a;
  }

  private UUID idOf(Authentication a) {
    return directory.findBySubject(a.getName()).orElseThrow().id();
  }

  private void grant(
      Authentication to, String permission, String type, String resource, Instant expires) {
    jdbc.sql(
            "insert into identity.scoped_grants (id, user_id, permission_code, resource_type, resource_id, granted_by, granted_at, expires_at) values (:id,:u,:p,:t,:r,:u,:now,:exp)")
        .param("id", Ids.newId())
        .param("u", idOf(to))
        .param("p", permission)
        .param("t", type)
        .param("r", resource)
        .param("now", Timestamp.from(Instant.now()))
        .param("exp", expires == null ? null : Timestamp.from(expires))
        .update();
  }

  @Test
  void scopedCapabilitiesApplyOnlyToGrantedResources() {
    Authentication instructor = provisioned("INSTRUCTOR");
    assertThat(authz.can(instructor, "course.edit", "course", "c1")).as("no grant yet").isFalse();
    assertThat(authz.can(instructor, "course.edit")).as("never globally").isFalse();

    grant(instructor, "course.edit", "course", "c1", null);
    assertThat(authz.can(instructor, "course.edit", "course", "c1")).isTrue();
    assertThat(authz.can(instructor, "course.edit", "course", "c2")).as("other course").isFalse();
    assertThat(authz.can(instructor, "course.edit", "lesson", "c1"))
        .as("other resource type")
        .isFalse();
    assertThat(authz.can(instructor, "course.publish", "course", "c1"))
        .as("other permission")
        .isFalse();
    assertThat(authz.can(instructor, "course.edit")).as("still not global").isFalse();
  }

  @Test
  void expiredGrantsStopWorking() {
    Authentication instructor = provisioned("INSTRUCTOR");
    grant(instructor, "course.edit", "course", "old", Instant.now().minus(Duration.ofMinutes(1)));
    grant(instructor, "course.edit", "course", "live", Instant.now().plus(Duration.ofDays(1)));
    assertThat(authz.can(instructor, "course.edit", "course", "old")).isFalse();
    assertThat(authz.can(instructor, "course.edit", "course", "live")).isTrue();
  }

  @Test
  void aGrantNeverExceedsTheRole() {
    // a PROBLEM_SETTER holding a course.edit grant (e.g. left over after a role change) gets
    // nothing from it
    Authentication setter = provisioned("PROBLEM_SETTER");
    grant(setter, "course.edit", "course", "c1", null);
    assertThat(authz.can(setter, "course.edit", "course", "c1")).isFalse();
    grant(setter, "problem.edit", "problem", "p1", null);
    assertThat(authz.can(setter, "problem.edit", "problem", "p1")).isTrue();

    // and a user with NO role never gains anything from a grant
    Authentication plain = provisioned("NONE");
    grant(plain, "course.edit", "course", "c1", null);
    assertThat(authz.can(plain, "course.edit", "course", "c1")).isFalse();
  }

  @Test
  void aGrantBelongsToOneUser() {
    Authentication a = provisioned("INSTRUCTOR");
    Authentication b = provisioned("INSTRUCTOR");
    grant(a, "course.edit", "course", "c1", null);
    assertThat(authz.can(b, "course.edit", "course", "c1")).isFalse();
  }

  @Test
  void aSuspendedUsersGrantsStopWorking() {
    Authentication instructor = provisioned("INSTRUCTOR");
    grant(instructor, "course.edit", "course", "c1", null);
    jdbc.sql("update identity.users set status = 'suspended' where keycloak_sub = :s")
        .param("s", instructor.getName())
        .update();
    assertThat(authz.can(instructor, "course.edit", "course", "c1")).isFalse();
  }

  @Test
  void globalHoldersPassEveryResourceCheck() {
    assertThat(authz.can(as("cm", "CONTENT_MANAGER"), "course.edit", "course", "anything"))
        .isTrue();
    assertThat(authz.can(as("mod", "MODERATOR"), "course.edit", "course", "anything")).isFalse();
  }

  // ----------------------------------------------------------------------- guard against typos

  @Test
  void everyPermissionCodeReferencedInPreAuthorizeExpressionsExists() {
    Pattern p = Pattern.compile("can\\(\\s*'([^']+)'");
    Set<String> known = allPermissions();
    List<String> referenced = new ArrayList<>();
    for (HandlerMethod hm : handlerMapping.getHandlerMethods().values()) {
      var pre =
          org.springframework.core.annotation.AnnotatedElementUtils.findMergedAnnotation(
              hm.getMethod(), org.springframework.security.access.prepost.PreAuthorize.class);
      if (pre != null) {
        Matcher m = p.matcher(pre.value());
        while (m.find()) {
          referenced.add(m.group(1));
        }
      }
    }
    // (the list is empty until admin endpoints land in M1.3; this test then protects them
    // automatically)
    assertThat(known).containsAll(referenced);
  }

  // ----------------------------------------------------------------------- /me/permissions

  private JwtRequestPostProcessor token(String sub, String... roles) {
    return jwt()
        .jwt(j -> j.subject(sub).claim("preferred_username", sub))
        .authorities(
            java.util.Arrays.stream(roles)
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                .toArray(SimpleGrantedAuthority[]::new));
  }

  @Test
  void myPermissionsReportsGlobalScopedAndGrants() throws Exception {
    String sub = "kc-" + Ids.newId();
    mvc.perform(get("/api/v1/me/permissions").with(token(sub, "INSTRUCTOR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.global.length()").value(0))
        .andExpect(jsonPath("$.scopedCapabilities[0]").value("content.premium.read"))
        .andExpect(jsonPath("$.scopedCapabilities[1]").value("course.edit"))
        .andExpect(jsonPath("$.grants.length()").value(0));

    Authentication a = as(sub, "INSTRUCTOR");
    grant(a, "course.edit", "course", "c9", null);
    grant(
        a,
        "problem.edit",
        "problem",
        "p9",
        null); // exceeds the INSTRUCTOR role -> must not be listed
    mvc.perform(get("/api/v1/me/permissions").with(token(sub, "INSTRUCTOR")))
        .andExpect(jsonPath("$.grants.length()").value(1))
        .andExpect(jsonPath("$.grants[0].permission").value("course.edit"))
        .andExpect(jsonPath("$.grants[0].resourceId").value("c9"));

    mvc.perform(get("/api/v1/me/permissions").with(token("kc-" + Ids.newId(), "ADMIN")))
        .andExpect(jsonPath("$.global.length()").value(12));
    mvc.perform(get("/api/v1/me/permissions").with(token("kc-" + Ids.newId())))
        .andExpect(jsonPath("$.global.length()").value(0));
    mvc.perform(get("/api/v1/me/permissions")).andExpect(status().isUnauthorized());
  }
}
