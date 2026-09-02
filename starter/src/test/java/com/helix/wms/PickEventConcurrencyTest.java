package com.helix.wms;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PickEventConcurrencyTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    private String reservationId;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.update("DELETE FROM pick_events");
        jdbc.update("DELETE FROM reservations");
        jdbc.update("DELETE FROM order_lines");
        jdbc.update("DELETE FROM orders");
        jdbc.update(
                "UPDATE bin_stock SET quantity_on_hand = 5, quantity_reserved = 0 " +
                        "WHERE bin_id = 'A-12-3' AND sku_id = 'SKU-RED-MUG'");

        mockMvc.perform(post("/orders")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "orderId": "ORD-CONC-PICK",
                                  "lines": [
                                    {"lineId": "L1", "skuId": "SKU-RED-MUG", "quantity": 3}
                                  ]
                                }
                                """))
                .andExpect(status().isCreated());

        MvcResult result = mockMvc.perform(post("/orders/ORD-CONC-PICK/allocate"))
                .andExpect(status().isOk())
                .andReturn();
        reservationId = com.jayway.jsonpath.JsonPath.read(
                result.getResponse().getContentAsString(),
                "$.reservations[0].reservationId");
    }

    @AfterEach
    void tearDown() {
        jdbc.update("DELETE FROM pick_events");
        jdbc.update("DELETE FROM reservations");
        jdbc.update("DELETE FROM order_lines");
        jdbc.update("DELETE FROM orders");
        jdbc.update(
                "UPDATE bin_stock SET quantity_on_hand = 5, quantity_reserved = 0 " +
                        "WHERE bin_id = 'A-12-3' AND sku_id = 'SKU-RED-MUG'");
    }

    @Test
    @DisplayName("T6: concurrent retries of the same client_event_id apply exactly once")
    void concurrentDuplicateRetriesAreIdempotent() throws Exception {
        String body = """
                {
                  "clientEventId": "scn-conc-001",
                  "reservationId": "%s",
                  "quantity": 3,
                  "scannedBarcode": "SKU-RED-MUG",
                  "pickerId": "USR-42"
                }
                """.formatted(reservationId);

        ExecutorService executor = Executors.newFixedThreadPool(5);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(5);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger serverErrors = new AtomicInteger();

        for (int i = 0; i < 5; i++) {
            executor.submit(() -> {
                try {
                    start.await();
                    int status = mockMvc.perform(post("/pick-events")
                                    .contentType(APPLICATION_JSON)
                                    .content(body))
                            .andReturn()
                            .getResponse()
                            .getStatus();
                    if (status == 200) {
                        successes.incrementAndGet();
                    } else if (status >= 500) {
                        serverErrors.incrementAndGet();
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        done.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals(0, serverErrors.get());
        assertEquals(5, successes.get());

        mockMvc.perform(get("/inventory/SKU-RED-MUG"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bins[?(@.binId=='A-12-3')].onHand").value(2))
                .andExpect(jsonPath("$.totalReserved").value(0));
    }
}
