package com.helix.wms;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Reference tests for the pick-event endpoints (§4.3 and §4.4 of the brief).
 * Enable as you implement T5, T6, and T7.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Disabled("Enable as you complete T5–T7. See PickEventController.java for TODOs.")
class PickEventControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    @DisplayName("T5: a successful pick decrements bin on_hand and increments reservation.quantity_picked")
    void happyPathPickDecrementsBinAndAdvancesReservation() {
        // Setup: allocate an order so a reservation exists for 3× RED-MUG from A-12-3.
        // Action: POST /pick-events { reservation_id, quantity: 3, scanned_barcode: SKU-RED-MUG, ... }.
        // Expect:
        //   bin_stock.quantity_on_hand for A-12-3 dropped by 3,
        //   bin_stock.quantity_reserved for A-12-3 dropped by 3,
        //   reservation.status = PICKED, quantity_picked = 3,
        //   order.status = PICKED.
    }

    @Test
    @DisplayName("T5: scanned_barcode that doesn't match the reservation's SKU is rejected")
    void rejectsBarcodeMismatch() {
        // Allocate a reservation for RED-MUG; submit a pick event with scanned_barcode = SKU-BLUE-PEN.
        // Expect status 409 (or your chosen client-actionable error).
    }

    @Test
    @DisplayName("T5: pick quantity exceeding reservation is rejected")
    void rejectsOverpick() {
        // Allocate a reservation for 3 units; submit pick with quantity = 5.
        // Expect status 409. Reservation state unchanged.
    }

    @Test
    @DisplayName("T6: retried pick with same client_event_id does not double-decrement")
    void duplicateClientEventIdIsIdempotent() {
        // Submit the same pick twice with identical client_event_id.
        // Expect: both responses succeed (same body); the second causes NO additional decrement.
    }

    @Test
    @DisplayName("T6: concurrent retries of the same client_event_id apply exactly once")
    void concurrentDuplicateRetriesAreIdempotent() {
        // Issue 5 concurrent POST /pick-events with the same client_event_id.
        // Expect: bin_stock and reservation reflect a SINGLE pick; no exceptions leak as 5xx.
        //
        // Hint: an app-level "if exists then return" has a TOCTOU race.
        //       Lean on a DB constraint + catch-the-conflict pattern instead.
    }

    @Test
    @DisplayName("T7: short-pick reduces reservation and follows your documented semantics")
    void shortPickSemantics() {
        // Allocate 3× RED-MUG. Submit short-pick with quantity_short=1 (i.e., picker found 2).
        // Expect (per your documented semantics in the README): some combination of:
        //   - reservation.quantity_reserved adjusted (or stays + tracked separately),
        //   - bin_stock.quantity_on_hand adjusted (or NOT, depending on your model),
        //   - order moves toward SHORT or remains PICKING,
        //   - other reservations on the same bin appropriately flagged.
    }
}
