package com.helix.wms.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public class AuditRepository {

    private final JdbcTemplate jdbc;

    public AuditRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record AuditRow(
            String auditId,
            String eventType,
            String entityType,
            String entityId,
            String detail,
            String occurredAt) {}

    public void append(
            String orderId,
            String entityType,
            String entityId,
            String eventType,
            String detail) {
        jdbc.update(
                "INSERT INTO audit_log (audit_id, order_id, entity_type, entity_id, event_type, detail, occurred_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?)",
                "AUD-" + UUID.randomUUID().toString().substring(0, 8),
                orderId,
                entityType,
                entityId,
                eventType,
                detail,
                Timestamp.from(Instant.now()));
    }

    public List<AuditRow> findByOrderId(String orderId) {
        return jdbc.query(
                "SELECT audit_id, event_type, entity_type, entity_id, detail, " +
                        "CAST(occurred_at AS VARCHAR) AS occurred_at " +
                        "FROM audit_log WHERE order_id = ? ORDER BY occurred_at ASC, audit_id ASC",
                (rs, n) -> new AuditRow(
                        rs.getString("audit_id"),
                        rs.getString("event_type"),
                        rs.getString("entity_type"),
                        rs.getString("entity_id"),
                        rs.getString("detail"),
                        rs.getString("occurred_at")),
                orderId);
    }
}
