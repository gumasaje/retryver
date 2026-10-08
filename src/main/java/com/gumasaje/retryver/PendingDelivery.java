package com.gumasaje.retryver;

public record PendingDelivery(String deliveryId, String eventId, String receiverUrl, String payload,
                              String deliveryStatus) {
}
