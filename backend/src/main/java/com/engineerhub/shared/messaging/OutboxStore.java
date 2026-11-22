package com.engineerhub.shared.messaging;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * SQL access to {@code shared.outbox}. Plain JDBC: it needs {@code FOR UPDATE SKIP LOCKED} and
 * jsonb.
 */
@Repository
class OutboxStore {

  private final JdbcClient jdbc;

  OutboxStore(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  void insert(OutboxRow row) {
    jdbc.sql(
            """
            insert into shared.outbox
              (id, event_type, event_version, aggregate_type, aggregate_id, payload,
               correlation_id, occurred_at, created_at)
            values (:id, :type, :version, :aggType, :aggId, cast(:payload as jsonb),
                    :corr, :occurredAt, :createdAt)
            """)
        .param("id", row.id())
        .param("type", row.eventType())
        .param("version", row.eventVersion())
        .param("aggType", row.aggregateType())
        .param("aggId", row.aggregateId())
        .param("payload", row.payloadJson())
        .param("corr", row.correlationId())
        .param("occurredAt", Timestamp.from(row.occurredAt()))
        .param("createdAt", Timestamp.from(row.createdAt()))
        .update();
  }

  /**
   * Locks up to {@code limit} pending rows, oldest first. {@code SKIP LOCKED} means concurrent
   * relays (several instances, or overlapping runs) take disjoint rows instead of blocking or
   * double-publishing. Must run inside the transaction that later marks the rows published.
   */
  List<OutboxRow> lockPending(int limit) {
    return jdbc.sql(
            """
            select id, event_type, event_version, aggregate_type, aggregate_id,
                   payload::text as payload, correlation_id, occurred_at, created_at
            from shared.outbox
            where published_at is null
            order by created_at, id
            limit :limit
            for update skip locked
            """)
        .param("limit", limit)
        .query(
            (rs, i) ->
                new OutboxRow(
                    rs.getObject("id", UUID.class),
                    rs.getString("event_type"),
                    rs.getInt("event_version"),
                    rs.getString("aggregate_type"),
                    rs.getString("aggregate_id"),
                    rs.getString("payload"),
                    rs.getString("correlation_id"),
                    rs.getTimestamp("occurred_at").toInstant(),
                    rs.getTimestamp("created_at").toInstant()))
        .list();
  }

  void markPublished(List<UUID> ids, Instant at) {
    if (ids.isEmpty()) {
      return;
    }
    jdbc.sql("update shared.outbox set published_at = :at, last_error = null where id in (:ids)")
        .param("at", Timestamp.from(at))
        .param("ids", ids)
        .update();
  }

  void recordFailure(UUID id, String sanitizedError) {
    jdbc.sql("update shared.outbox set attempts = attempts + 1, last_error = :err where id = :id")
        .param("err", sanitizedError)
        .param("id", id)
        .update();
  }

  int deletePublishedBefore(Instant cutoff) {
    return jdbc.sql(
            "delete from shared.outbox where published_at is not null and published_at < :cutoff")
        .param("cutoff", Timestamp.from(cutoff))
        .update();
  }

  long countPending() {
    return jdbc.sql("select count(*) from shared.outbox where published_at is null")
        .query(Long.class)
        .single();
  }
}
