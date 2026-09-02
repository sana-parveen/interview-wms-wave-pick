package com.helix.wms.api.dto;

import java.util.List;

public record OrderResponse(
        String orderId,
        String status,
        List<Line> lines,
        List<Reservation> reservations,
        List<AuditEntry> auditTrail
) {
    public record Line(
            String lineId,
            String skuId,
            int quantityRequired,
            int quantityPicked
    ) {}

    public record Reservation(
            String reservationId,
            String lineId,
            String binId,
            int quantityReserved,
            int quantityPicked,
            String status
    ) {}

    public record AuditEntry(
            String eventType,
            String entityType,
            String entityId,
            String detail,
            String occurredAt
    ) {}
}
