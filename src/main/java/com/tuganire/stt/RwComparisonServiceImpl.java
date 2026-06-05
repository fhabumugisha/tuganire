package com.tuganire.stt;

import com.tuganire.llm.LlmSettings;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default {@link RwComparisonService}. Cleans the same raw transcript with each compared model on its own virtual
 * thread, so both candidates are ready in roughly the time of the slowest single model rather than their sum.
 */
@Service
@RequiredArgsConstructor
@Slf4j
class RwComparisonServiceImpl implements RwComparisonService {

    /**
     * The two models compared for Kinyarwanda transcript cleanup. Fixed by product decision (GPT-5.5 vs Claude Sonnet);
     * both ids must exist in {@code tuganire.llm.translation-models} so their labels resolve.
     */
    private static final List<String> COMPARE_MODEL_IDS = List.of("gpt-5.5", "claude-sonnet-4-6");

    private final KinyarwandaCorrectionService correctionService;
    private final LlmSettings llmSettings;
    private final RwModelVoteRepository voteRepository;

    @Override
    public List<RwCandidate> compare(String rawTranscript) {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<RwCandidate>> futures = COMPARE_MODEL_IDS.stream()
                    .map(id -> CompletableFuture.supplyAsync(
                            () -> new RwCandidate(id, labelFor(id), correctionService.correct(rawTranscript, id)),
                            executor))
                    .toList();
            return futures.stream().map(CompletableFuture::join).toList();
        }
    }

    @Override
    @Transactional
    public void recordChoice(String chosenModel, String rejectedModel, String sessionId) {
        voteRepository.save(RwModelVote.builder().chosenModel(chosenModel).rejectedModel(rejectedModel)
                .sessionId(sessionId).build());
        log.debug("Recorded RW model vote: chosen={} rejected={}", chosenModel, rejectedModel);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RwModelStat> stats() {
        return voteRepository.countVotesByModel().stream()
                .map(c -> new RwModelStat(c.getModel(), labelFor(c.getModel()), c.getVotes())).toList();
    }

    /** Resolves a model's human-readable label from the configured selectable models; falls back to the id. */
    private String labelFor(String modelId) {
        return llmSettings.getAvailableModels().stream().filter(m -> m.id().equals(modelId)).map(m -> m.label())
                .findFirst().orElse(modelId);
    }
}
