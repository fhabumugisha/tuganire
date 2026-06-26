package com.tuganire.web;

import com.tuganire.feedback.FeedbackRequest;
import com.tuganire.feedback.FeedbackService;
import com.tuganire.feedback.FeedbackType;
import com.tuganire.translation.TranslationRequest;
import com.tuganire.translation.TranslationResponse;
import com.tuganire.translation.TranslationService;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Thymeleaf controller for the Tuganire "deux boutons" web POC.
 *
 * <p>
 * {@code GET /} renders the main conversation screen. {@code POST /translate} is the HTMX endpoint: it accepts a
 * transcript submitted by the Alpine/Web Speech API client, delegates to {@link TranslationService}, and returns the
 * {@code fragments/translation :: bubble} partial so HTMX can swap it directly into the conversation list.
 *
 * <p>
 * {@code GET /settings} renders the settings screen (TTS provider selector, language selector). {@code GET /onboarding}
 * renders the 3-step onboarding overlay page.
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class WebController {

    private final TranslationService translationService;
    private final FeedbackService feedbackService;

    /**
     * Renders the main "deux boutons" / split-screen conversation screen.
     *
     * @return the logical view name resolved by Thymeleaf
     */
    @GetMapping("/")
    public String index() {
        return "index";
    }

    /**
     * Renders the text-translation screen: a tourist pastes text (e.g. a Kinyarwanda SMS received by text message) into
     * a field and translates it. Reuses the {@code POST /translate} HTMX endpoint and the
     * {@code fragments/translation :: bubble} fragment, so the translation is also read aloud.
     *
     * @return the logical view name resolved by Thymeleaf
     */
    @GetMapping("/text")
    public String text() {
        return "text";
    }

    /**
     * Renders the settings screen (TTS provider selector and UI language selector).
     *
     * @return the logical view name resolved by Thymeleaf
     */
    @GetMapping("/settings")
    public String settings() {
        return "settings";
    }

    /**
     * Renders the 3-step skippable onboarding page.
     *
     * @return the logical view name resolved by Thymeleaf
     */
    @GetMapping("/onboarding")
    public String onboarding() {
        return "onboarding";
    }

    /**
     * HTMX translate endpoint: receives a speech transcript, translates it, and returns the
     * {@code translation :: bubble} Thymeleaf fragment for insertion into the conversation list.
     *
     * @param sourceText
     *            the transcript to translate; non-blank
     * @param sessionId
     *            the anonymous session UUID; may be empty (a fallback UUID is used)
     * @param sourceLang
     *            BCP-47 source language code ({@code "fr"} or {@code "rw"})
     * @param targetLang
     *            BCP-47 target language code ({@code "rw"} or {@code "fr"})
     * @param model
     *            the Spring MVC model
     * @return the fragment view name {@code "fragments/translation :: bubble"}
     */
    @PostMapping("/translate")
    public String translate(@RequestParam(name = "sourceText") String sourceText,
            @RequestParam(name = "sessionId", defaultValue = "") String sessionId,
            @RequestParam(name = "sourceLang", defaultValue = "fr") String sourceLang,
            @RequestParam(name = "targetLang", defaultValue = "rw") String targetLang, Model model) {

        String effectiveSession = sessionId.isBlank() ? UUID.randomUUID().toString() : sessionId;

        log.debug("POST /translate: src={} tgt={} session={} text='{}'", sourceLang, targetLang, effectiveSession,
                sourceText.substring(0, Math.min(50, sourceText.length())));

        TranslationRequest request = new TranslationRequest(sourceText, Locale.forLanguageTag(sourceLang),
                Locale.forLanguageTag(targetLang), effectiveSession);

        TranslationResponse response = translationService.translate(request);

        // Unique ID for this bubble; used for HTMX feedback targeting
        String translationId = UUID.randomUUID().toString();

        model.addAttribute("originalText", response.originalText());
        model.addAttribute("translatedText", response.translatedText());
        model.addAttribute("translationId", translationId);
        model.addAttribute("sessionId", effectiveSession);
        // The bubble fragment colors/labels each side by its own language (FR=primary, RW=accent)
        // so RW→FR conversations don't appear mislabeled. Lowercase BCP-47 codes are expected.
        model.addAttribute("sourceLang", sourceLang.toLowerCase(Locale.ROOT));
        model.addAttribute("targetLang", targetLang.toLowerCase(Locale.ROOT));
        // Audio is NOT synthesised here. The bubble fragment references GET /api/v1/audio/speak.mp3,
        // which synthesises lazily when the browser loads it — so the text bubble appears immediately
        // instead of waiting for the (slower) TTS to finish.

        return "fragments/translation :: bubble";
    }

    /**
     * HTMX feedback endpoint: records a thumbs-up/down for a translation and returns the {@code feedback :: thanks}
     * fragment that replaces the rating buttons.
     *
     * <p>
     * This mirrors {@link #translate} (form-encoded HTMX POST returning a Thymeleaf fragment). The JSON REST equivalent
     * lives at {@code POST /api/v1/feedback} for programmatic clients.
     *
     * @param translationId
     *            id of the translation being rated; non-blank
     * @param sessionId
     *            anonymous session id; non-blank
     * @param type
     *            feedback category ({@code THUMBS_UP} / {@code THUMBS_DOWN})
     * @return the fragment view name {@code "fragments/feedback :: thanks"}
     */
    @PostMapping("/feedback")
    public String feedback(@RequestParam(name = "translationId") String translationId,
            @RequestParam(name = "sessionId") String sessionId, @RequestParam(name = "type") FeedbackType type) {

        log.debug("POST /feedback: session={} translation={} type={}", sessionId, translationId, type);
        feedbackService.submit(new FeedbackRequest(sessionId, translationId, type, null));
        return "fragments/feedback :: thanks";
    }
}
