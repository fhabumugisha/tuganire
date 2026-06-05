package com.tuganire.postprocessor.rules;

import com.tuganire.postprocessor.CorrectionRule;
import com.tuganire.postprocessor.RuleResult;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Collapses stacked politeness markers to a single canonical form.
 *
 * <p>
 * LLMs occasionally combine multiple politeness expressions in a single sentence (e.g., "Mwambabarira ...
 * ndabinginze"), which sounds socially bizarre to native speakers (PRD category 3, ARCHI section 5). This rule detects
 * and simplifies such constructs, retaining only the most direct polite form.
 *
 * <p>
 * Pattern list is a constant so enrichment is a data change.
 */
@Component
@Order(3)
public class AntiPolitenessStackingRule implements CorrectionRule {

    static final String RULE_NAME = "ANTI_POLITENESS_STACKING";

    /**
     * Each entry is a regex that matches a stacked-politeness pattern, with its single canonical replacement. Patterns
     * use text blocks for readability; all are case-insensitive.
     */
    private static final List<StackingPattern> PATTERNS = List.of(
            new StackingPattern("(?i)mwambabarira[^.!?]*ndabinginze", "ndabinginze"),
            new StackingPattern("(?i)ndabinginze[^.!?]*mwambabarira", "ndabinginze"),
            new StackingPattern("(?i)mwambabarira[^.!?]*mwirinde", "mwirinde"),
            new StackingPattern("(?i)mubabarire[^.!?]*ndabinginze", "ndabinginze"),
            new StackingPattern("(?i)ndakwinginze[^.!?]*mwambabarira", "ndabinginze"));

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

        for (var p : PATTERNS) {
            String result = p.compiled().matcher(corrected).replaceAll(p.replacement());
            if (!result.equals(corrected)) {
                corrected = result;
                changed = true;
            }
        }

        return changed ? RuleResult.changed(corrected, "Empilement de politesse réduit") : RuleResult.unchanged(text);
    }

    private static boolean isKinyarwanda(Locale locale) {
        return "rw".equals(locale.getLanguage());
    }

    /**
     * Pairs a compiled regex with its replacement string.
     */
    private record StackingPattern(Pattern compiled, String replacement) {

        StackingPattern(String regex, String replacement) {
            this(Pattern.compile(regex), replacement);
        }
    }
}
