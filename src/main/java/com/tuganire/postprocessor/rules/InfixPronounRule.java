package com.tuganire.postprocessor.rules;

import com.tuganire.postprocessor.CorrectionRule;
import com.tuganire.postprocessor.RuleResult;
import java.util.Locale;
import java.util.Map;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Repairs missing or incorrect pronominal infixes in Bantu verb stems.
 *
 * <p>
 * Kinyarwanda verbs carry object agreement infixes (e.g. -n- for first-person object). LLMs routinely omit these
 * infixes, producing grammatically wrong forms. For example, "kurya" (to eat, general) must be "kundya" (to eat me /
 * feed me) when the object is first-person singular (PRD category 1, ARCHI section 5).
 *
 * <p>
 * Substitution map is a constant for easy enrichment.
 */
@Component
@Order(2)
public class InfixPronounRule implements CorrectionRule {

    static final String RULE_NAME = "INFIX_PRONOUN";

    /**
     * Incorrect verb form → corrected form with proper infix, drawn from PRD section 4 category 1.
     */
    private static final Map<String, String> SUBSTITUTIONS = Map.of("kurya", "kundya", "kubyara", "kundubyara",
            "kubwira", "kumbwira", "kufasha", "kundafasha", "kurekura", "kundekura", "gutura", "kundatura", "kwambika",
            "kwambika", "kubona", "kundabona");

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
            String pattern = "(?i)\\b" + entry.getKey() + "\\b";
            if (corrected.toLowerCase().contains(entry.getKey())) {
                corrected = corrected.replaceAll(pattern, entry.getValue());
                changed = true;
            }
        }

        return changed ? RuleResult.changed(corrected, "Infixe pronominal corrigé") : RuleResult.unchanged(text);
    }

    private static boolean isKinyarwanda(Locale locale) {
        return "rw".equals(locale.getLanguage());
    }
}
