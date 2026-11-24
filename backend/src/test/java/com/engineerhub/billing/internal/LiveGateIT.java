package com.engineerhub.billing.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.engineerhub.shared.id.Ids;
import com.engineerhub.testsupport.IntegrationTest;
import java.util.Arrays;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The Live Readiness Gate: attestations only a SUPER_ADMIN can record, machine prerequisites where
 * the system can check something, an all-or-nothing switch, and the fact that nothing here lifts
 * the build's refusal of live mode.
 */
@IntegrationTest
class LiveGateIT {

  private static final String BASE = "/api/v1/admin/billing";
  private static final String[] ITEMS = {
    "legal_entity",
    "razorpay_kyc",
    "bank_details",
    "tax_mode_confirmed",
    "policies_published",
    "live_keys_installed",
    "webhook_verified_live",
    "seller_details_real",
    "test_checklist_signed"
  };

  @Autowired MockMvc mvc;
  @Autowired JdbcClient jdbc;
  @Autowired ObjectMapper mapper;

  @BeforeEach
  @AfterEach
  void reset() {
    jdbc.sql(
            "update billing.live_readiness set done = false, note = null, confirmed_by = null, confirmed_at = null")
        .update();
    jdbc.sql("update billing.live_gate set live_ready = false where id = 1").update();
    jdbc.sql(
            "update billing.tax_settings set tax_mode = 'UNCONFIGURED', seller_legal_name = null, seller_address = null, seller_state = null, seller_gstin = null, sac_code = null where id = 1")
        .update();
    jdbc.sql("update billing.refund_settings set policy_text = null where id = 1").update();
  }

  private static JwtRequestPostProcessor who(String sub, String... roles) {
    return jwt()
        .jwt(
            j ->
                j.subject(sub)
                    .claim("preferred_username", sub)
                    .claim("email", sub + "@example.test"))
        .authorities(
            Arrays.stream(roles)
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                .toArray(SimpleGrantedAuthority[]::new));
  }

  private static String sub() {
    return "kc-" + Ids.newId();
  }

  private final JwtRequestPostProcessor owner = who(sub(), "SUPER_ADMIN");
  private final JwtRequestPostProcessor admin = who(sub(), "ADMIN");
  private final JwtRequestPostProcessor manager = who(sub(), "CONTENT_MANAGER");

  private ResultActions call(
      MockHttpServletRequestBuilder b, JwtRequestPostProcessor who, String body) throws Exception {
    MockHttpServletRequestBuilder req = who == null ? b : b.with(who);
    return mvc.perform(
        body == null ? req : req.contentType(MediaType.APPLICATION_JSON).content(body));
  }

  private JsonNode json(ResultActions r) throws Exception {
    return mapper.readTree(r.andReturn().getResponse().getContentAsString());
  }

  private ResultActions tick(String code, boolean done) throws Exception {
    return call(
        put(BASE + "/live-gate/items/" + code),
        owner,
        "{\"done\":%s,\"note\":\"attested in test\"}".formatted(done));
  }

  /** Makes the three machine prerequisites true, the way the owner really would. */
  private void satisfyMachineChecks() throws Exception {
    call(
            put(BASE + "/tax"),
            admin,
            "{\"mode\":\"DISABLED\",\"sellerLegalName\":\"Example Seller\",\"sellerAddress\":\"1 Example Road\"}")
        .andExpect(status().isOk());
    call(
            put(BASE + "/refund-settings"),
            admin,
            "{\"policyText\":\"Written by the owner.\",\"revokeAccessOnRefund\":true,\"autoDetectDuplicates\":true}")
        .andExpect(status().isOk());
  }

  @Test
  void theGateStartsClosedWithNineOpenItemsAndNothingAttested() throws Exception {
    JsonNode g = json(call(get(BASE + "/live-gate"), admin, null).andExpect(status().isOk()));
    assertThat(g.get("liveReady").asBoolean()).isFalse();
    assertThat(g.get("allItemsDone").asBoolean()).isFalse();
    assertThat(g.get("items")).hasSize(9);
    for (JsonNode i : g.get("items")) {
      assertThat(i.get("done").asBoolean()).as(i.get("code").asString()).isFalse();
      assertThat(i.get("label").asString()).isNotBlank();
    }
    assertThat(g.get("enforcement").asString()).contains("refuses live payments");
  }

  @Test
  void anyBillingManagerMayReadButOnlyTheOwnerRoleMayChangeIt() throws Exception {
    call(get(BASE + "/live-gate"), null, null).andExpect(status().isUnauthorized());
    call(get(BASE + "/live-gate"), who(sub()), null).andExpect(status().isForbidden());
    call(get(BASE + "/live-gate"), manager, null).andExpect(status().isForbidden());
    for (JwtRequestPostProcessor denied :
        new JwtRequestPostProcessor[] {null, who(sub()), manager, admin}) {
      int expected = denied == null ? 401 : 403;
      call(put(BASE + "/live-gate/items/legal_entity"), denied, "{\"done\":true}")
          .andExpect(status().is(expected));
      call(post(BASE + "/live-gate/ready"), denied, "{\"ready\":true}")
          .andExpect(status().is(expected));
      call(post(BASE + "/live-gate/ready"), denied, "{\"ready\":false}")
          .andExpect(status().is(expected));
    }
    assertThat(
            json(call(get(BASE + "/live-gate"), admin, null))
                .get("items")
                .get(0)
                .get("done")
                .asBoolean())
        .isFalse();
    tick("legal_entity", true).andExpect(status().isOk());
    call(put(BASE + "/live-gate/items/not_an_item"), owner, "{\"done\":true}")
        .andExpect(status().isNotFound());
    call(put(BASE + "/live-gate/items/legal_entity"), owner, "{}")
        .andExpect(status().isBadRequest());
  }

