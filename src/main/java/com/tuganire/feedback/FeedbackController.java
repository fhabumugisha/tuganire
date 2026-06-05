package com.tuganire.feedback;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for user feedback on translations.
 *
 * <p>
 * Thin adapter: validates the HTTP payload, delegates persistence to {@link FeedbackService}, and returns 202 Accepted.
 * Native API versioning yields {@code /api/v1/feedback}.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Feedback", description = "Submit translation quality feedback")
public class FeedbackController {

    private final FeedbackService feedbackService;

    /**
     * Accepts a feedback submission for a previous translation.
     *
     * @param httpRequest
     *            the validated feedback payload
     * @return 202 Accepted on success
     */
    @PostMapping("/feedback")
    @Operation(summary = "Submit feedback", description = "Record a thumbs-up/down or correction proposal for a translation")
    public ResponseEntity<Void> submitFeedback(@Valid @RequestBody FeedbackHttpRequest httpRequest) {
        log.debug("POST /feedback: sessionId={} type={}", httpRequest.sessionId(), httpRequest.type());

        FeedbackRequest request = new FeedbackRequest(httpRequest.sessionId(), httpRequest.translationId(),
                httpRequest.type(), httpRequest.suggestedCorrection());

        feedbackService.submit(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    // ── HTTP request DTO ─────────────────────────────────────────────────────

    /**
     * HTTP request payload for {@code POST /api/v1/feedback}.
     *
     * <p>
     * Kept separate from the service-layer {@link FeedbackRequest} to allow Bean Validation constraints
     * ({@code @NotBlank}) to fire before the service layer is invoked.
     *
     * @param sessionId
     *            session that produced the translation; required
     * @param translationId
     *            identifier of the translation being rated; required
     * @param type
     *            feedback category; required
     * @param suggestedCorrection
     *            proposed correction text; required when {@code type} is {@link FeedbackType#CORRECTION_PROPOSED}
     */
    public record FeedbackHttpRequest(@NotBlank(message = "sessionId must not be blank") String sessionId,
            @NotBlank(message = "translationId must not be blank") String translationId,
            @NotNull(message = "type must not be null") FeedbackType type, String suggestedCorrection) {
    }
}
