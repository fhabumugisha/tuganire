package com.tuganire.stt;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/**
 * Declarative HTTP client for the Python MMS-ASR endpoint (speech-to-text).
 *
 * <p>
 * Registered via {@link MmsSttClientConfig} bound to {@code tuganire.mms.base-url} — the same Python server that hosts
 * MMS-TTS. The recorded audio bytes are POSTed raw (the server decodes WebM/Opus, MP4, WAV… via ffmpeg) with the target
 * language as a query parameter.
 *
 * @see MmsSttResponse
 */
@HttpExchange
public interface MmsSttClient {

    /**
     * Transcribes raw audio bytes to text using the MMS-ASR model.
     *
     * @param audio
     *            raw recorded audio bytes (WebM/Opus from {@code MediaRecorder}, etc.)
     * @param lang
     *            app language code (e.g. {@code "rw"})
     * @return the transcription response
     */
    @PostExchange(url = "/stt", contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE, accept = MediaType.APPLICATION_JSON_VALUE)
    MmsSttResponse transcribe(@RequestBody byte[] audio, @RequestParam("lang") String lang);

    /**
     * Response from the MMS-ASR {@code POST /stt} endpoint.
     *
     * @param text
     *            the transcribed text
     */
    record MmsSttResponse(String text) {
    }
}
