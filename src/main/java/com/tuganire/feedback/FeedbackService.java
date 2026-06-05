package com.tuganire.feedback;

/**
 * Service for persisting user feedback on translations and querying feedback metrics.
 *
 * <p>
 * Feedback is a core differentiator (US-06): thumbs-up/down reactions and proposed corrections feed the continuous
 * improvement loop.
 */
public interface FeedbackService {

    /**
     * Validates and persists a feedback submission from the user.
     *
     * <p>
     * Validation rules:
     *
     * <ul>
     * <li>{@link FeedbackType#THUMBS_UP} and {@link FeedbackType#THUMBS_DOWN} require no correction text.
     * <li>{@link FeedbackType#CORRECTION_PROPOSED} requires a non-blank {@code
     *       suggestedCorrection}; a blank value throws {@link com.tuganire.shared.exception.BusinessException}.
     * </ul>
     *
     * @param request
     *            the feedback payload, never {@code null}
     * @throws com.tuganire.shared.exception.BusinessException
     *             if a correction type is submitted without correction text
     */
    void submit(FeedbackRequest request);

    /**
     * Returns the total number of persisted feedback entries for the given type.
     *
     * @param type
     *            the feedback type to count, never {@code null}
     * @return non-negative count of entries with that type
     */
    long countByType(FeedbackType type);
}
