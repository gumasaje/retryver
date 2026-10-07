package com.gumasaje.retryver;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EventIntakeController {
    private final EventIntakeService eventIntakeService;

    public EventIntakeController(EventIntakeService eventIntakeService) {
        this.eventIntakeService = eventIntakeService;
    }

    @PostMapping("/events")
    public ResponseEntity<EventIntakeResponse> accept(
            @Valid @RequestBody EventIntakeRequest request
    ) {
        String deliveryId = eventIntakeService.accept(request);

        EventIntakeResponse response = new EventIntakeResponse(request.eventId(), deliveryId, "PENDING");

        return ResponseEntity.accepted().body(response);
    }
}
