package com.engineerhub.shared.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import com.engineerhub.shared.id.Ids;
import com.engineerhub.testsupport.IntegrationTest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Flyway baseline, Hibernate schema validation and the idempotency primitive against a real
 * PostgreSQL 18.
 */
@IntegrationTest
class PersistenceIT {

  @Autowired JdbcClient jdbc;
  @Autowired IdempotencyService idempotency;
  @Autowired PlatformTransactionManager txManager;

  @Value("${spring.jpa.hibernate.ddl-auto}")
  String ddlAuto;

  @Value("${spring.flyway.clean-disabled}")
  boolean cleanDisabled;

  // ---- Flyway / schema ----

  @Test
  void flywayAppliedTheBaselineSuccessfully() {
    Integer applied =
        jdbc.sql("select count(*) from flyway_schema_history where success and version = '1'")
            .query(Integer.class)
            .single();
    assertThat(applied).isEqualTo(1);
  }

  @Test
  void ledgerTableHasTheDocumentedShape() {
    List<String> cols =
        jdbc.sql(
                "select column_name || ':' || data_type from information_schema.columns "
                    + "where table_schema = 'shared' and table_name = 'processed_messages' order by column_name")
            .query(String.class)
            .list();
    assertThat(cols)
        .containsExactly(
            "consumer:character varying",
            "message_id:uuid",
            "processed_at:timestamp with time zone");
  }

  @Test
  void hibernateOnlyValidatesAndFlywayCannotBeCleaned() {
    // Context start-up already proved the entity mapping matches the schema (ddl-auto=validate).
    assertThat(ddlAuto).isEqualTo("validate");
    assertThat(cleanDisabled).isTrue();
  }

  // ---- idempotency ----

  @Test
  void firstDeliveryIsTrueThenDuplicatesAreFalse() {
    UUID id = Ids.newId();
    assertThat(idempotency.firstDelivery("email-sender", id)).isTrue();
    assertThat(idempotency.firstDelivery("email-sender", id)).isFalse();
    assertThat(idempotency.firstDelivery("email-sender", id)).isFalse();
  }

  @Test
  void theSameMessageIsIndependentPerConsumer() {
    UUID id = Ids.newId();
    assertThat(idempotency.firstDelivery("analytics", id)).isTrue();
    assertThat(idempotency.firstDelivery("notifications", id)).isTrue();
  }

  @Test
  void exactlyOneOfManyConcurrentDeliveriesWins() throws Exception {
    UUID id = Ids.newId();
    int threads = 16;
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    try {
      List<Callable<Boolean>> tasks = new ArrayList<>();
      for (int i = 0; i < threads; i++) {
        tasks.add(() -> idempotency.firstDelivery("concurrent-consumer", id));
      }
      long winners = 0;
      for (Future<Boolean> f : pool.invokeAll(tasks)) {
        if (f.get()) {
          winners++;
        }
      }
      assertThat(winners).isEqualTo(1);
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void aRolledBackHandlerLeavesNoLedgerRowSoTheRetryIsProcessed() {
    UUID id = Ids.newId();
    TransactionTemplate tx = new TransactionTemplate(txManager);
    try {
      tx.executeWithoutResult(
          status -> {
            assertThat(idempotency.firstDelivery("flaky-consumer", id)).isTrue();
            throw new IllegalStateException("handler failed after marking");
          });
    } catch (IllegalStateException expected) {
      // transaction rolled back
    }
    assertThat(idempotency.firstDelivery("flaky-consumer", id)).isTrue(); // retry is processed
  }

  @Test
  void purgeRemovesOnlyOldLedgerRows() {
    UUID oldId = Ids.newId();
    UUID freshId = Ids.newId();
    jdbc.sql("insert into shared.processed_messages values ('purge-test', :id, :at)")
        .param("id", oldId)
        .param("at", java.sql.Timestamp.from(Instant.now().minus(Duration.ofDays(40))))
        .update();
    assertThat(idempotency.firstDelivery("purge-test", freshId)).isTrue();

    int removed = idempotency.purgeOlderThan(Duration.ofDays(30));

    assertThat(removed).isGreaterThanOrEqualTo(1);
    assertThat(idempotency.firstDelivery("purge-test", oldId)).isTrue(); // forgotten => new again
    assertThat(idempotency.firstDelivery("purge-test", freshId)).isFalse(); // retained
  }
}
