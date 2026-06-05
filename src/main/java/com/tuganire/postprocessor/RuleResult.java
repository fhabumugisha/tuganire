package com.tuganire.postprocessor;

/**
 * Outcome of applying a single {@link CorrectionRule} to translated text.
 *
 * <p>
 * Use the static factories {@link #unchanged} and {@link #changed} rather than the record constructor directly.
 *
 * @param text
 *            the (possibly corrected) text
 * @param changed
 *            {@code true} if this rule modified the text
 * @param explanation
 *            human-readable reason for the change; empty string when unchanged
 */
public record RuleResult(String text, boolean changed, String explanation) {

    /**
     * Returns a result indicating the rule did not modify the text.
     *
     * @param text
     *            the original text passed through unchanged
     * @return a result with {@code changed = false}
     */
    public static RuleResult unchanged(String text) {
        return new RuleResult(text, false, "");
    }

    /**
     * Returns a result indicating the rule modified the text.
     *
     * @param text
     *            the corrected text
     * @param explanation
     *            why the correction was applied
     * @return a result with {@code changed = true}
     */
    public static RuleResult changed(String text, String explanation) {
        return new RuleResult(text, true, explanation);
    }
}
