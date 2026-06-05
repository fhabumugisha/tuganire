package com.tuganire.feedback;

import org.jspecify.annotations.Nullable;

public record FeedbackRequest(String sessionId, String translationId, FeedbackType type,
        @Nullable String suggestedCorrection) {
}
