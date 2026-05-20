package com.helix.wms.domain;

import java.time.LocalDate;

public record BinStock(
        String binId,
        String skuId,
        int quantityOnHand,
        int quantityReserved,
        LocalDate receivedAt
) {
    public int available() {
        return quantityOnHand - quantityReserved;
    }
}
