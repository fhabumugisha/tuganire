package com.tuganire.tts;

import java.time.Duration;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Calls the OpenAI {@code POST /v1/audio/speech} endpoint directly to synthesise "steered" speech, passing the
 * {@code gpt-4o-mini-tts} {@code instructions} field to control articulation and pacing.
 *
 * <p>
 * Spring AI 2.0.0-M6's {@code OpenAiAudioSpeechOptions} does not yet expose {@code instructions}, so this thin
 * {@link RestClient} wrapper is used only by the A/B voice comparison (the "articulated" Kinyarwanda candidate). The
 * default delivery instructions are tuned for clear, well-articulated Kinyarwanda with natural pauses; override with
 * {@code tuganire.tts.steer-instructions}.
 */
@Component
@Slf4j
class OpenAiSteeredSpeechClient {

    static final String VARIANT_NAME = "openai-steered";

    private static final String SPEECH_PATH = "/v1/audio/speech";
    private static final String MODEL = "gpt-4o-mini-tts";
    private static final String VOICE = "alloy";
    private static final String RESPONSE_FORMAT = "mp3";

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

    /**
     * Default delivery instructions. Kept free of {@code :} and {@code }} so it remains a valid SpEL placeholder
     * default in the constructor annotation below.
     */
    private static final String DEFAULT_INSTRUCTIONS = "Speak slowly and clearly. Articulate every syllable with a"
            + " calm, warm and friendly tone, and insert natural pauses at punctuation so the sentence is easy to"
            + " follow.";

    private final RestClient restClient;
    private final String apiKey;
    private final String instructions;

    OpenAiSteeredSpeechClient(@Value("${spring.ai.openai.api-key:}") String apiKey,
            @Value("${spring.ai.openai.base-url:https://api.openai.com}") String baseUrl,
            @Value("${tuganire.tts.steer-instructions:" + DEFAULT_INSTRUCTIONS + "}") String instructions) {
        this.apiKey = apiKey;
        this.instructions = instructions;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        // Build the client from the static factory: this app does not expose a RestClient.Builder bean.
        this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
    }

    /**
     * Synthesises {@code text} with steered articulation and returns the MP3 audio bytes.
     *
     * @param text
     *            the text to speak
     * @return MP3 audio bytes
     */
    byte[] synthesize(String text) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("OpenAI API key is not configured; cannot synthesise the steered voice");
        }
        log.debug("OpenAI steered TTS synthesize: text length={}", text.length());
        Map<String, Object> body = Map.of("model", MODEL, "voice", VOICE, "input", text, "instructions", instructions,
                "response_format", RESPONSE_FORMAT);
        try {
            return restClient.post().uri(SPEECH_PATH).header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(byte[].class);
        } catch (RestClientException ex) {
            log.warn("OpenAI steered TTS call failed: {}", ex.getMessage());
            throw new IllegalStateException("OpenAI steered TTS synthesis failed", ex);
        }
    }
}
