package com.tuganire.admin;

import com.tuganire.feedback.Feedback;
import com.tuganire.feedback.FeedbackRepo;
import com.tuganire.feedback.FeedbackType;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default {@link AdminStatsService}: reads feedback counts/rows and LLM-usage aggregates from their repositories and
 * maps them to admin DTOs (controllers never see entities).
 */
@Service
@RequiredArgsConstructor
class AdminStatsServiceImpl implements AdminStatsService {

    /** Number of most-recent feedback rows shown in the dashboard table. */
    private static final int RECENT_FEEDBACK_LIMIT = 50;

    private final FeedbackRepo feedbackRepo;
    private final LlmUsageEventRepo usageRepo;

    @Override
    @Transactional(readOnly = true)
    public FeedbackStats feedbackStats() {
        long up = feedbackRepo.countByType(FeedbackType.THUMBS_UP);
        long down = feedbackRepo.countByType(FeedbackType.THUMBS_DOWN);
        long corrections = feedbackRepo.countByType(FeedbackType.CORRECTION_PROPOSED);
        List<FeedbackRow> recent = feedbackRepo.findAllByOrderByCreatedAtDesc(Limit.of(RECENT_FEEDBACK_LIMIT)).stream()
                .map(AdminStatsServiceImpl::toRow).toList();
        return new FeedbackStats(up, down, corrections, recent);
    }

    @Override
    @Transactional(readOnly = true)
    public LlmUsageStats llmUsageStats() {
        long calls = usageRepo.count();
        long promptTokens = usageRepo.sumPromptTokens();
        long completionTokens = usageRepo.sumCompletionTokens();
        BigDecimal cost = usageRepo.sumEstimatedCost();
        List<ModelUsageRow> byModel = usageRepo.aggregateByModel().stream().map(a -> new ModelUsageRow(a.getProvider(),
                a.getModel(), a.getCalls(), a.getPromptTokens(), a.getCompletionTokens(), a.getCost())).toList();
        return new LlmUsageStats(calls, promptTokens, completionTokens, cost == null ? BigDecimal.ZERO : cost, byModel);
    }

    private static FeedbackRow toRow(Feedback f) {
        return new FeedbackRow(f.getId(), f.getTranslationId(), f.getSessionId(), f.getType(),
                f.getSuggestedCorrection(), f.getCreatedAt());
    }
}
