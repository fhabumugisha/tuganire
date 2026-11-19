package com.tuganire.stt;

import com.tuganire.config.SttConfig;
import java.util.Locale;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.AudioTranscriptionResponse;
import org.springframework.ai.openai.OpenAiAudioTranscriptionModel;
import org.springframework.ai.openai.OpenAiAudioTranscriptionOptions;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Component;

/**
 * Kinyarwanda {@link SttProvider} backed by an OpenAI transcription model ({@code gpt-transcribe} by default).
 *
 * <p>
 * The OpenAI API rejects the ISO code {@code "rw"} ("Language code 'rw' is not recognized"), so no language is sent;
 * instead a short Kinyarwanda prompt steers the model, which otherwise drifts to Portuguese or Swahili. Benchmarks
 * (2026-11-13, 6 sentences) gave a word error rate of ~0.52 vs ~0.42 for MMS, but ~0.6 s instead of ~3.2 s — and the
 * downstream LLM correction + translation step absorbs most transcription slips.
 *
 * <p>
 * For resilience the provider <strong>falls back to MMS</strong> when the OpenAI call fails or returns an empty
 * transcript, mirroring {@code ProtoTtsProvider}.
 */
@Component
@Slf4j
class OpenAiSttProvider implements SttProvider {

    static final String PROVIDER_NAME = "openai";

    /** Steers the model towards Kinyarwanda, since the API has no {@code rw} language code. */
    static final String KINYARWANDA_PROMPT = "Transcription en kinyarwanda (Rwanda). Kinyarwanda speech. "
            + "Muraho, amakuru? Murakoze cyane.";

    private static final Set<String> SUPPORTED_LANGUAGES = Set.of("rw");

    private final OpenAiAudioTranscriptionModel transcriptionModel;
    private final SttConfig config;
    private final MmsSttProvider mmsFallback;

    OpenAiSttProvider(OpenAiAudioTranscriptionModel transcriptionModel, SttConfig config, MmsSttProvider mmsFallback) {
        this.transcriptionModel = transcriptionModel;
        this.config = config;
        this.mmsFallback = mmsFallback;
    }

    @Override
    public String name() {
        return PROVIDER_NAME;
    }

    @Override
    public boolean supportsLanguage(String languageCode) {
        return SUPPORTED_LANGUAGES.contains(languageCode);
    }

    @Override
    public String transcribe(byte[] audio, Locale language) {
        try {
            String transcript = callOpenAi(audio);
            if (transcript.isBlank()) {
                log.warn("OpenAI STT returned an empty transcript ({} bytes); using MMS fallback", audio.length);
                return mmsFallback.transcribe(audio, language);
            }
            return transcript;
        } catch (RuntimeException ex) {
            log.warn("OpenAI STT call failed ({}); using MMS fallback", ex.getMessage());
            return mmsFallback.transcribe(audio, language);
        }
    }

    private String callOpenAi(byte[] audio) {
        String filename = "audio." + AudioFormatSniffer.extension(audio);
        ByteArrayResource resource = new ByteArrayResource(audio) {

            @Override
            public @Nullable String getFilename() {
                // The OpenAI API picks its decoder from the file name's extension.
                return filename;
            }
        };

        OpenAiAudioTranscriptionOptions options = OpenAiAudioTranscriptionOptions.builder().model(config.openaiModel())
                .prompt(KINYARWANDA_PROMPT).build();

        AudioTranscriptionResponse response = transcriptionModel.call(new AudioTranscriptionPrompt(resource, options));
        String transcript = response.getResult() != null ? response.getResult().getOutput() : null;
        log.debug("OpenAI STT ({}): {} bytes as {} → {} chars", config.openaiModel(), audio.length, filename,
                transcript == null ? 0 : transcript.length());
        return transcript == null ? "" : transcript.strip();
    }
}
