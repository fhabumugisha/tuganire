package com.tuganire.conversation;

import com.tuganire.conversation.StreamTranslationService.StreamTranslationRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Server-Sent Events endpoint that streams a translation pipeline to the browser.
 *
 * <p>
 * {@code GET /api/v1/stream/translate} emits, in order: the corrected SOURCE text token by token ({@code correction} +
 * {@code correction-done}), the TRANSLATION token by token ({@code translation} + {@code translation-done} — the latter
 * carrying the final text and a lazy TTS {@code audioUrl}), then a terminal {@code done} event. An exact
 * golden-dictionary hit short-circuits the translation: {@code translation-done} is emitted directly with no
 * {@code translation} token events. Failures surface as an {@code error} event.
 *
 * <p>
 * Mounted under {@code /api/v1/**} (CSRF-exempt + {@code permitAll}; see {@code SecurityConfig}). The actual pipeline
 * runs on a virtual thread inside {@link StreamTranslationService}; this controller only creates the emitter.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/stream")
@RequiredArgsConstructor
@Tag(name = "Streaming translation", description = "SSE pipeline: streamed source correction, streamed translation, final audio URL")
public class StreamTranslationController {

    /** No timeout: the virtual-thread pipeline completes the emitter itself; the LLM stream bounds the duration. */
    private static final long NO_TIMEOUT = 0L;

    private final StreamTranslationService streamTranslationService;

    /**
     * Opens an SSE stream that corrects {@code text}, then translates it from {@code sourceLang} to {@code targetLang},
     * emitting tokens as they arrive.
     *
     * @param text
     *            the source transcript to correct and translate (URL-encoded by the client); non-blank
     * @param sourceLang
     *            BCP-47 source language code ({@code "fr"} or {@code "rw"})
     * @param targetLang
     *            BCP-47 target language code ({@code "rw"} or {@code "fr"})
     * @param sessionId
     *            optional anonymous session id; may be omitted
     * @return the {@link SseEmitter} Spring MVC streams to the client as {@code text/event-stream}
     */
    @GetMapping(value = "/translate", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Stream a translation", description = "Streams corrected source then translation token by token, ending with the full translation and a TTS audio URL")
    public SseEmitter streamTranslate(@RequestParam("text") String text, @RequestParam("sourceLang") String sourceLang,
            @RequestParam("targetLang") String targetLang,
            @RequestParam(name = "sessionId", required = false) String sessionId) {

        log.debug("GET /api/v1/stream/translate: src={} tgt={} session={} textLen={}", sourceLang, targetLang,
                sessionId, text == null ? 0 : text.length());

        SseEmitter emitter = new SseEmitter(NO_TIMEOUT);
        streamTranslationService.stream(emitter, new StreamTranslationRequest(text, sourceLang, targetLang, sessionId));
        return emitter;
    }
}
