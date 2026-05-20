package com.helix.wms.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

public record ShortPickRequest(
        @NotBlank String clientEventId,
        @NotBlank String reservationId,
        @Positive int quantityShort,
        @NotBlank @Pattern(regexp = "NOT_FOUND|DAMAGED|WRONG_ITEM") String reason,
        @NotBlank String pickerId
) {}
