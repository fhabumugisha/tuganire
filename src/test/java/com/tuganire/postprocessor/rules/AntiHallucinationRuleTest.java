package com.tuganire.postprocessor.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.tuganire.postprocessor.RuleResult;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link AntiHallucinationRule}.
 *
 * <p>
 * Verifies that known LLM-invented (hallucinated) words are replaced by correct native equivalents when targeting
 * Kinyarwanda, and that non-Kinyarwanda targets pass through unchanged.
 */
class AntiHallucinationRuleTest {

    private static final Locale FR = Locale.forLanguageTag("fr");
    private static final Locale RW = Locale.forLanguageTag("rw");

    private AntiHallucinationRule rule;

    @BeforeEach
    void setUp() {
        rule = new AntiHallucinationRule();
    }

    @Test
    void apply_replacesArakoolWithNiCool_whenTargetIsKinyarwanda() {
        // Given — "arakool" is a hallucinated blend of Kinyarwanda morphology and English
        String input = "Iki kintu arakool";

        // When
        RuleResult result = rule.apply(input, FR, RW);

        // Then
        assertThat(result.changed()).isTrue();
        assertThat(result.text()).contains("ni cool");
        assertThat(result.text()).doesNotContainIgnoringCase("arakool");
    }

    @Test
    void apply_replacesArasomeWithAsomye_whenTargetIsKinyarwanda() {
        // Given — "arasome" uses wrong tense morpheme
        String input = "Yego arasome igitabo";

        // When
        RuleResult result = rule.apply(input, FR, RW);

        // Then
        assertThat(result.changed()).isTrue();
        assertThat(result.text()).contains("asomye");
    }

    @Test
    void apply_replacesInkuruNzuriWithAmakuruMeza_whenTargetIsKinyarwanda() {
        // Given — "inkuru nzuri" is a typical LLM calque
        String input = "Ngewe ndakunda inkuru nzuri";

        // When
        RuleResult result = rule.apply(input, FR, RW);

        // Then
        assertThat(result.changed()).isTrue();
        assertThat(result.text()).contains("amakuru meza");
    }

    @Test
    void apply_textUnchanged_whenTargetIsNotKinyarwanda() {
        // Given
        String input = "arakool arasome inkuru nzuri";

        // When
        RuleResult result = rule.apply(input, FR, FR);

        // Then
        assertThat(result.changed()).isFalse();
        assertThat(result.text()).isEqualTo(input);
    }

    @Test
    void apply_changedFlagFalse_whenNoHallucinatedWordFound() {
        // Given — clean native Kinyarwanda
        String input = "Ndagukunda cyane mwene wanjye";

        // When
        RuleResult result = rule.apply(input, FR, RW);

        // Then
        assertThat(result.changed()).isFalse();
        assertThat(result.text()).isEqualTo(input);
    }

    @Test
    void name_returnsAntiHallucination() {
        assertThat(rule.name()).isEqualTo(AntiHallucinationRule.RULE_NAME);
    }
}
