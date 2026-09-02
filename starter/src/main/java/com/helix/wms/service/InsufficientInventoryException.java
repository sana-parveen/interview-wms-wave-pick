package com.helix.wms.service;

import com.helix.wms.api.dto.AllocateConflictResponse;

public class InsufficientInventoryException extends RuntimeException {

    private final AllocateConflictResponse response;

    public InsufficientInventoryException(AllocateConflictResponse response) {
        super(response.message());
        this.response = response;
    }

    public AllocateConflictResponse getResponse() {
        return response;
    }
}
