package com.helix.wms;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class InventoryControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void returnsPerBinBreakdownForRedMug() throws Exception {
        mockMvc.perform(get("/inventory/SKU-RED-MUG"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skuId").value("SKU-RED-MUG"))
                .andExpect(jsonPath("$.totalOnHand").value(7))
                .andExpect(jsonPath("$.totalReserved").value(0))
                .andExpect(jsonPath("$.totalAvailable").value(7))
                .andExpect(jsonPath("$.bins.length()").value(2));
    }

    @Test
    void returnsNotFoundForUnknownSku() throws Exception {
        mockMvc.perform(get("/inventory/SKU-NOPE"))
                .andExpect(status().isNotFound());
    }
}
