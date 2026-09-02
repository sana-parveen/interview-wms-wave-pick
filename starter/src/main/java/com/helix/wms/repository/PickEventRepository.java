package com.helix.wms.repository;

import com.helix.wms.api.dto.PickEventResponse;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class PickEventRepository {

    private final JdbcTemplate jdbc;

    public PickEventRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record ReservationRow(
            String reservationId,
            String orderId,
            String lineId,
            String binId,
            String skuId,
            int quantityReserved,
            int quantityPicked,
            String status) {

        public ReservationRow withPick(int quantityPicked, String status) {
            return new ReservationRow(
                    reservationId, orderId, lineId, binId, skuId,
                    quantityReserved, quantityPicked, status);
        }
    }

    public Optional<ReservationRow> lockReservationForUpdate(String reservationId) {
        List<ReservationRow> rows = jdbc.query(
                "SELECT reservation_id, order_id, line_id, bin_id, sku_id, " +
                        "quantity_reserved, quantity_picked, status " +
                        "FROM reservations WHERE reservation_id = ? FOR UPDATE",
                (rs, n) -> new ReservationRow(
                        rs.getString("reservation_id"),
                        rs.getString("order_id"),
                        rs.getString("line_id"),
                        rs.getString("bin_id"),
                        rs.getString("sku_id"),
                        rs.getInt("quantity_reserved"),
                        rs.getInt("quantity_picked"),
                        rs.getString("status")),
                reservationId);
        return rows.stream().findFirst();
    }

    public boolean tryInsertPickEvent(
            String eventId,
            String clientEventId,
            String reservationId,
            String eventType,
            int quantity,
            String scannedBarcode,
            String reason,
            String pickerId,
            Instant at) {
        try {
            jdbc.update(
                    "INSERT INTO pick_events " +
                            "(event_id, client_event_id, reservation_id, event_type, quantity, " +
                            "scanned_barcode, reason, picker_id, at) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    eventId, clientEventId, reservationId, eventType, quantity,
                    scannedBarcode, reason, pickerId, at);
            return true;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }

    public Optional<String> findReservationIdByClientEventId(String clientEventId) {
        List<String> ids = jdbc.query(
                "SELECT reservation_id FROM pick_events WHERE client_event_id = ?",
                (rs, n) -> rs.getString("reservation_id"),
                clientEventId);
        return ids.stream().findFirst();
    }

    public boolean applyBinPick(String binId, String skuId, int quantity) {
        int updated = jdbc.update(
                "UPDATE bin_stock SET " +
                        "quantity_on_hand = quantity_on_hand - ?, " +
                        "quantity_reserved = quantity_reserved - ? " +
                        "WHERE bin_id = ? AND sku_id = ? " +
                        "AND quantity_on_hand >= ? AND quantity_reserved >= ?",
                quantity, quantity, binId, skuId, quantity, quantity);
        return updated == 1;
    }

    public boolean applyBinShortPick(String binId, String skuId, int foundQuantity, int reservedRelease) {
        int updated = jdbc.update(
                "UPDATE bin_stock SET " +
                        "quantity_on_hand = quantity_on_hand - ?, " +
                        "quantity_reserved = quantity_reserved - ? " +
                        "WHERE bin_id = ? AND sku_id = ? " +
                        "AND quantity_on_hand >= ? AND quantity_reserved >= ?",
                foundQuantity, reservedRelease, binId, skuId, foundQuantity, reservedRelease);
        return updated == 1;
    }

    public void updateReservationPicked(String reservationId, int quantityPicked, String status) {
        jdbc.update(
                "UPDATE reservations SET quantity_picked = ?, status = ? WHERE reservation_id = ?",
                quantityPicked, status, reservationId);
    }

    public void updateOrderLinePicked(String orderId, String lineId, int quantityPicked) {
        jdbc.update(
                "UPDATE order_lines SET quantity_picked = ? WHERE order_id = ? AND line_id = ?",
                quantityPicked, orderId, lineId);
    }

    public void updateOrderStatus(String orderId, String status) {
        jdbc.update("UPDATE orders SET status = ? WHERE order_id = ?", status, orderId);
    }

    public int countOpenReservations(String orderId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM reservations " +
                        "WHERE order_id = ? AND quantity_picked < quantity_reserved",
                Integer.class,
                orderId);
        return count != null ? count : 0;
    }

    public boolean isOrderFullyPicked(String orderId) {
        Integer incomplete = jdbc.queryForObject(
                "SELECT COUNT(*) FROM order_lines " +
                        "WHERE order_id = ? AND quantity_picked < quantity_required",
                Integer.class,
                orderId);
        return incomplete != null && incomplete == 0;
    }

    public boolean hasShortReservation(String orderId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM reservations WHERE order_id = ? AND status = 'SHORT'",
                Integer.class,
                orderId);
        return count != null && count > 0;
    }

    public int getOrderLineQuantityPicked(String orderId, String lineId) {
        return jdbc.queryForObject(
                "SELECT quantity_picked FROM order_lines WHERE order_id = ? AND line_id = ?",
                Integer.class,
                orderId, lineId);
    }

    public PickEventResponse buildResponse(String clientEventId, ReservationRow reservation) {
        String orderStatus = jdbc.queryForObject(
                "SELECT status FROM orders WHERE order_id = ?",
                String.class,
                reservation.orderId());
        return new PickEventResponse(
                clientEventId,
                reservation.reservationId(),
                reservation.quantityPicked(),
                reservation.quantityReserved(),
                reservation.status(),
                reservation.orderId(),
                orderStatus);
    }

    public PickEventResponse buildResponseForClientEvent(String clientEventId) {
        String reservationId = findReservationIdByClientEventId(clientEventId)
                .orElseThrow();
        ReservationRow reservation = jdbc.query(
                "SELECT reservation_id, order_id, line_id, bin_id, sku_id, " +
                        "quantity_reserved, quantity_picked, status " +
                        "FROM reservations WHERE reservation_id = ?",
                (rs, n) -> new ReservationRow(
                        rs.getString("reservation_id"),
                        rs.getString("order_id"),
                        rs.getString("line_id"),
                        rs.getString("bin_id"),
                        rs.getString("sku_id"),
                        rs.getInt("quantity_reserved"),
                        rs.getInt("quantity_picked"),
                        rs.getString("status")),
                reservationId).getFirst();
        return buildResponse(clientEventId, reservation);
    }
}
