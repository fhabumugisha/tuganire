package com.tuganire.postprocessor.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.tuganire.postprocessor.RuleResult;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link AntiSyntacticCalqueRule}.
 *
 * <p>
 * Verifies that French syntactic calques are replaced by direct Bantu verb constructions when targeting Kinyarwanda,
 * and that non-Kinyarwanda targets pass through unchanged.
 */
class AntiSyntacticCalqueRuleTest {

    private static final Locale FR = Locale.forLanguageTag("fr");
    private static final Locale RW = Locale.forLanguageTag("rw");

    private AntiSyntacticCalqueRule rule;

    @BeforeEach
    void setUp() {
        rule = new AntiSyntacticCalqueRule();
    }

    @Test
    void apply_replacesMwampaKugabanyirizwa_whenTargetIsKinyarwanda() {
        // Given — "mwampa kugabanyirizwa" contains "mpa kugabanyirizwa" as a substring; the rule replaces the longest
        // matching substring first. The (?i) regex for "mpa kugabanyirizwa" will match inside "Mwampa kugabanyirizwa",
        // producing "Mwangabanyirize" (prefix Mw + replacement ngabanyirize).
        String input = "mwampa kugabanyirizwa kuri iyi nguzanyo";

        // When
        RuleResult result = rule.apply(input, FR, RW);

        // Then — calque replaced; result no longer contains the calque phrase
        assertThat(result.changed()).isTrue();
        assertThat(result.text()).doesNotContainIgnoringCase("kugabanyirizwa");
    }

    @Test
    void apply_replacesMpaKugabanyirizwaWithNgabanyirize_whenTargetIsKinyarwanda() {
        // Given — standalone first-person calque (no "mw" prefix)
        String input = "mpa kugabanyirizwa igiciro";

        // When
        RuleResult result = rule.apply(input, FR, RW);

        // Then
        assertThat(result.changed()).isTrue();
        assertThat(result.text()).contains("ngabanyirize");
    }

    @Test
    void apply_replacesMwampaKunywa_whenTargetIsKinyarwanda() {
        // Given — standalone "mpa kunywa" form (not prefixed with "mw"); exact substitution verified
        String input = "mpa kunywa amazi";

        // When
        RuleResult result = rule.apply(input, FR, RW);

        // Then — calque replaced by direct Bantu verb
        assertThat(result.changed()).isTrue();
        assertThat(result.text()).contains("nyweshe");
    }

    @Test
    void apply_textUnchanged_whenTargetIsNotKinyarwanda() {
        // Given
        String input = "mwampa kugabanyirizwa mpa kunywa";

        // When
        RuleResult result = rule.apply(input, FR, FR);

        // Then
        assertThat(result.changed()).isFalse();
        assertThat(result.text()).isEqualTo(input);
    }

    @Test
    void apply_changedFlagFalse_whenNoCalqueFound() {
        // Given — clean native Kinyarwanda verb phrase
        String input = "Mwangabanyiriza ibiciro";

        // When
        RuleResult result = rule.apply(input, FR, RW);

        // Then
        assertThat(result.changed()).isFalse();
        assertThat(result.text()).isEqualTo(input);
    }

    @Test
    void name_returnsAntiSyntacticCalque() {
        assertThat(rule.name()).isEqualTo(AntiSyntacticCalqueRule.RULE_NAME);
    }
}
