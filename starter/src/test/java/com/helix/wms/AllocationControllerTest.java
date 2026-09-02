package com.helix.wms;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AllocationControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    @DisplayName("T1: full allocation in a single bin marks order ALLOCATED and updates bin.quantity_reserved")
    void allocatesHappyPath() throws Exception {
        mockMvc.perform(post("/orders")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "orderId": "ORD-1",
                                  "lines": [
                                    {"lineId": "L1", "skuId": "SKU-RED-MUG", "quantity": 3}
                                  ]
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/orders/ORD-1/allocate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value("ORD-1"))
                .andExpect(jsonPath("$.status").value("ALLOCATED"))
                .andExpect(jsonPath("$.reservations.length()").value(1))
                .andExpect(jsonPath("$.reservations[0].lineId").value("L1"))
                .andExpect(jsonPath("$.reservations[0].binId").value("A-12-3"))
                .andExpect(jsonPath("$.reservations[0].quantity").value(3));

        mockMvc.perform(get("/orders/ORD-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ALLOCATED"))
                .andExpect(jsonPath("$.reservations.length()").value(1))
                .andExpect(jsonPath("$.reservations[0].quantityReserved").value(3));

        mockMvc.perform(get("/inventory/SKU-RED-MUG"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalReserved").value(3));
    }

    @Test
    @DisplayName("T2: a line larger than any single bin is split across bins")
    void splitsAcrossMultipleBins() throws Exception {
        mockMvc.perform(post("/orders")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "orderId": "ORD-2",
                                  "lines": [
                                    {"lineId": "L1", "skuId": "SKU-BLUE-PEN", "quantity": 10}
                                  ]
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/orders/ORD-2/allocate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ALLOCATED"))
                .andExpect(jsonPath("$.reservations.length()").value(2))
                .andExpect(jsonPath("$.reservations[0].binId").value("B-04-1"))
                .andExpect(jsonPath("$.reservations[0].quantity").value(7))
                .andExpect(jsonPath("$.reservations[1].binId").value("C-09-2"))
                .andExpect(jsonPath("$.reservations[1].quantity").value(3));

        mockMvc.perform(get("/inventory/SKU-BLUE-PEN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalReserved").value(10));
    }

    @Test
    @Disabled("T3")
    @DisplayName("T3: when full allocation is impossible, return 409 and reserve nothing")
    void rejectsPartialAllocationAtomically() {
    }

    @Test
    @Disabled("T4")
    @DisplayName("T2/T4: allocate is idempotent — calling twice does not double-reserve")
    void allocateIsIdempotent() {
    }

    @Test
    @Disabled("T4")
    @DisplayName("T4: concurrent allocations of the last unit — exactly one succeeds")
    void concurrentAllocationsDoNotDoubleReserve() {
    }
}
