package com.tuganire.postprocessor;

import com.tuganire.postprocessor.rules.AntiHallucinationRule;
import com.tuganire.postprocessor.rules.AntiPolitenessStackingRule;
import com.tuganire.postprocessor.rules.AntiSyntacticCalqueRule;
import com.tuganire.postprocessor.rules.InfixPronounRule;
import com.tuganire.postprocessor.rules.PluralRespectRule;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Applies the five Kinyarwanda correction rules to raw LLM output.
 *
 * <p>
 * Rule application order (informed by PRD section 4 and ARCHI section 5):
 * <ol>
 * <li>{@link PluralRespectRule} — correct tu/vous confusion first; downstream rules assume correct respect register so
 * they match the right patterns</li>
 * <li>{@link InfixPronounRule} — fix missing pronominal infixes before calque detection to avoid double-correcting
 * compound forms</li>
 * <li>{@link AntiPolitenessStackingRule} — collapse stacked politeness; runs after plural-respect so the surviving form
 * is already in correct register</li>
 * <li>{@link AntiHallucinationRule} — replace invented words; runs before calque cleanup so hallucinated forms inside
 * calque constructs are caught correctly</li>
 * <li>{@link AntiSyntacticCalqueRule} — replace French calques last; by this point the text contains corrected,
 * non-hallucinated forms, giving cleaner substitution targets</li>
 * </ol>
 *
 * <p>
 * Invoked by {@code TranslationService} on every LLM output that is not a golden-dictionary hit and not a cache hit.
 * Rules that detect no match return their input unchanged and are not recorded in
 * {@link ProcessedTranslation#appliedCorrections()}.
 */
@Component
public class KinyarwandaPostProcessor {

    private final List<CorrectionRule> rules;

    public KinyarwandaPostProcessor(PluralRespectRule pluralRespectRule, InfixPronounRule infixPronounRule,
            AntiPolitenessStackingRule antiPolitenessStackingRule, AntiHallucinationRule antiHallucinationRule,
            AntiSyntacticCalqueRule antiSyntacticCalqueRule) {
        // Order is documented in the class-level Javadoc above; do not reorder without updating it.
        this.rules = List.of(pluralRespectRule, infixPronounRule, antiPolitenessStackingRule, antiHallucinationRule,
                antiSyntacticCalqueRule);
    }

    /**
     * Applies all correction rules in order to the LLM output.
     *
     * @param llmOutput
     *            raw text returned by the LLM
     * @param src
     *            source language locale
     * @param tgt
     *            target language locale
     * @return the corrected text together with the names of every rule that changed it
     */
    public ProcessedTranslation process(String llmOutput, Locale src, Locale tgt) {
        String current = llmOutput;
        List<String> applied = new ArrayList<>();

        for (var rule : rules) {
            var result = rule.apply(current, src, tgt);
            if (result.changed()) {
                applied.add(rule.name());
                current = result.text();
            }
        }

        return new ProcessedTranslation(current, applied);
    }
}
