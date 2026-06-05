package com.tuganire.postprocessor.rules;

import com.tuganire.postprocessor.CorrectionRule;
import com.tuganire.postprocessor.RuleResult;
import java.util.Locale;
import java.util.Map;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Corrects tu/vous confusion by enforcing the plural of respect.
 *
 * <p>
 * Kinyarwanda social register requires second-person plural forms when addressing strangers or in any polite
 * interaction. GPT-4o and Claude consistently use informal singular forms, which sounds rude to native speakers. This
 * rule substitutes the known-bad singular forms with correct plural-of-respect equivalents (PRD category 2).
 *
 * <p>
 * The substitution map is a constant so future enrichment from the golden dictionary is a data change, not a code
 * change.
 */
@Component
@Order(1)
public class PluralRespectRule implements CorrectionRule {

    static final String RULE_NAME = "PLURAL_RESPECT";

    /**
     * Singular → plural-of-respect substitutions drawn from PRD section 4, category 2 and ARCHI section 5. Keys are
     * lowercase; matching uses case-insensitive regex at runtime.
     */
    private static final Map<String, String> SUBSTITUTIONS = Map.of("ndakwinginze", "ndabinginze", "wamfasha",
            "mwamfasha", "mpa", "mumpe", "mbwira", "mwambwira", "nkubwira", "mwambwira", "nkugezeho", "mwagezeho",
            "nkubaza", "mwabaza", "ntegeka", "mutegeke");

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

        for (var entry : SUBSTITUTIONS.entrySet()) {
            String pattern = "(?i)" + entry.getKey();
            if (corrected.toLowerCase().contains(entry.getKey())) {
                corrected = corrected.replaceAll(pattern, entry.getValue());
                changed = true;
            }
        }

        return changed ? RuleResult.changed(corrected, "Pluriel de respect appliqué") : RuleResult.unchanged(text);
    }

    private static boolean isKinyarwanda(Locale locale) {
        return "rw".equals(locale.getLanguage());
    }
}
