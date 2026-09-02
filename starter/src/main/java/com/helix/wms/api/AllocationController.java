package com.helix.wms.api;

import com.helix.wms.api.dto.AllocateConflictResponse;
import com.helix.wms.api.dto.AllocateResponse;
import com.helix.wms.service.AllocationService;
import com.helix.wms.service.InsufficientInventoryException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/orders")
public class AllocationController {

    private final AllocationService allocation;

    public AllocationController(AllocationService allocation) {
        this.allocation = allocation;
    }

    @PostMapping("/{orderId}/allocate")
    public AllocateResponse allocate(@PathVariable String orderId) {
        return allocation.allocate(orderId);
    }

    @ExceptionHandler(InsufficientInventoryException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public AllocateConflictResponse handleInsufficientInventory(InsufficientInventoryException ex) {
        return ex.getResponse();
    }
}
