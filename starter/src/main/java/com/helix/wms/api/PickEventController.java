package com.helix.wms.api;

import com.helix.wms.api.dto.PickEventRequest;
import com.helix.wms.api.dto.PickEventResponse;
import com.helix.wms.api.dto.ShortPickRequest;
import com.helix.wms.service.PickEventService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/pick-events")
public class PickEventController {

    private final PickEventService picks;

    public PickEventController(PickEventService picks) {
        this.picks = picks;
    }

    @PostMapping
    public PickEventResponse pick(@Valid @RequestBody PickEventRequest req) {
        return picks.pick(req);
    }

    @PostMapping("/short-pick")
    public PickEventResponse shortPick(@Valid @RequestBody ShortPickRequest req) {
        return picks.shortPick(req);
    }
}
