package com.helix.wms.service;

import com.helix.wms.api.dto.AllocateConflictResponse;
import com.helix.wms.api.dto.AllocateResponse;
import com.helix.wms.repository.AllocationRepository;
import com.helix.wms.repository.AllocationRepository.BinAvailabilityRow;
import com.helix.wms.repository.AllocationRepository.OrderLineRow;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;

@Service
public class AllocationService {

    private final AllocationRepository allocation;

    public AllocationService(AllocationRepository allocation) {
        this.allocation = allocation;
    }

    @Transactional
    public AllocateResponse allocate(String orderId) {
        String status = allocation.lockOrderForUpdate(orderId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "order not found: " + orderId));

        if (isAlreadyAllocated(status)) {
            return allocation.loadAllocateResponse(orderId);
        }

        if (!"NEW".equals(status)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "order cannot be allocated from status: " + status);
        }

        List<OrderLineRow> lines = allocation.loadOrderLines(orderId);
        if (lines.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "order has no lines: " + orderId);
        }

        Map<String, List<BinAvailabilityRow>> lockedBinsBySku = lockBinsForOrder(lines);

        AllocationPlan plan = planAllocation(orderId, lines, lockedBinsBySku);
        if (!plan.canFullyAllocate()) {
            throw new InsufficientInventoryException(plan.conflictResponse());
        }

        List<AllocateResponse.Reservation> reservations = new ArrayList<>();
        for (PlannedReservation planned : plan.reservations()) {
            if (!allocation.incrementReserved(planned.binId(), planned.skuId(), planned.quantity())) {
                throw new InsufficientInventoryException(plan.conflictResponse());
            }

            String reservationId = "R-" + UUID.randomUUID().toString().substring(0, 8);
            allocation.insertReservation(
                    reservationId,
                    orderId,
                    planned.lineId(),
                    planned.binId(),
                    planned.skuId(),
                    planned.quantity());
            reservations.add(new AllocateResponse.Reservation(
                    reservationId,
                    planned.lineId(),
                    planned.binId(),
                    planned.quantity()));
        }

        allocation.updateOrderStatus(orderId, "ALLOCATED");
        return new AllocateResponse(orderId, "ALLOCATED", reservations);
    }

    private boolean isAlreadyAllocated(String status) {
        return "ALLOCATED".equals(status)
                || "PICKING".equals(status)
                || "PICKED".equals(status);
    }

    private Map<String, List<BinAvailabilityRow>> lockBinsForOrder(List<OrderLineRow> lines) {
        Set<String> skus = new TreeSet<>();
        for (OrderLineRow line : lines) {
            skus.add(line.skuId());
        }
        Map<String, List<BinAvailabilityRow>> lockedBinsBySku = new TreeMap<>();
        for (String sku : skus) {
            lockedBinsBySku.put(sku, allocation.lockBinsForSku(sku));
        }
        return lockedBinsBySku;
    }

    private AllocationPlan planAllocation(
            String orderId,
            List<OrderLineRow> lines,
            Map<String, List<BinAvailabilityRow>> lockedBinsBySku) {
        List<PlannedReservation> plannedReservations = new ArrayList<>();
        List<AllocateConflictResponse.LineDetail> lineDetails = new ArrayList<>();
        boolean canFullyAllocate = true;

        for (OrderLineRow line : lines) {
            int remaining = line.quantityRequired();
            List<AllocateConflictResponse.BinDetail> couldReserve = new ArrayList<>();

            for (BinAvailabilityRow bin : lockedBinsBySku.get(line.skuId())) {
                if (remaining == 0) {
                    break;
                }
                int take = Math.min(remaining, bin.available());
                if (take == 0) {
                    continue;
                }
                plannedReservations.add(new PlannedReservation(
                        line.lineId(), line.skuId(), bin.binId(), take));
                couldReserve.add(new AllocateConflictResponse.BinDetail(bin.binId(), take));
                remaining -= take;
            }

            int quantityAvailable = line.quantityRequired() - remaining;
            if (remaining > 0) {
                canFullyAllocate = false;
            }
            lineDetails.add(new AllocateConflictResponse.LineDetail(
                    line.lineId(),
                    line.skuId(),
                    line.quantityRequired(),
                    quantityAvailable,
                    couldReserve));
        }

        AllocateConflictResponse conflict = new AllocateConflictResponse(
                orderId,
                "insufficient inventory for full allocation",
                lineDetails);
        return new AllocationPlan(canFullyAllocate, plannedReservations, conflict);
    }

    private record PlannedReservation(
            String lineId, String skuId, String binId, int quantity) {}

    private record AllocationPlan(
            boolean canFullyAllocate,
            List<PlannedReservation> reservations,
            AllocateConflictResponse conflictResponse) {}
}
