package com.tuganire.tts;

import com.tuganire.conversation.AudioStore;
import com.tuganire.shared.exception.BusinessException;
import com.tuganire.tts.MmsTtsClient.MmsTtsRequest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default {@link TtsVoiceComparisonService}. Synthesises the same Kinyarwanda text with each compared voice on its own
 * virtual thread (so both clips are ready in roughly the time of the slowest single voice rather than their sum),
 * stores each clip in the ephemeral {@link AudioStore}, and returns the candidates shuffled for a blind A/B test.
 */
@Service
@RequiredArgsConstructor
@Slf4j
class TtsVoiceComparisonServiceImpl implements TtsVoiceComparisonService {

    /** MMS Kinyarwanda voice with punctuation-aware pauses (native accent). */
    static final String VARIANT_MMS_PAUSES = "mms-pauses";

    /** OpenAI {@code gpt-4o-mini-tts} steered for articulation (generic accent). */
    static final String VARIANT_OPENAI_STEERED = "openai-steered";

    /**
     * The two voices compared for Kinyarwanda reading. Fixed by product decision (MMS+pauses vs OpenAI steered).
     */
    private static final List<String> VARIANT_IDS = List.of(VARIANT_MMS_PAUSES, VARIANT_OPENAI_STEERED);

    /** Message-key prefix for the per-variant admin labels (e.g. {@code admin.voice.variant.mms-pauses}). */
    private static final String LABEL_KEY_PREFIX = "admin.voice.variant.";

    private static final String AUDIO_URL_TEMPLATE = "/api/v1/audio/%s.mp3";

    /** Message key surfaced to the client (HTTP 400) when a voice cannot be synthesised. */
    private static final String COMPARE_FAILED_KEY = "voice.compareFailed";

    private final MmsTtsClient mmsTtsClient;
    private final OpenAiSteeredSpeechClient steeredSpeechClient;
    private final AudioStore audioStore;
    private final TtsVoiceVoteRepository voteRepository;
    private final MessageSource messageSource;

    @Override
    public List<TtsVoiceCandidate> compare(String text, String languageCode) {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<TtsVoiceCandidate>> futures = VARIANT_IDS.stream().map(variant -> CompletableFuture
                    .supplyAsync(() -> synthesizeCandidate(variant, text, languageCode), executor)).toList();
            List<TtsVoiceCandidate> candidates = new ArrayList<>(
                    futures.stream().map(CompletableFuture::join).toList());
            // Shuffle so neither voice gets a fixed "Voix 1" position, keeping the A/B test blind.
            Collections.shuffle(candidates);
            return candidates;
        } catch (CompletionException ex) {
            // A/B needs both voices; if either synthesis fails, surface a clean 400 the UI can degrade on.
            log.warn("TTS voice comparison failed: {}", ex.getCause() != null ? ex.getCause().getMessage() : ex);
            throw new BusinessException(COMPARE_FAILED_KEY, ex);
        }
    }

    private TtsVoiceCandidate synthesizeCandidate(String variant, String text, String languageCode) {
        byte[] audio = synthesizeVariant(variant, text, languageCode);
        String id = UUID.randomUUID().toString();
        audioStore.store(id, audio);
        return new TtsVoiceCandidate(variant, labelFor(variant), String.format(AUDIO_URL_TEMPLATE, id));
    }

    private byte[] synthesizeVariant(String variant, String text, String languageCode) {
        return switch (variant) {
            case VARIANT_MMS_PAUSES -> mmsTtsClient.synthesize(new MmsTtsRequest(text, languageCode, true));
            case VARIANT_OPENAI_STEERED -> steeredSpeechClient.synthesize(text);
            default -> throw new IllegalArgumentException("Unknown TTS voice variant: " + variant);
        };
    }

    @Override
    @Transactional
    public void recordChoice(String chosenVariant, String rejectedVariant, String sessionId) {
        voteRepository.save(TtsVoiceVote.builder().chosenVariant(chosenVariant).rejectedVariant(rejectedVariant)
                .sessionId(sessionId).build());
        log.debug("Recorded TTS voice vote: chosen={} rejected={}", chosenVariant, rejectedVariant);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TtsVoiceStat> stats() {
        return voteRepository.countVotesByVariant().stream()
                .map(c -> new TtsVoiceStat(c.getVariant(), labelFor(c.getVariant()), c.getVotes())).toList();
    }

    /** Resolves a variant's localized human-readable label from the message bundle; falls back to the id. */
    private String labelFor(String variant) {
        return messageSource.getMessage(LABEL_KEY_PREFIX + variant, null, variant, LocaleContextHolder.getLocale());
    }
}
