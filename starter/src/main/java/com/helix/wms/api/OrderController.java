package com.helix.wms.api;

import com.helix.wms.api.dto.CreateOrderRequest;
import com.helix.wms.api.dto.OrderResponse;
import com.helix.wms.repository.OrderRepository;
import jakarta.validation.Valid;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderRepository orders;

    public OrderController(OrderRepository orders) {
        this.orders = orders;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse create(@Valid @RequestBody CreateOrderRequest req) {
        for (var line : req.lines()) {
            if (!orders.skuExists(line.skuId())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "unknown sku: " + line.skuId());
            }
        }
        try {
            orders.createOrder(req.orderId(), req.lines());
        } catch (DuplicateKeyException e) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "order already exists: " + req.orderId());
        }
        return orders.loadOrder(req.orderId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "order disappeared after insert"));
    }

    @GetMapping("/{orderId}")
    public OrderResponse get(@PathVariable String orderId) {
        return orders.loadOrder(orderId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "order not found: " + orderId));
    }
}
