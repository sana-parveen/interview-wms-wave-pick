package com.helix.wms.api;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/orders")
public class AllocationController {

    @PostMapping("/{orderId}/allocate")
    public Object allocate(@PathVariable String orderId) {
        // ── TODO T1 ──────────────────────────────────────────────────────────
        // Implement the happy path: for an order whose lines can each be
        // satisfied entirely from a single bin, create reservations, increment
        // bin_stock.quantity_reserved, and transition the order to ALLOCATED.
        // Document your bin-selection strategy in the README.
        //
        // ── TODO T2 ──────────────────────────────────────────────────────────
        // Extend to split a single line across multiple bins when no one bin
        // has enough.
        //
        // ── TODO T3 ──────────────────────────────────────────────────────────
        // If full allocation across all bins is impossible, return 409 with a
        // body showing what *could* be reserved. Persist nothing.
        //
        // ── TODO T4 ──────────────────────────────────────────────────────────
        // Make this safe under concurrent requests. Two callers racing for the
        // last unit must not both succeed; bin_stock.quantity_reserved must
        // never exceed quantity_on_hand.
        //
        // Contract: 01_candidate_take_home.md §4.2
        // Invariants: §5.1, §5.2
        throw new ResponseStatusException(
                HttpStatus.NOT_IMPLEMENTED,
                "allocate not implemented yet — see TODOs in AllocationController.java");
    }
}
