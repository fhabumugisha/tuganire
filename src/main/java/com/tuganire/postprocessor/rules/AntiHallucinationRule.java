package com.tuganire.postprocessor.rules;

import com.tuganire.postprocessor.CorrectionRule;
import com.tuganire.postprocessor.RuleResult;
import java.util.Locale;
import java.util.Map;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Replaces known LLM-invented (hallucinated) Kinyarwanda words with correct forms.
 *
 * <p>
 * LLMs frequently invent plausible-sounding but non-existent Kinyarwanda words (e.g., "arakool", "ndziho"). These are
 * the most critical errors because they produce sentences that are syntactically Kinyarwanda but semantically empty to
 * a native speaker (PRD category 4, ARCHI section 5).
 *
 * <p>
 * The invention map is a constant; new hallucinations discovered via the feedback loop (Task 09) are added here without
 * touching logic.
 */
@Component
@Order(4)
public class AntiHallucinationRule implements CorrectionRule {

    static final String RULE_NAME = "ANTI_HALLUCINATION";

    /**
     * Known invented words → their correct native equivalents, seeded from PRD section 4 category 4. Keys are
     * lowercase; matching is case-insensitive.
     */
    private static final Map<String, String> INVENTIONS = Map.ofEntries(Map.entry("arakool", "ni cool"),
            Map.entry("ndziho", "umutegetsi wanjye"), Map.entry("numwe", "numwe"), // kept — this is valid; placeholder
                                                                                   // for future entries
            Map.entry("ikintu kiranshimisha", "ikintu kiranezeza"), Map.entry("akavugo", "ijambo"),
            Map.entry("arasome", "asomye"), Map.entry("ndagukunda cane", "ndagukunda cyane"),
            Map.entry("inkuru nzuri", "amakuru meza"), Map.entry("ikibazo kibi", "ikibazo gikomeye"),
            Map.entry("kureba neza", "kureba neza") // placeholder
    );

    @Override
    public String name() {
        return RULE_NAME;
    }

    @Override
    public RuleResult apply(String text, Locale src, Locale tgt) {
        if (!isKinyarwanda(tgt)) {
            return RuleResult.unchanged(text);
        }

        String corrected = text;
        boolean changed = false;

        for (var entry : INVENTIONS.entrySet()) {
            String pattern = "(?i)\\b" + entry.getKey() + "\\b";
            if (corrected.toLowerCase().contains(entry.getKey())) {
                String replaced = corrected.replaceAll(pattern, entry.getValue());
                if (!replaced.equals(corrected)) {
                    corrected = replaced;
                    changed = true;
                }
            }
        }

        return changed ? RuleResult.changed(corrected, "Mot inventé corrigé") : RuleResult.unchanged(text);
    }

    private static boolean isKinyarwanda(Locale locale) {
        return "rw".equals(locale.getLanguage());
    }
}
