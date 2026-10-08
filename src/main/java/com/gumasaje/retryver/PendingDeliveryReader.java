package com.gumasaje.retryver;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class PendingDeliveryReader {
    private final JdbcClient jdbcClient;

    public PendingDeliveryReader(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public Optional<PendingDelivery> findByDeliveryId(String deliveryId) {
        String sql = """
                SELECT delivery_id, d.event_id, receiver_url, payload, delivery_status
                FROM deliveries d JOIN events e ON d.event_id = e.event_id
                WHERE delivery_id = :deliveryId AND delivery_status = :deliveryStatus
                """;

        return jdbcClient.sql(sql)
                .param("deliveryId", deliveryId)
                .param("deliveryStatus", "PENDING")
                .query(PendingDelivery.class)
                .optional();
    }
}
