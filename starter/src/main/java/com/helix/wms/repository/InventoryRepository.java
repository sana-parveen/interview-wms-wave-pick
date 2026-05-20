package com.helix.wms.repository;

import com.helix.wms.api.dto.InventoryResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class InventoryRepository {

    private final JdbcTemplate jdbc;

    public InventoryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean skuExists(String skuId) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM skus WHERE sku_id = ?",
                Integer.class, skuId);
        return n != null && n > 0;
    }

    public InventoryResponse loadInventory(String skuId) {
        List<InventoryResponse.Bin> bins = jdbc.query(
                "SELECT bin_id, quantity_on_hand, quantity_reserved " +
                        "FROM bin_stock WHERE sku_id = ? ORDER BY bin_id",
                (rs, n) -> {
                    int onHand = rs.getInt("quantity_on_hand");
                    int reserved = rs.getInt("quantity_reserved");
                    return new InventoryResponse.Bin(
                            rs.getString("bin_id"),
                            onHand,
                            reserved,
                            onHand - reserved);
                },
                skuId);

        int totalOnHand = bins.stream().mapToInt(InventoryResponse.Bin::onHand).sum();
        int totalReserved = bins.stream().mapToInt(InventoryResponse.Bin::reserved).sum();
        return new InventoryResponse(
                skuId,
                totalOnHand,
                totalReserved,
                totalOnHand - totalReserved,
                bins);
    }
}
