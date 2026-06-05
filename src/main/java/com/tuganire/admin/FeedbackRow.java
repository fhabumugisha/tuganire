package com.tuganire.admin;

import com.tuganire.feedback.FeedbackType;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * Read-only view of a feedback entry for the admin dashboard.
 *
 * @param id
 *            the feedback id
 * @param translationId
 *            the rated translation's id (may be {@code null})
 * @param sessionId
 *            the anonymous session id
 * @param type
 *            the feedback type
 * @param suggestedCorrection
 *            the proposed correction text (may be {@code null})
 * @param createdAt
 *            when the feedback was submitted
 */
public record FeedbackRow(Long id, @Nullable String translationId, String sessionId, FeedbackType type,
        @Nullable String suggestedCorrection, Instant createdAt) {
}
