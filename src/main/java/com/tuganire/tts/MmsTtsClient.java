package com.tuganire.tts;

import java.util.List;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/**
 * Declarative HTTP client for the Python MMS-TTS server (Task 02).
 *
 * <p>
 * Registered via {@link MmsTtsClientConfig} bound to {@code tuganire.mms.base-url}. Spring Framework 7 generates the
 * proxy at application start — no manual {@code RestTemplate} or {@code WebClient} wiring needed.
 *
 * @see MmsTtsRequest
 * @see MmsHealthResponse
 */
@HttpExchange
public interface MmsTtsClient {

    /**
     * Synthesises speech and returns raw audio bytes from the MMS model.
     *
     * @param req
     *            the synthesis request (text + lang)
     * @return raw audio bytes
     */
    @PostExchange("/tts")
    byte[] synthesize(@RequestBody MmsTtsRequest req);

    /**
     * Checks the health of the Python MMS-TTS server.
     *
     * @return health status including device and supported languages
     */
    @GetExchange("/health")
    MmsHealthResponse health();

    // ---------------------------------------------------------------------------
    // Nested request / response records
    // ---------------------------------------------------------------------------

    /**
     * Request body sent to the MMS-TTS {@code POST /tts} endpoint.
     *
     * @param text
     *            text to synthesise
     * @param lang
     *            BCP-47 language code (e.g. {@code "rw"}, {@code "fr"})
     * @param pauses
     *            when {@code true}, the server inserts natural pauses at punctuation for clearer articulation
     */
    record MmsTtsRequest(String text, String lang, boolean pauses) {
    }

    /**
     * Response from the MMS-TTS {@code GET /health} endpoint.
     *
     * @param status
     *            server status string (e.g. {@code "ok"})
     * @param device
     *            compute device in use (e.g. {@code "cpu"}, {@code "cuda"})
     * @param languages
     *            list of BCP-47 language codes supported by this server
     */
    record MmsHealthResponse(String status, String device, List<String> languages) {
    }
}
