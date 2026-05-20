package com.helix.wms.repository;

import com.helix.wms.api.dto.CreateOrderRequest;
import com.helix.wms.api.dto.OrderResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public class OrderRepository {

    private final JdbcTemplate jdbc;

    public OrderRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean orderExists(String orderId) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM orders WHERE order_id = ?",
                Integer.class, orderId);
        return n != null && n > 0;
    }

    public boolean skuExists(String skuId) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM skus WHERE sku_id = ?",
                Integer.class, skuId);
        return n != null && n > 0;
    }

    @Transactional
    public void createOrder(String orderId, List<CreateOrderRequest.Line> lines) {
        jdbc.update("INSERT INTO orders (order_id, status) VALUES (?, 'NEW')", orderId);
        for (var line : lines) {
            jdbc.update(
                    "INSERT INTO order_lines (order_id, line_id, sku_id, quantity_required) " +
                            "VALUES (?, ?, ?, ?)",
                    orderId, line.lineId(), line.skuId(), line.quantity());
        }
    }

    public Optional<OrderResponse> loadOrder(String orderId) {
        List<String[]> header = jdbc.query(
                "SELECT order_id, status FROM orders WHERE order_id = ?",
                (rs, n) -> new String[]{rs.getString("order_id"), rs.getString("status")},
                orderId);
        if (header.isEmpty()) {
            return Optional.empty();
        }

        List<OrderResponse.Line> lines = jdbc.query(
                "SELECT line_id, sku_id, quantity_required, quantity_picked " +
                        "FROM order_lines WHERE order_id = ? ORDER BY line_id",
                (rs, n) -> new OrderResponse.Line(
                        rs.getString("line_id"),
                        rs.getString("sku_id"),
                        rs.getInt("quantity_required"),
                        rs.getInt("quantity_picked")),
                orderId);

        List<OrderResponse.Reservation> reservations = jdbc.query(
                "SELECT reservation_id, line_id, bin_id, quantity_reserved, quantity_picked, status " +
                        "FROM reservations WHERE order_id = ? ORDER BY reservation_id",
                (rs, n) -> new OrderResponse.Reservation(
                        rs.getString("reservation_id"),
                        rs.getString("line_id"),
                        rs.getString("bin_id"),
                        rs.getInt("quantity_reserved"),
                        rs.getInt("quantity_picked"),
                        rs.getString("status")),
                orderId);

        return Optional.of(new OrderResponse(header.get(0)[0], header.get(0)[1], lines, reservations));
    }
}
