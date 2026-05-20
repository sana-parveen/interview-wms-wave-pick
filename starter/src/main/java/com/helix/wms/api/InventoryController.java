package com.helix.wms.api;

import com.helix.wms.api.dto.InventoryResponse;
import com.helix.wms.repository.InventoryRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/inventory")
public class InventoryController {

    private final InventoryRepository inventory;

    public InventoryController(InventoryRepository inventory) {
        this.inventory = inventory;
    }

    @GetMapping("/{skuId}")
    public InventoryResponse get(@PathVariable String skuId) {
        if (!inventory.skuExists(skuId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "sku not found: " + skuId);
        }
        return inventory.loadInventory(skuId);
    }
}
