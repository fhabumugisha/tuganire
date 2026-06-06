package com.tuganire.tts;

/**
 * One synthesised Kinyarwanda voice, shown as an A/B candidate the user can play and pick.
 *
 * <p>
 * The {@code variantId} is the stable internal id used when recording the vote; the UI shows a neutral, numbered label
 * ("Voix 1" / "Voix 2") so the blind test is not biased by the provider name.
 *
 * @param variantId
 *            the voice variant id that produced this clip (e.g. {@code "mms-pauses"})
 * @param label
 *            human-readable voice label (used only by the admin stats, not the blind A/B UI)
 * @param audioUrl
 *            the ephemeral {@code /api/v1/audio/{id}.mp3} URL streaming this clip
 */
public record TtsVoiceCandidate(String variantId, String label, String audioUrl) {
}
