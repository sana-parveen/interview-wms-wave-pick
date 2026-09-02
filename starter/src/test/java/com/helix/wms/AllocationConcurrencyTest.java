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
class AllocationConcurrencyTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void shrinkMugInventoryToOneUnit() {
        jdbc.update(
                "UPDATE bin_stock SET quantity_on_hand = 0, quantity_reserved = 0 " +
                        "WHERE bin_id = 'A-12-4' AND sku_id = 'SKU-RED-MUG'");
        jdbc.update(
                "UPDATE bin_stock SET quantity_on_hand = 1, quantity_reserved = 0 " +
                        "WHERE bin_id = 'A-12-3' AND sku_id = 'SKU-RED-MUG'");
    }

    @AfterEach
    void restoreSeedInventory() {
        jdbc.update("DELETE FROM audit_log");
        jdbc.update("DELETE FROM pick_events");
        jdbc.update("DELETE FROM reservations");
        jdbc.update("DELETE FROM order_lines");
        jdbc.update("DELETE FROM orders");

        jdbc.update(
                "UPDATE bin_stock SET quantity_on_hand = 5, quantity_reserved = 0 " +
                        "WHERE bin_id = 'A-12-3' AND sku_id = 'SKU-RED-MUG'");
        jdbc.update(
                "UPDATE bin_stock SET quantity_on_hand = 2, quantity_reserved = 0 " +
                        "WHERE bin_id = 'A-12-4' AND sku_id = 'SKU-RED-MUG'");
    }

    @Test
    @DisplayName("T4: concurrent allocations of the last unit — exactly one succeeds")
    void concurrentAllocationsDoNotDoubleReserve() throws Exception {
        createOrder("ORD-A");
        createOrder("ORD-B");

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();

        for (String orderId : new String[]{"ORD-A", "ORD-B"}) {
            executor.submit(() -> {
                try {
                    start.await();
                    int status = mockMvc.perform(post("/orders/" + orderId + "/allocate"))
                            .andReturn()
                            .getResponse()
                            .getStatus();
                    if (status == 200) {
                        successes.incrementAndGet();
                    } else if (status == 409) {
                        conflicts.incrementAndGet();
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

        assertEquals(1, successes.get());
        assertEquals(1, conflicts.get());

        mockMvc.perform(get("/inventory/SKU-RED-MUG"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalReserved").value(1));
    }

    private void createOrder(String orderId) throws Exception {
        mockMvc.perform(post("/orders")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "orderId": "%s",
                                  "lines": [
                                    {"lineId": "L1", "skuId": "SKU-RED-MUG", "quantity": 1}
                                  ]
                                }
                                """.formatted(orderId)))
                .andExpect(status().isCreated());
    }
}
