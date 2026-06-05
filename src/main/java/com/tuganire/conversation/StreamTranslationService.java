package com.tuganire.conversation;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Drives the server-side SSE translation pipeline: streams the corrected source text token by token, then the
 * translation token by token, and finally a {@code translation-done} event carrying the full translation plus a lazy
 * TTS audio URL.
 *
 * <p>
 * The pipeline runs on a virtual thread; the returned {@link SseEmitter} is completed (or completed with error) by the
 * implementation when the work finishes.
 */
public interface StreamTranslationService {

    /**
     * Submits the streaming translation pipeline for {@code request} onto a virtual thread and returns the emitter the
     * controller hands back to Spring MVC.
     *
     * @param emitter
     *            the SSE emitter to write events to; the implementation owns its lifecycle (complete / error)
     * @param request
     *            the validated stream request (source text + languages + optional session id)
     */
    void stream(SseEmitter emitter, StreamTranslationRequest request);

    /**
     * Parameters for a single streaming translation.
     *
     * @param text
     *            the source transcript to correct and translate; non-blank
     * @param sourceLang
     *            BCP-47 source language code ({@code "fr"} or {@code "rw"})
     * @param targetLang
     *            BCP-47 target language code ({@code "rw"} or {@code "fr"})
     * @param sessionId
     *            the anonymous session id; may be {@code null} or blank
     */
    record StreamTranslationRequest(String text, String sourceLang, String targetLang, String sessionId) {
    }
}
