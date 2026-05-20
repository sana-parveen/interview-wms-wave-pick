package com.helix.wms.api;

import com.helix.wms.api.dto.PickEventRequest;
import com.helix.wms.api.dto.ShortPickRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/pick-events")
public class PickEventController {

    @PostMapping
    public Object pick(@Valid @RequestBody PickEventRequest req) {
        // ── TODO T5 ──────────────────────────────────────────────────────────
        // Happy path: decrement bin_stock.quantity_on_hand AND
        // bin_stock.quantity_reserved by req.quantity(); increment
        // reservation.quantity_picked. Reject if scanned_barcode doesn't match
        // the reservation's expected SKU (409). Reject overpick (409).
        // When all reservations on an order are fully picked, transition the
        // order to PICKED.
        //
        // ── TODO T6 ──────────────────────────────────────────────────────────
        // Idempotency: dedup by client_event_id. A retry must not cause a
        // second decrement. Your solution must work when two retries arrive
        // concurrently (think DB-level constraints, not app-level checks).
        //
        // ── TODO T8 ──────────────────────────────────────────────────────────
        // Emit an audit event so we can answer "what happened?" later.
        //
        // Contract: 01_candidate_take_home.md §4.3
        // Invariants: §5.3, §5.4, §5.5
        throw new ResponseStatusException(
                HttpStatus.NOT_IMPLEMENTED,
                "pick not implemented yet — see TODOs in PickEventController.java");
    }

    @PostMapping("/short-pick")
    public Object shortPick(@Valid @RequestBody ShortPickRequest req) {
        // ── TODO T7 ──────────────────────────────────────────────────────────
        // Decide and DOCUMENT in your README what a short-pick does to:
        //   (a) the reservation,
        //   (b) the bin's quantity_on_hand,
        //   (c) the order (status?),
        //   (d) any other reservations on the same bin.
        // There is no single right answer — show your reasoning.
        //
        // ── TODO T8 ──────────────────────────────────────────────────────────
        // Emit an audit event.
        //
        // Contract: 01_candidate_take_home.md §4.4
        throw new ResponseStatusException(
                HttpStatus.NOT_IMPLEMENTED,
                "short-pick not implemented yet — see TODOs in PickEventController.java");
    }
}
