package com.tuganire.translation;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for text translation.
 *
 * <p>
 * Thin adapter: validates the incoming HTTP request, delegates all logic to {@link TranslationService}, and returns a
 * {@link TranslationResponse} DTO. Native API versioning via Spring 7 path-segment strategy yields
 * {@code /api/v1/translate}.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Translation", description = "Translate text between French and Kinyarwanda")
public class TranslationController {

    private final TranslationService translationService;

    /**
     * Translates a piece of text from a source language to a target language.
     *
     * @param httpRequest
     *            the validated translation request payload
     * @return 200 with the translation result
     */
    @PostMapping("/translate")
    @Operation(summary = "Translate text", description = "Translate text from the source locale to the target locale")
    public ResponseEntity<TranslationResponse> translate(@Valid @RequestBody TranslateHttpRequest httpRequest) {

        log.debug("POST /translate: src={} tgt={} text='{}'", httpRequest.sourceLanguage(),
                httpRequest.targetLanguage(),
                httpRequest.sourceText().substring(0, Math.min(50, httpRequest.sourceText().length())));

        TranslationRequest request = new TranslationRequest(httpRequest.sourceText(),
                Locale.forLanguageTag(httpRequest.sourceLanguage()),
                Locale.forLanguageTag(httpRequest.targetLanguage()), httpRequest.sessionId());

        TranslationResponse response = translationService.translate(request);
        return ResponseEntity.ok(response);
    }

    // ── HTTP request DTO ─────────────────────────────────────────────────────

    /**
     * HTTP request payload for {@code POST /api/v1/translate}.
     *
     * <p>
     * Kept separate from the service-layer {@link TranslationRequest} so that Bean Validation ({@code @NotBlank}) can
     * gate the controller before any business logic runs.
     *
     * @param sourceText
     *            text to translate; required, non-blank
     * @param sourceLanguage
     *            BCP-47 language tag of the source (e.g. {@code "fr"})
     * @param targetLanguage
     *            BCP-47 language tag of the target (e.g. {@code "rw"})
     * @param sessionId
     *            optional session identifier for caching correlation
     */
    public record TranslateHttpRequest(@NotBlank(message = "sourceText must not be blank") String sourceText,
            @NotBlank(message = "sourceLanguage must not be blank") String sourceLanguage,
            @NotBlank(message = "targetLanguage must not be blank") String targetLanguage, @NotNull String sessionId) {
    }
}
