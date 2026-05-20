package com.helix.wms.domain;

public record OrderLine(
        String orderId,
        String lineId,
        String skuId,
        int quantityRequired,
        int quantityPicked
) {}
