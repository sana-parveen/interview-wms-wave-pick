package com.helix.wms;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class InvariantTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    @DisplayName("§5.1: quantity_on_hand never goes negative under any sequence")
    void onHandNeverNegative() throws Exception {
        String reservationId = createAndAllocate("ORD-INV-1", 3);

        mockMvc.perform(post("/pick-events")
                        .contentType(APPLICATION_JSON)
                        .content(pickBody("scn-inv-1", reservationId, 2)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/pick-events/short-pick")
                        .contentType(APPLICATION_JSON)
                        .content(shortPickBody("scn-inv-2", reservationId, 1)))
                .andExpect(status().isOk());

        assertBinInvariantsHold();
    }

    @Test
    @DisplayName("§5.2: quantity_reserved never exceeds quantity_on_hand")
    void reservedNeverExceedsOnHand() throws Exception {
        String reservationId = createAndAllocate("ORD-INV-2", 3);

        mockMvc.perform(post("/pick-events")
                        .contentType(APPLICATION_JSON)
                        .content(pickBody("scn-inv-3", reservationId, 1)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/pick-events/short-pick")
                        .contentType(APPLICATION_JSON)
                        .content(shortPickBody("scn-inv-4", reservationId, 1)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/orders")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "orderId": "ORD-INV-3",
                                  "lines": [
                                    {"lineId": "L1", "skuId": "SKU-RED-MUG", "quantity": 1}
                                  ]
                                }
                                """))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/orders/ORD-INV-3/allocate"))
                .andExpect(status().isOk());

        assertBinInvariantsHold();
    }

    @Test
    @DisplayName("§5.5: audit trail can reconstruct what happened to a unit")
    void auditTrailIsReconstructable() throws Exception {
        String reservationId = createAndAllocate("ORD-INV-4", 3);

        mockMvc.perform(post("/pick-events")
                        .contentType(APPLICATION_JSON)
                        .content(pickBody("scn-inv-5", reservationId, 2)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/pick-events/short-pick")
                        .contentType(APPLICATION_JSON)
                        .content(shortPickBody("scn-inv-6", reservationId, 1)))
                .andExpect(status().isOk());

        MvcResult result = mockMvc.perform(get("/orders/ORD-INV-4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.auditTrail.length()").value(greaterThanOrEqualTo(6)))
                .andReturn();

        String content = result.getResponse().getContentAsString();
        @SuppressWarnings("unchecked")
        java.util.List<java.util.Map<String, Object>> audit =
                com.jayway.jsonpath.JsonPath.read(content, "$.auditTrail");
        java.util.List<String> eventTypes = audit.stream()
                .map(entry -> (String) entry.get("eventType"))
                .toList();
        assertTrue(eventTypes.contains("ORDER_CREATED"));
        assertTrue(eventTypes.contains("ORDER_ALLOCATED"));
        assertTrue(eventTypes.contains("RESERVATION_OPENED"));
        assertTrue(eventTypes.contains("BIN_RESERVED"));
        assertTrue(eventTypes.contains("PICK_APPLIED"));
        assertTrue(eventTypes.contains("SHORT_PICK_APPLIED"));

        String pickDetail = audit.stream()
                .filter(entry -> "PICK_APPLIED".equals(entry.get("eventType")))
                .map(entry -> (String) entry.get("detail"))
                .findFirst()
                .orElse("");
        String shortPickDetail = audit.stream()
                .filter(entry -> "SHORT_PICK_APPLIED".equals(entry.get("eventType")))
                .map(entry -> (String) entry.get("detail"))
                .findFirst()
                .orElse("");
        assertTrue(pickDetail.contains("A-12-3"));
        assertTrue(shortPickDetail.contains("NOT_FOUND"));
        assertTrue(eventTypes.indexOf("ORDER_CREATED") < eventTypes.indexOf("PICK_APPLIED"));
        assertTrue(eventTypes.indexOf("PICK_APPLIED") < eventTypes.indexOf("SHORT_PICK_APPLIED"));
    }

    private String createAndAllocate(String orderId, int quantity) throws Exception {
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

    private String pickBody(String clientEventId, String reservationId, int quantity) {
        return """
                {
                  "clientEventId": "%s",
                  "reservationId": "%s",
                  "quantity": %d,
                  "scannedBarcode": "SKU-RED-MUG",
                  "pickerId": "USR-42"
                }
                """.formatted(clientEventId, reservationId, quantity);
    }

    private String shortPickBody(String clientEventId, String reservationId, int quantityShort) {
        return """
                {
                  "clientEventId": "%s",
                  "reservationId": "%s",
                  "quantityShort": %d,
                  "reason": "NOT_FOUND",
                  "pickerId": "USR-42"
                }
                """.formatted(clientEventId, reservationId, quantityShort);
    }

    private void assertBinInvariantsHold() {
        Integer violations = jdbc.queryForObject(
                "SELECT COUNT(*) FROM bin_stock " +
                        "WHERE quantity_on_hand < 0 OR quantity_reserved < 0 " +
                        "OR quantity_reserved > quantity_on_hand",
                Integer.class);
        assertTrue(violations != null && violations == 0);
    }
}
