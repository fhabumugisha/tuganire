package com.tuganire.translation.normalizer;

import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Rule-based normalizer for transcribed French text. Applied before golden-dictionary lookup and LLM translation (FR
 * source only; RW source skips this step). Normalization is idempotent: calling {@code normalize} on an
 * already-normalized string produces the same output.
 */
@Component
public class FrenchNormalizer {

    private static final Pattern HEDGE_PATTERN = Pattern.compile(NormalizerConstants.HEDGE_REGEX_FRAGMENT,
            Pattern.CASE_INSENSITIVE);

    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");

    /**
     * Normalizes French text for lookup and translation.
     *
     * <ol>
     * <li>Trims leading/trailing whitespace.
     * <li>Lowercases for comparison, then applies slang→standard substitutions.
     * <li>Strips hedge tokens ({@link NormalizerConstants#HEDGES}).
     * <li>Collapses runs of whitespace to a single space.
     * </ol>
     *
     * @param frenchText
     *            raw transcribed French; non-null
     * @return normalized text suitable for dictionary lookup; never null
     */
    public String normalize(String frenchText) {
        if (frenchText == null || frenchText.isBlank()) {
            return "";
        }

        // Work in lowercase for matching, but preserve the normalised standard forms.
        String text = frenchText.trim().toLowerCase();

        // Apply slang-to-standard substitutions (longest keys first to avoid partial mismatches).
        text = applySlangSubstitutions(text);

        // Strip hedges — patterns already include word boundaries.
        if (!NormalizerConstants.HEDGE_REGEX_FRAGMENT.isEmpty()) {
            text = HEDGE_PATTERN.matcher(text).replaceAll(" ");
        }

        // Collapse internal whitespace and re-trim.
        text = WHITESPACE_PATTERN.matcher(text).replaceAll(" ").strip();

        return text;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private String applySlangSubstitutions(String text) {
        // Sort entries by key-length descending so longer phrases take priority.
        var entries = NormalizerConstants.SLANG_TO_STANDARD.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getKey().length(), a.getKey().length())).toList();

        for (Map.Entry<String, String> entry : entries) {
            String slang = entry.getKey();
            String standard = entry.getValue();
            // Pattern.quote escapes the slang string; \b provides word-boundary anchoring.
            Pattern p = Pattern.compile("\\b" + Pattern.quote(slang) + "\\b", Pattern.CASE_INSENSITIVE);
            text = p.matcher(text).replaceAll(standard);
        }
        return text;
    }
}
