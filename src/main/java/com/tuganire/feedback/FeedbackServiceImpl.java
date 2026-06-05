package com.tuganire.feedback;

import com.tuganire.shared.exception.BusinessException;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
class FeedbackServiceImpl implements FeedbackService {

    private static final String COUNTER_NAME = "tuganire.feedback.total";
    private static final String TAG_TYPE = "type";

    private final FeedbackRepo feedbackRepo;
    private final MeterRegistry meterRegistry;

    @Override
    @Transactional
    public void submit(FeedbackRequest request) {
        if (request.type() == FeedbackType.CORRECTION_PROPOSED
                && (request.suggestedCorrection() == null || request.suggestedCorrection().isBlank())) {
            throw new BusinessException("feedback.correction.required");
        }

        Feedback feedback = new Feedback();
        feedback.setSessionId(request.sessionId());
        feedback.setTranslationId(request.translationId());
        feedback.setType(request.type());
        feedback.setSuggestedCorrection(request.suggestedCorrection());
        // createdAt is populated by @CreatedDate via JPA auditing

        feedbackRepo.save(feedback);

        meterRegistry.counter(COUNTER_NAME, TAG_TYPE, request.type().name()).increment();

        log.debug("Feedback persisted: sessionId={}, type={}", request.sessionId(), request.type());
    }

    @Override
    @Transactional(readOnly = true)
    public long countByType(FeedbackType type) {
        return feedbackRepo.countByType(type);
    }
}
