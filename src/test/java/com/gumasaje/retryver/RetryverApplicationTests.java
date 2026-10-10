package com.gumasaje.retryver;

import com.sun.net.httpserver.HttpServer;
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

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

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

    @Autowired
    private PendingDeliveryReader pendingDeliveryReader;

    @Autowired
    private DeliveryExecutor deliveryExecutor;

    private record ReceivedRequest(
            String method, String eventId, String contentType, String body
    ) {
    }

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

        var delivery = pendingDeliveryReader
                .findByDeliveryId(response.deliveryId())
                .orElseThrow();


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

        assertEquals(eventId, delivery.eventId());
        assertEquals(response.deliveryId(), delivery.deliveryId());
        assertEquals("http://localhost:9090/events", delivery.receiverUrl());
        assertEquals(jsonMapper.readTree(requestBody).get("payload"),
                jsonMapper.readTree(delivery.payload()));
        assertEquals("PENDING", delivery.deliveryStatus());
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

    @Test
    void returnsEmptyWhenDeliveryDoesNotExist() {
        String missingDeliveryId = "missing--" + UUID.randomUUID();
        var result = pendingDeliveryReader.findByDeliveryId(missingDeliveryId);

        assertTrue(result.isEmpty());
    }

    @Test
    void sendStoredPendingDeliveryToReceiver() throws Exception {
        var received = new AtomicReference<ReceivedRequest>();

        var receiver = HttpServer.create(
                new InetSocketAddress("127.0.0.1", 0), 0
        );

        receiver.createContext("/callback", exchange -> {
            try (exchange) {
                received.set(new ReceivedRequest(
                        exchange.getRequestMethod(),
                        exchange.getRequestHeaders().getFirst("X-Event-Id"),
                        exchange.getRequestHeaders().getFirst("Content-Type"),
                        new String(
                                exchange.getRequestBody().readAllBytes(),
                                StandardCharsets.UTF_8
                        )
                ));
                exchange.sendResponseHeaders(204, -1);
            }
        });

        receiver.start();

        try {
            String receiverUrl = "http://127.0.0.1:"
                    + receiver.getAddress().getPort() + "/callback";

            String eventId = "test-invalid-" + UUID.randomUUID();

            String requestBody = """
                        {
                          "eventId": "%s",
                          "eventCategory": "주문 생성",
                          "payload": {"orderId": 1010, "note": "전달 확인"},
                          "receiverUrl": "%s"
                          }
                    """.formatted(eventId, receiverUrl);

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

            var observedStatus = deliveryExecutor.execute(response.deliveryId()).orElseThrow();

            assertEquals(204, observedStatus);

            var delivery = pendingDeliveryReader
                    .findByDeliveryId(response.deliveryId())
                    .orElseThrow();

            var actual = received.get();

            assertNotNull(actual);

            assertEquals("POST", actual.method());
            assertEquals(delivery.eventId(), actual.eventId());
            assertEquals("application/json", actual.contentType());
            assertEquals(
                    jsonMapper.readTree(delivery.payload()),
                    jsonMapper.readTree(actual.body())
            );

            assertEquals("PENDING", delivery.deliveryStatus());

        } finally {
            receiver.stop(0);
        }

    }
}
