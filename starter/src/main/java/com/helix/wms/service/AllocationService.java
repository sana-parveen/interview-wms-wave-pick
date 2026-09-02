package com.helix.wms.service;

import com.helix.wms.api.dto.AllocateResponse;
import com.helix.wms.repository.AllocationRepository;
import com.helix.wms.repository.AllocationRepository.BinStockRow;
import com.helix.wms.repository.AllocationRepository.OrderLineRow;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class AllocationService {

    private final AllocationRepository allocation;

    public AllocationService(AllocationRepository allocation) {
        this.allocation = allocation;
    }

    @Transactional
    public AllocateResponse allocate(String orderId) {
        String status = allocation.findOrderStatus(orderId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "order not found: " + orderId));

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

        List<AllocateResponse.Reservation> reservations = new ArrayList<>();
        for (OrderLineRow line : lines) {
            BinStockRow bin = allocation.findBinForFullLine(line.skuId(), line.quantityRequired())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "insufficient inventory for sku: " + line.skuId()));

            String reservationId = "R-" + UUID.randomUUID().toString().substring(0, 8);
            allocation.incrementReserved(bin.binId(), bin.skuId(), line.quantityRequired());
            allocation.insertReservation(
                    reservationId,
                    orderId,
                    line.lineId(),
                    bin.binId(),
                    line.skuId(),
                    line.quantityRequired());
            reservations.add(new AllocateResponse.Reservation(
                    reservationId,
                    line.lineId(),
                    bin.binId(),
                    line.quantityRequired()));
        }

        allocation.updateOrderStatus(orderId, "ALLOCATED");
        return new AllocateResponse(orderId, "ALLOCATED", reservations);
    }
}
