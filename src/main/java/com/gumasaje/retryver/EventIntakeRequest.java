package com.gumasaje.retryver;

import tools.jackson.databind.JsonNode;

public record EventIntakeRequest(String eventId, String eventCategory, JsonNode payload, String receiverUrl
) {
}
