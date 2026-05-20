package com.helix.wms.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;

import java.util.List;

public record CreateOrderRequest(
        @NotBlank String orderId,
        @Valid @NotEmpty List<Line> lines
) {
    public record Line(
            @NotBlank String lineId,
            @NotBlank String skuId,
            @Positive int quantity
    ) {}
}
