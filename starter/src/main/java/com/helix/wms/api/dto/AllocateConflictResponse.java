package com.helix.wms.api.dto;

import java.util.List;

public record AllocateConflictResponse(
        String orderId,
        String message,
        List<LineDetail> lines
) {
    public record LineDetail(
            String lineId,
            String skuId,
            int quantityRequired,
            int quantityAvailable,
            List<BinDetail> couldReserve
    ) {}

    public record BinDetail(
            String binId,
            int quantity
    ) {}
}
