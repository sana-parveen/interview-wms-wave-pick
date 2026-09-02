package com.helix.wms;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PickEventControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    @DisplayName("T5: a successful pick decrements bin on_hand and increments reservation.quantity_picked")
    void happyPathPickDecrementsBinAndAdvancesReservation() throws Exception {
        String reservationId = allocateMugOrder("ORD-PICK-1", 3);

        mockMvc.perform(post("/pick-events")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "clientEventId": "scn-pick-001",
                                  "reservationId": "%s",
                                  "quantity": 3,
                                  "scannedBarcode": "SKU-RED-MUG",
                                  "pickerId": "USR-42",
                                  "at": "2026-05-13T09:14:22Z"
                                }
                                """.formatted(reservationId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservationStatus").value("PICKED"))
                .andExpect(jsonPath("$.quantityPicked").value(3))
                .andExpect(jsonPath("$.orderStatus").value("PICKED"));

        mockMvc.perform(get("/inventory/SKU-RED-MUG"))
                .andExpect(jsonPath("$.bins[?(@.binId=='A-12-3')].onHand").value(2))
                .andExpect(jsonPath("$.bins[?(@.binId=='A-12-3')].reserved").value(0))
                .andExpect(jsonPath("$.totalReserved").value(0));
    }

    @Test
    @DisplayName("T5: scanned_barcode that doesn't match the reservation's SKU is rejected")
    void rejectsBarcodeMismatch() throws Exception {
        String reservationId = allocateMugOrder("ORD-PICK-2", 3);

        mockMvc.perform(post("/pick-events")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "clientEventId": "scn-pick-002",
                                  "reservationId": "%s",
                                  "quantity": 1,
                                  "scannedBarcode": "SKU-BLUE-PEN",
                                  "pickerId": "USR-42"
                                }
                                """.formatted(reservationId)))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/inventory/SKU-RED-MUG"))
                .andExpect(jsonPath("$.totalReserved").value(3));
    }

    @Test
    @DisplayName("T5: pick quantity exceeding reservation is rejected")
    void rejectsOverpick() throws Exception {
        String reservationId = allocateMugOrder("ORD-PICK-3", 3);

        mockMvc.perform(post("/pick-events")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "clientEventId": "scn-pick-003",
                                  "reservationId": "%s",
                                  "quantity": 5,
                                  "scannedBarcode": "SKU-RED-MUG",
                                  "pickerId": "USR-42"
                                }
                                """.formatted(reservationId)))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/inventory/SKU-RED-MUG"))
                .andExpect(jsonPath("$.totalReserved").value(3));
    }

    @Test
    @DisplayName("T6: retried pick with same client_event_id does not double-decrement")
    void duplicateClientEventIdIsIdempotent() throws Exception {
        String reservationId = allocateMugOrder("ORD-PICK-4", 3);
        String body = """
                {
                  "clientEventId": "scn-pick-004",
                  "reservationId": "%s",
                  "quantity": 3,
                  "scannedBarcode": "SKU-RED-MUG",
                  "pickerId": "USR-42"
                }
                """.formatted(reservationId);

        mockMvc.perform(post("/pick-events").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        mockMvc.perform(post("/pick-events").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantityPicked").value(3));

        mockMvc.perform(get("/inventory/SKU-RED-MUG"))
                .andExpect(jsonPath("$.bins[?(@.binId=='A-12-3')].onHand").value(2))
                .andExpect(jsonPath("$.totalReserved").value(0));
    }

    @Test
    @DisplayName("T7: short-pick reduces reservation and follows your documented semantics")
    void shortPickSemantics() throws Exception {
        String reservationId = allocateMugOrder("ORD-SHORT-1", 3);

        mockMvc.perform(post("/pick-events/short-pick")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "clientEventId": "scn-short-001",
                                  "reservationId": "%s",
                                  "quantityShort": 1,
                                  "reason": "NOT_FOUND",
                                  "pickerId": "USR-42"
                                }
                                """.formatted(reservationId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantityPicked").value(2))
                .andExpect(jsonPath("$.reservationStatus").value("SHORT"))
                .andExpect(jsonPath("$.orderStatus").value("SHORT"));

        mockMvc.perform(get("/orders/ORD-SHORT-1"))
                .andExpect(jsonPath("$.status").value("SHORT"))
                .andExpect(jsonPath("$.lines[0].quantityPicked").value(2));

        mockMvc.perform(get("/inventory/SKU-RED-MUG"))
                .andExpect(jsonPath("$.bins[?(@.binId=='A-12-3')].onHand").value(3))
                .andExpect(jsonPath("$.bins[?(@.binId=='A-12-3')].reserved").value(0));
    }

    private String allocateMugOrder(String orderId, int quantity) throws Exception {
        mockMvc.perform(post("/orders")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "orderId": "%s",
                                  "lines": [
                                    {"lineId": "L1", "skuId": "SKU-RED-MUG", "quantity": %d}
                                  ]
                                }
                                """.formatted(orderId, quantity)))
                .andExpect(status().isCreated());

        MvcResult result = mockMvc.perform(post("/orders/" + orderId + "/allocate"))
                .andExpect(status().isOk())
                .andReturn();

        return com.jayway.jsonpath.JsonPath.read(
                result.getResponse().getContentAsString(),
                "$.reservations[0].reservationId");
    }
}
