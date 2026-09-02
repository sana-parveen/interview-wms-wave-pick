package com.helix.wms.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class AllocationRepository {

    private final JdbcTemplate jdbc;

    public AllocationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record OrderLineRow(String lineId, String skuId, int quantityRequired) {}

    public record BinStockRow(String binId, String skuId) {}

    public Optional<String> findOrderStatus(String orderId) {
        List<String> statuses = jdbc.query(
                "SELECT status FROM orders WHERE order_id = ?",
                (rs, n) -> rs.getString("status"),
                orderId);
        return statuses.stream().findFirst();
    }

    public List<OrderLineRow> loadOrderLines(String orderId) {
        return jdbc.query(
                "SELECT line_id, sku_id, quantity_required FROM order_lines " +
                        "WHERE order_id = ? ORDER BY line_id",
                (rs, n) -> new OrderLineRow(
                        rs.getString("line_id"),
                        rs.getString("sku_id"),
                        rs.getInt("quantity_required")),
                orderId);
    }

    /**
     * Returns the oldest bin (FIFO by received_at) with enough available stock
     * to satisfy the full line quantity in a single bin.
     */
    public Optional<BinStockRow> findBinForFullLine(String skuId, int quantity) {
        List<BinStockRow> bins = jdbc.query(
                "SELECT bin_id, sku_id FROM bin_stock " +
                        "WHERE sku_id = ? AND quantity_on_hand - quantity_reserved >= ? " +
                        "ORDER BY received_at ASC LIMIT 1",
                (rs, n) -> new BinStockRow(rs.getString("bin_id"), rs.getString("sku_id")),
                skuId, quantity);
        return bins.stream().findFirst();
    }

    public void incrementReserved(String binId, String skuId, int quantity) {
        int updated = jdbc.update(
                "UPDATE bin_stock SET quantity_reserved = quantity_reserved + ? " +
                        "WHERE bin_id = ? AND sku_id = ? " +
                        "AND quantity_on_hand - quantity_reserved >= ?",
                quantity, binId, skuId, quantity);
        if (updated != 1) {
            throw new IllegalStateException(
                    "failed to reserve " + quantity + " in bin " + binId);
        }
    }

    public void insertReservation(
            String reservationId,
            String orderId,
            String lineId,
            String binId,
            String skuId,
            int quantity) {
        jdbc.update(
                "INSERT INTO reservations " +
                        "(reservation_id, order_id, line_id, bin_id, sku_id, " +
                        "quantity_reserved, quantity_picked, status) " +
                        "VALUES (?, ?, ?, ?, ?, ?, 0, 'OPEN')",
                reservationId, orderId, lineId, binId, skuId, quantity);
    }

    public void updateOrderStatus(String orderId, String status) {
        jdbc.update("UPDATE orders SET status = ? WHERE order_id = ?", status, orderId);
    }
}
