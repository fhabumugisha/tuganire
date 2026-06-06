package com.tuganire.tts;

import java.util.List;

/**
 * Response of {@code GET /api/v1/tts/compare}: the spoken text plus one synthesised candidate per compared voice
 * variant, in randomised order for a blind A/B test.
 *
 * @param text
 *            the Kinyarwanda text that was synthesised
 * @param candidates
 *            the synthesised candidates, one per compared voice variant (shuffled)
 */
public record TtsVoiceCompareResponse(String text, List<TtsVoiceCandidate> candidates) {
}
