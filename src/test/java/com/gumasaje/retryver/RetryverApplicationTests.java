package com.gumasaje.retryver;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class RetryverApplicationTests {
    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private EventIntakeService eventIntakeService;

    @Autowired
    private JsonMapper jsonMapper;

    @Test
    void connectsToRetryverDatabase() {
        String database = jdbcClient.sql("SELECT DATABASE()")
                .query(String.class)
                .single();

        assertEquals("retryver", database);
    }

    @Test
    void savesEventAndDelivery() {
        String eventId = "test-" + UUID.randomUUID();

        var request = new EventIntakeRequest(
                eventId,
                "주문 생성",
                jsonMapper.readTree("{\"orderId\":42}"),
                "http://localhost:9090/events"
        );

        String deliveryId = eventIntakeService.accept(request);

        int eventCount = jdbcClient.sql("SELECT COUNT(*) FROM events WHERE event_id = :eventId")
                .param("eventId", eventId)
                .query(Integer.class)
                .single();

        int deliveryCount = jdbcClient.sql("SELECT COUNT(*) FROM deliveries WHERE delivery_id = :deliveryId AND event_id = :eventId")
                .param("deliveryId", deliveryId)
                .param("eventId", eventId)
                .query(Integer.class)
                .single();

        assertEquals(1, eventCount);
        assertEquals(1, deliveryCount);
    }

    @Test
    void rollsBackEventWhenDeliverySaveFails() {
        String eventId = "test-" + UUID.randomUUID();

        var request = new EventIntakeRequest(
                eventId,
                "주문 생성",
                jsonMapper.readTree("{\"orderId\":42}"),
                null
        );

        var failure = assertThrows(
                DataIntegrityViolationException.class,
                () -> eventIntakeService.accept(request)
        );

        assertTrue(failure.getMostSpecificCause()
                .getMessage().contains("receiver_url"));

        int eventCount = jdbcClient.sql("SELECT COUNT(*) FROM events WHERE event_id = :eventId")
                .param("eventId", eventId)
                .query(Integer.class)
                .single();

        int deliveryCount = jdbcClient.sql("SELECT COUNT(*) FROM deliveries WHERE event_id = :eventId")
                .param("eventId", eventId)
                .query(Integer.class)
                .single();

        assertEquals(0, eventCount);
        assertEquals(0, deliveryCount);
    }

}
