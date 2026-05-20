package com.helix.wms.api.dto;

import java.util.List;

public record InventoryResponse(
        String skuId,
        int totalOnHand,
        int totalReserved,
        int totalAvailable,
        List<Bin> bins
) {
    public record Bin(
            String binId,
            int onHand,
            int reserved,
            int available
    ) {}
}
