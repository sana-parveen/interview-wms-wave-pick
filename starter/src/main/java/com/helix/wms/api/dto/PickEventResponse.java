package com.helix.wms.api.dto;

public record PickEventResponse(
        String clientEventId,
        String reservationId,
        int quantityPicked,
        int quantityReserved,
        String reservationStatus,
        String orderId,
        String orderStatus
) {}
