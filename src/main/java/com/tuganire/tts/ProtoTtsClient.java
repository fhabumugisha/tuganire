package com.tuganire.tts;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/**
 * Declarative HTTP client for the Proto.cx Voice API (native Kinyarwanda TTS).
 *
 * <p>
 * Registered via {@link ProtoTtsClientConfig}, which sets the base URL and the {@code Authorization: Bearer <token>}
 * header from {@link com.tuganire.config.ProtoTtsConfig}. The endpoint returns the audio file bytes directly (MP3), so
 * the method returns {@code byte[]} just like {@link MmsTtsClient}.
 *
 * @see ProtoTtsRequest
 */
@HttpExchange
public interface ProtoTtsClient {

    /**
     * Synthesises speech and returns the raw audio bytes from Proto.
     *
     * @param subcompanyId
     *            the Proto teamspace id (path segment)
     * @param req
     *            the synthesis request (text + lang + format + gender)
     * @return raw MP3 audio bytes
     */
    @PostExchange("/platform/v1/voice/{subcompanyId}/tts")
    byte[] synthesize(@PathVariable("subcompanyId") String subcompanyId, @RequestBody ProtoTtsRequest req);

    /**
     * Request body sent to the Proto {@code POST .../tts} endpoint.
     *
     * @param text
     *            text to synthesise (max 5000 chars)
     * @param lang
     *            language code ({@code "rw"} Kinyarwanda, {@code "kj"} Oshiwambo)
     * @param responseFormat
     *            audio format ({@code "mp3"} or {@code "wav"}); serialised as {@code response_format}
     * @param gender
     *            voice gender ({@code "female"} or {@code "male"})
     */
    record ProtoTtsRequest(String text, String lang, @JsonProperty("response_format") String responseFormat,
            String gender) {
    }
}
