package com.helix.wms.domain;

public record Reservation(
        String reservationId,
        String orderId,
        String lineId,
        String binId,
        String skuId,
        int quantityReserved,
        int quantityPicked,
        ReservationStatus status
) {}
