package com.tuganire.tts;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * A/B comparison of Kinyarwanda TTS voices: synthesise the same text with two voices, let the user vote for the
 * better-sounding one, and expose per-voice preference totals for {@code /admin}.
 *
 * <ul>
 * <li>{@code GET /api/v1/tts/compare} — synthesise both voices for a blind A/B test</li>
 * <li>{@code POST /api/v1/tts/compare-vote} — record which voice the user preferred</li>
 * <li>{@code GET /api/v1/tts/compare-stats} — per-voice preference totals</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/tts")
@RequiredArgsConstructor
public class TtsCompareController {

    private static final String DEFAULT_LANG = "rw";

    private final TtsVoiceComparisonService comparisonService;

    /**
     * Synthesises {@code text} with each compared voice variant and returns the shuffled candidates.
     *
     * @param text
     *            the Kinyarwanda text to speak
     * @param lang
     *            BCP-47 language code (defaults to {@code "rw"})
     * @return 200 with one candidate per voice variant, or 400 if {@code text} is blank
     */
    @GetMapping("/compare")
    @Operation(summary = "Compare Kinyarwanda voices", description = "Synthesise the same text with two TTS voices for A/B comparison")
    public ResponseEntity<TtsVoiceCompareResponse> compare(@RequestParam("text") String text,
            @RequestParam(name = "lang", defaultValue = DEFAULT_LANG) String lang) {
        if (text == null || text.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(new TtsVoiceCompareResponse(text, comparisonService.compare(text, lang)));
    }

    /**
     * Records the user's preferred voice for one comparison.
     *
     * @param request
     *            the choice payload; {@code chosenVariant} is required
     * @return 200 (no body)
     */
    @PostMapping("/compare-vote")
    @Operation(summary = "Record A/B voice choice", description = "Record which Kinyarwanda voice the user preferred")
    public ResponseEntity<Void> recordVote(@Valid @RequestBody TtsVoiceChoiceRequest request) {
        comparisonService.recordChoice(request.chosenVariant(), request.rejectedVariant(), request.sessionId());
        return ResponseEntity.ok().build();
    }

    /**
     * Returns the per-voice A/B preference totals.
     *
     * @return 200 with one entry per voice variant that has received at least one vote
     */
    @GetMapping("/compare-stats")
    @Operation(summary = "A/B voice stats", description = "Per-voice preference totals for Kinyarwanda reading")
    public ResponseEntity<List<TtsVoiceStat>> compareStats() {
        return ResponseEntity.ok(comparisonService.stats());
    }
}
