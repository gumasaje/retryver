package com.gumasaje.retryver;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.MethodArgumentNotValidException;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RetryverApplicationTests {
    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private EventIntakeService eventIntakeService;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private MockMvc mockMvc;

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

    @Test
    void acceptsEventAndPersistsDelivery() throws Exception {
        String eventId = "test-http-" + UUID.randomUUID();

        String requestBody = """
                {
                "eventId": "%s",
                "eventCategory": "주문 생성",
                "payload": {"orderId": 42},
                "receiverUrl": "http://localhost:9090/events"
                }
                """.formatted(eventId);

        var result = mockMvc.perform(
                        post("/events")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isAccepted())
                .andReturn();

        var response = jsonMapper.readValue(
                result.getResponse().getContentAsString(),
                EventIntakeResponse.class
        );


        int eventCount = jdbcClient.sql("SELECT COUNT(*) FROM events WHERE event_id = :eventId")
                .param("eventId", eventId)
                .query(Integer.class)
                .single();


        int pendingDeliveryCount = jdbcClient.sql("SELECT COUNT(*) FROM deliveries WHERE event_id = :eventId AND delivery_id = :deliveryId AND delivery_status = :deliveryStatus")
                .param("eventId", eventId)
                .param("deliveryId", response.deliveryId())
                .param("deliveryStatus", "PENDING")
                .query(Integer.class)
                .single();

        assertEquals(eventId, response.eventId());
        assertEquals("PENDING", response.deliveryStatus());
        assertEquals(1, eventCount);
        assertEquals(1, pendingDeliveryCount);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "\"payload\": null,"})
    void rejectsMissingOrNullPayload(String payloadField) throws Exception {
        String eventId = "test-invalid-" + UUID.randomUUID();

        String requestBody = """
                {
                  "eventId": "%s",
                  "eventCategory": "주문 생성",
                  %s
                  "receiverUrl": "http://localhost:9090/events"
                }
                """.formatted(eventId, payloadField);

        var result = mockMvc.perform(
                        post("/events")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isBadRequest())
                .andReturn();

        var failure = assertInstanceOf(
                MethodArgumentNotValidException.class,
                result.getResolvedException()
        );

        assertTrue(
                failure.getBindingResult().hasFieldErrors("payloadPresent")
        );

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
