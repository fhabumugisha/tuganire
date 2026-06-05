package com.tuganire.postprocessor.rules;

import com.tuganire.postprocessor.CorrectionRule;
import com.tuganire.postprocessor.RuleResult;
import java.util.Locale;
import java.util.Map;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Replaces French syntactic calques with direct Bantu verb constructions.
 *
 * <p>
 * LLMs trained predominantly on French data tend to translate French analytical verb phrases (infinitive + complement)
 * literally, producing constructs like "mwampa kugabanyirizwa" ("give me to be discounted") instead of the direct Bantu
 * verb "mwangabanyiriza" ("discount me"). These calques are grammatically possible but unnatural and sometimes
 * ambiguous (PRD category 5, ARCHI section 5).
 *
 * <p>
 * Substitution map is a constant for easy enrichment from the golden dictionary.
 */
@Component
@Order(5)
public class AntiSyntacticCalqueRule implements CorrectionRule {

    static final String RULE_NAME = "ANTI_SYNTACTIC_CALQUE";

    /**
     * French-calque multi-word construction → direct Bantu verb equivalent, drawn from PRD section 4 category 5 and
     * ARCHI section 5.
     */
    private static final Map<String, String> CALQUES = Map.of("mwampa kugabanyirizwa", "mwangabanyiriza",
            "mpa kugabanyirizwa", "ngabanyirize", "mwampa kunywa", "mwanyweshe", "mpa kunywa", "nyweshe",
            "mwampa kurya", "mwanduhe", "mpa kurya", "mpehe", "mwampa kubona", "mwandekure", "mpa kugura", "mpere");

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

        for (var entry : CALQUES.entrySet()) {
            String pattern = "(?i)" + entry.getKey();
            if (corrected.toLowerCase().contains(entry.getKey())) {
                String replaced = corrected.replaceAll(pattern, entry.getValue());
                if (!replaced.equals(corrected)) {
                    corrected = replaced;
                    changed = true;
                }
            }
        }

        return changed
                ? RuleResult.changed(corrected, "Calque syntaxique français remplacé par verbe bantu direct")
                : RuleResult.unchanged(text);
    }

    private static boolean isKinyarwanda(Locale locale) {
        return "rw".equals(locale.getLanguage());
    }
}
