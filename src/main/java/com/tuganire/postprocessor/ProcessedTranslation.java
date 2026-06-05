package com.tuganire.postprocessor;

import java.util.List;

/**
 * The result of running all {@link CorrectionRule}s over a raw translation.
 *
 * @param text
 *            the final corrected text
 * @param appliedCorrections
 *            names of the rules that modified the text, in application order
 */
public record ProcessedTranslation(String text, List<String> appliedCorrections) {
}
