package com.tuganire.postprocessor;

import java.util.Locale;

/**
 * A single post-processing correction rule applied to translated text.
 *
 * <p>
 * Rules are composable: the pipeline in Task 12 chains them, feeding each rule's output text into the next.
 * Implementations live in the {@code rules} sub-package.
 */
public interface CorrectionRule {

    /**
     * Applies this correction to the given translated text.
     *
     * @param text
     *            the text to correct (output of the previous rule or raw translation)
     * @param src
     *            the source language locale
     * @param tgt
     *            the target language locale
     * @return a {@link RuleResult} describing the (possibly modified) text and whether a change was made
     */
    RuleResult apply(String text, Locale src, Locale tgt);

    /**
     * Returns the rule's stable identifier used in audit logs.
     *
     * @return rule name (e.g. {@code "french-accent-normalizer"})
     */
    String name();
}
