package com.tuganire.conversation;

import com.tuganire.shared.exception.ResourceNotFoundException;
import com.tuganire.translation.TranslationRequest;
import com.tuganire.translation.TranslationResponse;
import com.tuganire.translation.TranslationService;
import com.tuganire.tts.TtsService;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves ephemeral synthesised audio clips stored in Redis by {@link AudioStore}.
 *
 * <p>
 * The URL {@code /api/v1/audio/{id}.mp3} is returned inside every {@link ConversationService.AudioTranslationResult}
 * and inside {@link ConversationEvent.AudioReady} events. Entries expire after 5 minutes (see
 * {@link AudioStoreImpl#AUDIO_TTL}).
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/audio")
@RequiredArgsConstructor
class AudioController {

    private final AudioStore audioStore;
    private final TtsService ttsService;
    private final TranslationService translationService;

    /**
     * Streams the MP3 audio bytes for the given clip id.
     *
     * @param id
     *            the clip identifier embedded in the URL
     * @return 200 with {@code Content-Type: audio/mpeg} or 404 if the entry has expired or never existed
     */
    @GetMapping(value = "/{id}.mp3", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> getAudio(@PathVariable String id) {
        log.debug("Audio fetch: id={}", id);
        byte[] bytes = audioStore.getAudio(id)
                .orElseThrow(() -> new ResourceNotFoundException("Audio clip not found or expired: " + id));
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("audio/mpeg")).body(bytes);
    }

    /**
     * Synthesises {@code text} on demand and streams it. Used by the translation bubble so the text appears as soon as
     * the translation is ready, with the (slower) TTS happening lazily when the browser fetches this URL — instead of
     * blocking {@code POST /translate}. Degrades to 204 (silent) on synthesis failure so the text bubble is unaffected.
     *
     * @param lang
     *            BCP-47 language code to speak ({@code "rw"} / {@code "fr"})
     * @param text
     *            the text to synthesise
     * @return 200 with audio bytes, 400 if text is blank, or 204 if synthesis failed
     */
    @GetMapping(value = "/speak.mp3", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> speak(@RequestParam("lang") String lang, @RequestParam("text") String text) {
        if (text == null || text.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        try {
            byte[] bytes = ttsService.synthesize(text, Locale.forLanguageTag(lang));
            return ResponseEntity.ok().contentType(MediaType.parseMediaType("audio/mpeg")).body(bytes);
        } catch (Exception ex) {
            log.warn("Lazy TTS synthesis failed (lang={}): {}", lang, ex.getMessage());
            return ResponseEntity.noContent().build();
        }
    }

    /**
     * Back-translation audio: translates {@code text} from {@code from} to {@code to}, then speaks the result in
     * {@code to}. Lets the user hear a bubble's text rendered in the other language (e.g. hear the French meaning of a
     * Kinyarwanda translation) to verify it. Degrades to 204 (silent) on any failure so the bubble is unaffected.
     *
     * @param text
     *            the text to translate then speak
     * @param from
     *            BCP-47 code the text is written in
     * @param to
     *            BCP-47 code to translate into and speak
     * @return 200 with audio bytes, 400 if text is blank, or 204 on failure
     */
    @GetMapping(value = "/translate-speak.mp3", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> translateAndSpeak(@RequestParam("text") String text,
            @RequestParam("from") String from, @RequestParam("to") String to) {
        if (text == null || text.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        try {
            Locale toLocale = Locale.forLanguageTag(to);
            TranslationResponse response = translationService.translate(
                    new TranslationRequest(text, Locale.forLanguageTag(from), toLocale, UUID.randomUUID().toString()));
            String translated = response.translatedText();
            if (translated == null || translated.isBlank()) {
                return ResponseEntity.noContent().build();
            }
            byte[] bytes = ttsService.synthesize(translated, toLocale);
            return ResponseEntity.ok().contentType(MediaType.parseMediaType("audio/mpeg")).body(bytes);
        } catch (Exception ex) {
            log.warn("Translate+speak failed ({}->{}): {}", from, to, ex.getMessage());
            return ResponseEntity.noContent().build();
        }
    }
}