  @Test
  void itemsTheSystemCanCheckCannotBeTickedUntilTheyAreTrue() throws Exception {
    tick("tax_mode_confirmed", true).andExpect(status().isConflict());
    tick("policies_published", true).andExpect(status().isConflict());
    tick("seller_details_real", true).andExpect(status().isConflict());
    satisfyMachineChecks();
    tick("tax_mode_confirmed", true).andExpect(status().isOk());
    tick("policies_published", true).andExpect(status().isOk());
    tick("seller_details_real", true).andExpect(status().isOk());
    // pure attestations have no such check
    tick("legal_entity", true).andExpect(status().isOk());
    tick("live_keys_installed", true).andExpect(status().isOk());
    JsonNode g = json(call(get(BASE + "/live-gate"), admin, null));
    for (JsonNode i : g.get("items")) {
      if (i.get("code").asString().equals("legal_entity"))
        assertThat(i.path("check").isMissingNode()).isTrue();
      if (i.get("code").asString().equals("tax_mode_confirmed"))
        assertThat(i.get("check").get("ok").asBoolean()).isTrue();
    }
  }

  @Test
  void theGateOpensOnlyWhenEveryItemIsDoneAndEveryMachineCheckStillHolds() throws Exception {
    satisfyMachineChecks();
    for (int n = 0; n < ITEMS.length - 1; n++) tick(ITEMS[n], true).andExpect(status().isOk());
    JsonNode refused =
        json(
            call(post(BASE + "/live-gate/ready"), owner, "{\"ready\":true}")
                .andExpect(status().isBadRequest()));
    assertThat(refused.get("detail").asString()).contains("Test-mode end-to-end checklist");
    assertThat(json(call(get(BASE + "/live-gate"), owner, null)).get("liveReady").asBoolean())
        .isFalse();

    tick(ITEMS[ITEMS.length - 1], true).andExpect(status().isOk());
    // a machine check that stopped being true blocks it even though the item was ticked earlier
    jdbc.sql("update billing.refund_settings set policy_text = null where id = 1").update();
    JsonNode stale =
        json(
            call(post(BASE + "/live-gate/ready"), owner, "{\"ready\":true}")
                .andExpect(status().isBadRequest()));
    assertThat(stale.get("detail").asString()).contains("No refund policy is published yet");
    jdbc.sql(
            "update billing.refund_settings set policy_text = 'Written by the owner.' where id = 1")
        .update();

    JsonNode open =
        json(
            call(post(BASE + "/live-gate/ready"), owner, "{\"ready\":true}")
                .andExpect(status().isOk()));
    assertThat(open.get("liveReady").asBoolean()).isTrue();
    assertThat(open.get("allItemsDone").asBoolean()).isTrue();
    assertThat(
            jdbc.sql(
                    "select count(*) from admin.audit_logs where action = 'billing.live.ready.changed'")
                .query(Long.class)
                .single())
        .isGreaterThanOrEqualTo(1);
  }

  @Test
  void unTickingAnythingClosesTheGateAtOnceAndSwitchingOffIsAlwaysAllowed() throws Exception {
    satisfyMachineChecks();
    for (String code : ITEMS) tick(code, true).andExpect(status().isOk());
    call(post(BASE + "/live-gate/ready"), owner, "{\"ready\":true}").andExpect(status().isOk());
    JsonNode g = json(tick("webhook_verified_live", false).andExpect(status().isOk()));
    assertThat(g.get("liveReady").asBoolean()).isFalse();
    assertThat(g.get("allItemsDone").asBoolean()).isFalse();
    // switching off an already closed gate is harmless
    assertThat(
            json(call(post(BASE + "/live-gate/ready"), owner, "{\"ready\":false}")
                    .andExpect(status().isOk()))
                .get("liveReady")
                .asBoolean())
        .isFalse();
    // and opening again needs the item back
    call(post(BASE + "/live-gate/ready"), owner, "{\"ready\":true}")
        .andExpect(status().isBadRequest());
  }

  @Test
  void anOpenGateStillDoesNotLiftTheBuildsRefusalOfLiveMode() {
    // the gate is a recorded precondition, not the switch: live mode and live keys stay refused at
    // startup
    jdbc.sql("update billing.live_gate set live_ready = true where id = 1").update();
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () ->
                new RazorpayModeGuard(new RazorpayProperties("live", "rzp_live_abc", "s", "w"))
                    .check())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("whatever the Live Readiness Gate records");
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () ->
                new RazorpayModeGuard(new RazorpayProperties("test", "rzp_live_abc", "s", "w"))
                    .check())
        .isInstanceOf(IllegalStateException.class);
  }
}
