package com.tuganire.postprocessor.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.tuganire.postprocessor.RuleResult;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link PluralRespectRule}.
 *
 * <p>
 * Verifies that singular tu/vous forms are replaced by plural-of-respect equivalents when the target is Kinyarwanda,
 * and that non-Kinyarwanda targets pass through unchanged.
 */
class PluralRespectRuleTest {

    private static final Locale FR = Locale.forLanguageTag("fr");
    private static final Locale RW = Locale.forLanguageTag("rw");

    private PluralRespectRule rule;

    @BeforeEach
    void setUp() {
        rule = new PluralRespectRule();
    }

    @Test
    void apply_replacesNdakwinginzeWithNdabinginze_whenTargetIsKinyarwanda() {
        // Given
        String input = "Ndakwinginze gufasha";

        // When
        RuleResult result = rule.apply(input, FR, RW);

        // Then
        assertThat(result.changed()).isTrue();
        assertThat(result.text()).contains("ndabinginze");
        assertThat(result.text()).doesNotContain("Ndakwinginze");
    }

    @Test
    void apply_replacesMpaWithMumpe_whenTargetIsKinyarwanda() {
        // Given
        String input = "Mpa uyu muntu";

        // When
        RuleResult result = rule.apply(input, FR, RW);

        // Then
        assertThat(result.changed()).isTrue();
        assertThat(result.text()).contains("mumpe");
    }

    @Test
    void apply_replacesMbwiraWithMwambwira_whenTargetIsKinyarwanda() {
        // Given
        String input = "mbwira icyo ukeneye";

        // When
        RuleResult result = rule.apply(input, FR, RW);

        // Then
        assertThat(result.changed()).isTrue();
        assertThat(result.text()).contains("mwambwira");
    }

    @Test
    void apply_textUnchanged_whenTargetIsNotKinyarwanda() {
        // Given
        String input = "ndakwinginze mpa mbwira";

        // When
        RuleResult result = rule.apply(input, FR, FR);

        // Then
        assertThat(result.changed()).isFalse();
        assertThat(result.text()).isEqualTo(input);
    }

    @Test
    void apply_changedFlagFalse_whenNoSubstitutionMatches() {
        // Given
        String input = "Amakuru meza cyane";

        // When
        RuleResult result = rule.apply(input, FR, RW);

        // Then
        assertThat(result.changed()).isFalse();
        assertThat(result.text()).isEqualTo(input);
    }

    @Test
    void name_returnsPluralRespect() {
        assertThat(rule.name()).isEqualTo(PluralRespectRule.RULE_NAME);
    }
}
