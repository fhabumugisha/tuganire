package com.tuganire.conversation;

import java.util.function.Consumer;

/**
 * Splits the token stream of the merged "correct + translate" LLM call into its two parts.
 *
 * <p>
 * The model writes the corrected source sentence, a separator line, then the translation (see
 * {@code TranslationPromptBuilder#buildCorrectAndTranslateSystemPrompt}). Tokens before the separator are forwarded to
 * {@code onCorrection}, tokens after it to {@code onTranslation}; {@code onSplit} fires once, as soon as the separator
 * is seen, with the full correction text. Because a chunk boundary can fall inside the separator, the last
 * {@code separator.length() - 1} characters of the correction are held back until the next chunk disambiguates them.
 *
 * <p>
 * Not thread-safe: one instance per stream, fed from a single thread.
 */
final class CorrectionTranslationSplitter {

    private final String separator;
    private final Consumer<String> onCorrection;
    private final Consumer<String> onTranslation;
    private final Consumer<String> onSplit;

    private final StringBuilder pending = new StringBuilder();
    private final StringBuilder correction = new StringBuilder();
    private final StringBuilder translation = new StringBuilder();
    private boolean split;

    CorrectionTranslationSplitter(String separator, Consumer<String> onCorrection, Consumer<String> onTranslation,
            Consumer<String> onSplit) {
        this.separator = separator;
        this.onCorrection = onCorrection;
        this.onTranslation = onTranslation;
        this.onSplit = onSplit;
    }

    /** Feeds the next streamed chunk. */
    void accept(String chunk) {
        if (chunk == null || chunk.isEmpty()) {
            return;
        }
        if (split) {
            emitTranslation(chunk);
            return;
        }
        pending.append(chunk);
        int idx = pending.indexOf(separator);
        if (idx >= 0) {
            emitCorrection(pending.substring(0, idx));
            String rest = pending.substring(idx + separator.length());
            pending.setLength(0);
            split = true;
            onSplit.accept(correction.toString().trim());
            emitTranslation(rest);
            return;
        }
        int safe = pending.length() - (separator.length() - 1);
        if (safe > 0) {
            emitCorrection(pending.substring(0, safe));
            pending.delete(0, safe);
        }
    }

    /** Flushes held-back characters once the stream has ended. */
    void finish() {
        if (!split && !pending.isEmpty()) {
            emitCorrection(pending.toString());
            pending.setLength(0);
        }
    }

    /** {@code true} once the separator has been seen (i.e. the model followed the two-part format). */
    boolean isSplit() {
        return split;
    }

    /** The full correction text (trimmed). */
    String correction() {
        return correction.toString().trim();
    }

    /** The full translation text (trimmed). */
    String translation() {
        return translation.toString().trim();
    }

    private void emitCorrection(String text) {
        // Skip leading whitespace so the first token does not start with a newline.
        String out = correction.isEmpty() ? text.stripLeading() : text;
        if (!out.isEmpty()) {
            correction.append(out);
            onCorrection.accept(out);
        }
    }

    private void emitTranslation(String text) {
        // The separator sits on its own line: drop the newline(s) that follow it.
        String out = translation.isEmpty() ? text.stripLeading() : text;
        if (!out.isEmpty()) {
            translation.append(out);
            onTranslation.accept(out);
        }
    }
}
