package com.helix.wms.api.dto;

import java.util.List;

public record AllocateResponse(
        String orderId,
        String status,
        List<Reservation> reservations
) {
    public record Reservation(
            String reservationId,
            String lineId,
            String binId,
            int quantity
    ) {}
}
