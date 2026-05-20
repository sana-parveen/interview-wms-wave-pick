package com.helix.wms.domain;

import java.time.Instant;

public record PickEvent(
        String eventId,
        String clientEventId,
        String reservationId,
        String eventType,
        int quantity,
        String scannedBarcode,
        String reason,
        String pickerId,
        Instant at
) {}
