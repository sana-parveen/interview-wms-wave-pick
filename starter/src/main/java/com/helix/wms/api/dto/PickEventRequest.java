package com.helix.wms.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import java.time.Instant;

public record PickEventRequest(
        @NotBlank String clientEventId,
        @NotBlank String reservationId,
        @Positive int quantity,
        @NotBlank String scannedBarcode,
        @NotBlank String pickerId,
        Instant at
) {}
