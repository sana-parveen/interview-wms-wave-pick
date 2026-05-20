package com.helix.wms.domain;

import java.time.Instant;

public record Order(
        String orderId,
        OrderStatus status,
        Instant createdAt
) {}
