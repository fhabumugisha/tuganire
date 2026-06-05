package com.tuganire.stt;

import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.AudioTranscriptionResponse;
import org.springframework.ai.openai.OpenAiAudioTranscriptionModel;
import org.springframework.ai.openai.OpenAiAudioTranscriptionOptions;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Component;

/**
 * STT provider backed by OpenAI Whisper-1.
 *
 * <p>
 * Supports French and English well. Kinyarwanda ({@code "rw"}) is NOT supported — whisper-1 does not cover it. See
 * {@code STT-KINYARWANDA-NOTE.md} for alternatives. This provider intentionally returns {@code false} for {@code "rw"}
 * in {@link #supportsLanguage} so {@link SttProviderFactory} can surface a clear error rather than silently
 * transcribing garbage.
 */
@Component
@RequiredArgsConstructor
@Slf4j
class WhisperSttProvider implements SttProvider {

    static final String PROVIDER_NAME = "whisper";

    /** Language codes fully supported by OpenAI whisper-1. */
    private static final Set<String> SUPPORTED_LANGS = Set.of("fr", "en");

    /** Whisper model identifier. */
    private static final String WHISPER_MODEL = "whisper-1";

    private final OpenAiAudioTranscriptionModel transcriptionModel;

    @Override
    public String name() {
        return PROVIDER_NAME;
    }

    @Override
    public boolean supportsLanguage(String languageCode) {
        return SUPPORTED_LANGS.contains(languageCode);
    }

    /**
     * Transcribes the given audio bytes to text using Whisper-1.
     *
     * <p>
     * Logs a warning and proceeds when the language is not in the supported set; this path should normally be
     * unreachable because {@link SttProviderFactory#forLanguage} rejects unsupported codes before delegating here.
     *
     * @param audio
     *            raw audio bytes (WAV, MP3, M4A, or WebM)
     * @param language
     *            expected spoken language; only {@code fr} and {@code en} are supported
     * @return transcript text
     */
    @Override
    public String transcribe(byte[] audio, Locale language) {
        String langCode = language.getLanguage();
        if (!supportsLanguage(langCode)) {
            log.warn(
                    "WhisperSttProvider: language '{}' is not supported by whisper-1 — transcript may be inaccurate or empty. "
                            + "See STT-KINYARWANDA-NOTE.md for rw alternatives.",
                    langCode);
        }

        ByteArrayResource resource = new ByteArrayResource(audio) {

            @Override
            public @Nullable String getFilename() {
                // Whisper API requires a filename with an extension to detect format
                return "audio.wav";
            }
        };

        OpenAiAudioTranscriptionOptions options = OpenAiAudioTranscriptionOptions.builder().model(WHISPER_MODEL)
                .language(langCode).build();

        AudioTranscriptionResponse response = transcriptionModel.call(new AudioTranscriptionPrompt(resource, options));

        String transcript = response.getResult().getOutput();
        log.debug("WhisperSttProvider: transcribed {} bytes in '{}' → {} chars", audio.length, langCode,
                transcript.length());
        return transcript;
    }
}
