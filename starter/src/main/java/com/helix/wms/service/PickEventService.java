package com.helix.wms.service;

import com.helix.wms.api.dto.PickEventRequest;
import com.helix.wms.api.dto.PickEventResponse;
import com.helix.wms.api.dto.ShortPickRequest;
import com.helix.wms.repository.AuditRepository;
import com.helix.wms.repository.PickEventRepository;
import com.helix.wms.repository.PickEventRepository.ReservationRow;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;

@Service
public class PickEventService {

    private final PickEventRepository picks;
    private final AuditRepository audit;

    public PickEventService(PickEventRepository picks, AuditRepository audit) {
        this.picks = picks;
        this.audit = audit;
    }

    @Transactional
    public PickEventResponse pick(PickEventRequest req) {
        if (picks.findReservationIdByClientEventId(req.clientEventId()).isPresent()) {
            return picks.buildResponseForClientEvent(req.clientEventId());
        }

        ReservationRow reservation = picks.lockReservationForUpdate(req.reservationId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "reservation not found: " + req.reservationId()));

        if (picks.findReservationIdByClientEventId(req.clientEventId()).isPresent()) {
            return picks.buildResponseForClientEvent(req.clientEventId());
        }

        validateBarcode(req.scannedBarcode(), reservation.skuId());
        validatePickQuantity(req.quantity(), reservation);

        Instant at = req.at() != null ? req.at() : Instant.now();
        String eventId = "EVT-" + UUID.randomUUID().toString().substring(0, 8);
        if (!picks.tryInsertPickEvent(
                eventId,
                req.clientEventId(),
                req.reservationId(),
                "PICK",
                req.quantity(),
                req.scannedBarcode(),
                null,
                req.pickerId(),
                at)) {
            return picks.buildResponseForClientEvent(req.clientEventId());
        }

        if (!picks.applyBinPick(reservation.binId(), reservation.skuId(), req.quantity())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "failed to apply pick to bin: " + reservation.binId());
        }

        int newQuantityPicked = reservation.quantityPicked() + req.quantity();
        String reservationStatus = newQuantityPicked == reservation.quantityReserved()
                ? "PICKED" : "OPEN";
        picks.updateReservationPicked(req.reservationId(), newQuantityPicked, reservationStatus);

        int linePicked = picks.getOrderLineQuantityPicked(reservation.orderId(), reservation.lineId())
                + req.quantity();
        picks.updateOrderLinePicked(reservation.orderId(), reservation.lineId(), linePicked);

        refreshOrderStatus(reservation.orderId());

        audit.append(reservation.orderId(), "RESERVATION", req.reservationId(), "PICK_APPLIED",
                "{\"binId\":\"" + reservation.binId() + "\",\"skuId\":\"" + reservation.skuId()
                        + "\",\"quantity\":" + req.quantity() + ",\"pickerId\":\""
                        + req.pickerId() + "\"}");

        ReservationRow updated = reservation.withPick(newQuantityPicked, reservationStatus);
        return picks.buildResponse(req.clientEventId(), updated);
    }

    @Transactional
    public PickEventResponse shortPick(ShortPickRequest req) {
        if (picks.findReservationIdByClientEventId(req.clientEventId()).isPresent()) {
            return picks.buildResponseForClientEvent(req.clientEventId());
        }

        ReservationRow reservation = picks.lockReservationForUpdate(req.reservationId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "reservation not found: " + req.reservationId()));

        if (picks.findReservationIdByClientEventId(req.clientEventId()).isPresent()) {
            return picks.buildResponseForClientEvent(req.clientEventId());
        }

        int remainingUnpicked = reservation.quantityReserved() - reservation.quantityPicked();
        if (req.quantityShort() > remainingUnpicked) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "quantity_short exceeds remaining unpicked quantity");
        }

        int foundQuantity = remainingUnpicked - req.quantityShort();
        String eventId = "EVT-" + UUID.randomUUID().toString().substring(0, 8);
        int eventQuantity = foundQuantity > 0 ? foundQuantity : req.quantityShort();
        if (!picks.tryInsertPickEvent(
                eventId,
                req.clientEventId(),
                req.reservationId(),
                "SHORT_PICK",
                eventQuantity,
                reservation.skuId(),
                req.reason(),
                req.pickerId(),
                Instant.now())) {
            return picks.buildResponseForClientEvent(req.clientEventId());
        }

        if (foundQuantity > 0) {
            if (!picks.applyBinShortPick(
                    reservation.binId(), reservation.skuId(), foundQuantity, remainingUnpicked)) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT, "failed to apply short-pick to bin: " + reservation.binId());
            }
        } else if (remainingUnpicked > 0) {
            if (!picks.applyBinShortPick(
                    reservation.binId(), reservation.skuId(), 0, remainingUnpicked)) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT, "failed to release reserved stock for short-pick");
            }
        }

        int newQuantityPicked = reservation.quantityPicked() + foundQuantity;
        String reservationStatus = newQuantityPicked == reservation.quantityReserved()
                ? "PICKED"
                : "SHORT";
        picks.updateReservationPicked(req.reservationId(), newQuantityPicked, reservationStatus);

        int linePicked = picks.getOrderLineQuantityPicked(reservation.orderId(), reservation.lineId())
                + foundQuantity;
        picks.updateOrderLinePicked(reservation.orderId(), reservation.lineId(), linePicked);

        refreshOrderStatus(reservation.orderId());

        audit.append(reservation.orderId(), "RESERVATION", req.reservationId(), "SHORT_PICK_APPLIED",
                "{\"binId\":\"" + reservation.binId() + "\",\"skuId\":\"" + reservation.skuId()
                        + "\",\"quantityShort\":" + req.quantityShort() + ",\"foundQuantity\":"
                        + foundQuantity + ",\"reason\":\"" + req.reason() + "\",\"pickerId\":\""
                        + req.pickerId() + "\"}");

        ReservationRow updated = reservation.withPick(newQuantityPicked, reservationStatus);
        return picks.buildResponse(req.clientEventId(), updated);
    }

    private void validateBarcode(String scannedBarcode, String expectedSku) {
        if (!expectedSku.equals(scannedBarcode)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "scanned barcode does not match reservation sku: expected " + expectedSku);
        }
    }

    private void validatePickQuantity(int quantity, ReservationRow reservation) {
        if (reservation.quantityPicked() + quantity > reservation.quantityReserved()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "pick quantity exceeds reservation");
        }
    }

    private void refreshOrderStatus(String orderId) {
        String newStatus;
        if (picks.isOrderFullyPicked(orderId)) {
            newStatus = "PICKED";
        } else if (picks.hasShortReservation(orderId)) {
            newStatus = "SHORT";
        } else {
            newStatus = "PICKING";
        }
        picks.updateOrderStatus(orderId, newStatus);
        audit.append(orderId, "ORDER", orderId, "ORDER_STATUS_CHANGED",
                "{\"status\":\"" + newStatus + "\"}");
    }
}
