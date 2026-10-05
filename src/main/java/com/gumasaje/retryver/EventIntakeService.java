package com.gumasaje.retryver;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class EventIntakeService {
    private final JdbcClient jdbcClient;

    public EventIntakeService(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Transactional
    public String accept(EventIntakeRequest request) {
        String deliveryId = UUID.randomUUID().toString();

        jdbcClient.sql("INSERT INTO events (event_id, event_category, payload) VALUES (:eventId, :eventCategory, :payload)")
                .param("eventId", request.eventId())
                .param("eventCategory", request.eventCategory())
                .param("payload", request.payload().toString())
                .update();

        jdbcClient.sql("INSERT INTO deliveries (delivery_id, event_id, receiver_url, delivery_status) VALUES (:deliveryId, :eventId, :receiverUrl, :deliveryStatus)")
                .param("deliveryId", deliveryId)
                .param("eventId", request.eventId())
                .param("receiverUrl", request.receiverUrl())
                .param("deliveryStatus", "PENDING")
                .update();

        return deliveryId;
    }
}
