package com.helix.wms.api;

import com.helix.wms.api.dto.AllocateResponse;
import com.helix.wms.service.AllocationService;
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
}
