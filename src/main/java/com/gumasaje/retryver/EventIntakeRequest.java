package com.gumasaje.retryver;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.JsonNode;

public record EventIntakeRequest(
        @NotBlank
        @Size(max = 100)
        String eventId,

        @NotBlank
        @Size(max = 100)
        String eventCategory,

        JsonNode payload,

        @NotBlank
        @Size(max = 2048)
        String receiverUrl
) {
        @JsonIgnore
        @AssertTrue(message = "payload는 생략하거나 null로 보낼 수 없습니다.")
        public boolean isPayloadPresent() {
            return payload != null && !payload.isNull();
        }
}
