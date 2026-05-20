package com.helix.wms;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Reference tests for the allocation endpoint (§4.2 of the brief).
 *
 * Each test is disabled. Remove the @Disabled (or rewrite the test) as you
 * implement each TODO in AllocationController.
 *
 * The class-level @Disabled is a safety net so the build stays green from the
 * start; remove it once you start working in here.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Disabled("Enable as you complete T1–T4. See AllocationController.java for TODOs.")
class AllocationControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    @DisplayName("T1: full allocation in a single bin marks order ALLOCATED and updates bin.quantity_reserved")
    void allocatesHappyPath() {
        // Setup: create order ORD-1 requiring 3× SKU-RED-MUG.
        // Action: POST /orders/ORD-1/allocate.
        // Expect:
        //   status 200,
        //   GET /orders/ORD-1 → status "ALLOCATED" with one reservation of qty=3,
        //   GET /inventory/SKU-RED-MUG → totalReserved = 3.
    }

    @Test
    @DisplayName("T2: a line larger than any single bin is split across bins")
    void splitsAcrossMultipleBins() {
        // Setup: create order ORD-2 requiring 10× SKU-BLUE-PEN
        // (seed: B-04-1 has 7, C-09-2 has 4; total 11 available).
        // Expect:
        //   status 200,
        //   reservations sum to 10 across both bins,
        //   the split matches the allocation strategy you documented in README.
    }

    @Test
    @DisplayName("T3: when full allocation is impossible, return 409 and reserve nothing")
    void rejectsPartialAllocationAtomically() {
        // Setup: create order ORD-3 requiring 100× SKU-RED-MUG (only 7 available).
        // Expect:
        //   status 409,
        //   body indicates what *could* be reserved (7 across two bins),
        //   bin_stock.quantity_reserved for both mug bins is unchanged (still 0),
        //   GET /orders/ORD-3 → status remains "NEW".
    }

    @Test
    @DisplayName("T2/T4: allocate is idempotent — calling twice does not double-reserve")
    void allocateIsIdempotent() {
        // Setup: create order ORD-4 requiring 3× RED-MUG. Call allocate twice.
        // Expect:
        //   both responses report the same reservations,
        //   GET /inventory/SKU-RED-MUG → totalReserved = 3 (not 6).
    }

    @Test
    @DisplayName("T4: concurrent allocations of the last unit — exactly one succeeds")
    void concurrentAllocationsDoNotDoubleReserve() {
        // Setup: shrink inventory so only ONE RED-MUG is available across all bins
        // (e.g., UPDATE bin_stock SET quantity_on_hand = 0 for A-12-4 and = 1 for A-12-3).
        // Create two orders ORD-A and ORD-B, each requiring 1× RED-MUG.
        //
        // Action: issue both POST /orders/{id}/allocate requests concurrently
        //         (ExecutorService + CountDownLatch is the usual pattern).
        //
        // Expect:
        //   exactly one returns 200, the other returns 409,
        //   bin_stock.quantity_reserved == 1 at the end (not 2),
        //   only the winning order has a reservation.
        //
        // NOTE: this test cannot rely on @Transactional rollback (two threads).
        //       Reset state explicitly in @AfterEach.
    }
}
