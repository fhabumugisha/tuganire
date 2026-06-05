package com.tuganire.conversation;

import com.tuganire.shared.exception.BusinessException;
import com.tuganire.stt.SttService;
import com.tuganire.translation.TranslationRequest;
import com.tuganire.translation.TranslationResponse;
import com.tuganire.translation.TranslationService;
import com.tuganire.tts.TtsService;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Implementation of {@link ConversationService} that wires together STT → translate → TTS in a virtual-thread executor.
 * No {@code synchronized} keyword is used; the pipeline is naturally thread-safe because each call works on independent
 * local variables.
 */
@Slf4j
@Service
@RequiredArgsConstructor
class ConversationServiceImpl implements ConversationService {

    private static final String AUDIO_URL_TEMPLATE = "/api/v1/audio/%s.mp3";

    private final SttService sttService;
    private final TranslationService translationService;
    private final TtsService ttsService;
    private final AudioStore audioStore;

    /**
     * Virtual-thread executor used to run the pipeline off the caller thread when invoked from the WebSocket handler.
     * Each task gets its own virtual thread, so blocking I/O (STT, LLM, TTS HTTP calls) does not pin carrier threads.
     */
    private final ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();

    @Override
    public AudioTranslationResult processAudio(byte[] audio, Locale srcLang, Locale tgtLang, String sessionId) {
        long start = System.currentTimeMillis();
        log.debug("processAudio start: sessionId={} srcLang={} tgtLang={}", sessionId, srcLang, tgtLang);

        String transcript = sttService.transcribe(audio, srcLang);
        log.debug("STT done: sessionId={} transcript.length={}", sessionId, transcript.length());

        TranslationResponse translation = translationService
                .translate(new TranslationRequest(transcript, srcLang, tgtLang, sessionId));
        log.debug("Translation done: sessionId={}", sessionId);

        byte[] audioBytes = ttsService.synthesize(translation.translatedText(), tgtLang);
        log.debug("TTS done: sessionId={} audioBytes={}", sessionId, audioBytes.length);

        String audioId = UUID.randomUUID().toString();
        audioStore.store(audioId, audioBytes);

        String audioUrl = AUDIO_URL_TEMPLATE.formatted(audioId);
        long durationMs = System.currentTimeMillis() - start;
        log.info("Pipeline complete: sessionId={} durationMs={} audioUrl={}", sessionId, durationMs, audioUrl);

        return new AudioTranslationResult(transcript, translation, audioUrl, durationMs);
    }

    @Override
    public void processAudioStreaming(byte[] audio, Locale srcLang, Locale tgtLang, String sessionId,
            Consumer<ConversationEvent> sink) {
        virtualExecutor.execute(() -> runStreamingPipeline(audio, srcLang, tgtLang, sessionId, sink));
    }

    private void runStreamingPipeline(byte[] audio, Locale srcLang, Locale tgtLang, String sessionId,
            Consumer<ConversationEvent> sink) {
        long start = System.currentTimeMillis();
        try {
            String transcript = sttService.transcribe(audio, srcLang);
            sink.accept(new ConversationEvent.FinalTranscript(sessionId, transcript, srcLang));
            log.debug("Streaming STT done: sessionId={}", sessionId);

            TranslationResponse translation = translationService
                    .translate(new TranslationRequest(transcript, srcLang, tgtLang, sessionId));
            sink.accept(new ConversationEvent.TranslationReady(sessionId, translation.translatedText(), tgtLang));
            log.debug("Streaming translation done: sessionId={}", sessionId);

            byte[] audioBytes = ttsService.synthesize(translation.translatedText(), tgtLang);
            String audioId = UUID.randomUUID().toString();
            audioStore.store(audioId, audioBytes);

            long durationMs = System.currentTimeMillis() - start;
            String audioUrl = AUDIO_URL_TEMPLATE.formatted(audioId);
            sink.accept(new ConversationEvent.AudioReady(sessionId, audioUrl, durationMs));
            log.info("Streaming pipeline complete: sessionId={} durationMs={}", sessionId, durationMs);

        } catch (BusinessException ex) {
            log.error("Business error in streaming pipeline: sessionId={} code={}", sessionId, ex.getMessageKey(), ex);
            sink.accept(new ConversationEvent.ErrorOccurred(sessionId, ex.getMessageKey(), ex.getMessage()));
        } catch (Exception ex) {
            log.error("Unexpected error in streaming pipeline: sessionId={}", sessionId, ex);
            sink.accept(new ConversationEvent.ErrorOccurred(sessionId, "error.pipeline.unexpected", ex.getMessage()));
        }
    }
}
