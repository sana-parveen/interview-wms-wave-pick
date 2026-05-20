package com.helix.wms;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OrderControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void createsOrderInNewStatus() throws Exception {
        mockMvc.perform(post("/orders")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "orderId": "ORD-1",
                                  "lines": [
                                    {"lineId": "L1", "skuId": "SKU-RED-MUG", "quantity": 2}
                                  ]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").value("ORD-1"))
                .andExpect(jsonPath("$.status").value("NEW"))
                .andExpect(jsonPath("$.lines[0].lineId").value("L1"))
                .andExpect(jsonPath("$.lines[0].quantityRequired").value(2))
                .andExpect(jsonPath("$.lines[0].quantityPicked").value(0))
                .andExpect(jsonPath("$.reservations").isEmpty());
    }

    @Test
    void rejectsUnknownSku() throws Exception {
        mockMvc.perform(post("/orders")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "orderId": "ORD-X",
                                  "lines": [
                                    {"lineId": "L1", "skuId": "SKU-DOES-NOT-EXIST", "quantity": 1}
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsDuplicateOrderId() throws Exception {
        String body = """
                {
                  "orderId": "ORD-DUP",
                  "lines": [
                    {"lineId": "L1", "skuId": "SKU-RED-MUG", "quantity": 1}
                  ]
                }
                """;

        mockMvc.perform(post("/orders").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/orders").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }
}
