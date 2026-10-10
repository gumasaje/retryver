package com.gumasaje.retryver;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;

@Service
public class DeliveryExecutor {
    private final PendingDeliveryReader pendingDeliveryReader;

    public DeliveryExecutor(PendingDeliveryReader pendingDeliveryReader) {
        this.pendingDeliveryReader = pendingDeliveryReader;
    }

    public Optional<Integer> execute(String deliveryId) throws IOException, InterruptedException {
        Optional<PendingDelivery> deliveryResult = pendingDeliveryReader.findByDeliveryId(deliveryId);

        if (deliveryResult.isEmpty()) {
            return Optional.empty();
        }

        PendingDelivery delivery = deliveryResult.get();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(delivery.receiverUrl()))
                .header("X-Event-Id", delivery.eventId())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(delivery.payload()))
                .build();

        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());

            return Optional.of(response.statusCode());
        }
    }
}
