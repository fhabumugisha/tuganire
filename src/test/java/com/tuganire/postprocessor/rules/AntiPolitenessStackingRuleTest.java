package com.tuganire.postprocessor.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.tuganire.postprocessor.RuleResult;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link AntiPolitenessStackingRule}.
 *
 * <p>
 * Verifies that stacked politeness markers are collapsed to a single canonical form when targeting Kinyarwanda, and
 * that non-Kinyarwanda targets pass through unchanged.
 */
class AntiPolitenessStackingRuleTest {

    private static final Locale FR = Locale.forLanguageTag("fr");
    private static final Locale RW = Locale.forLanguageTag("rw");

    private AntiPolitenessStackingRule rule;

    @BeforeEach
    void setUp() {
        rule = new AntiPolitenessStackingRule();
    }

    @Test
    void apply_collapsesStackedMwambabariraWithNdabinginze_whenTargetIsKinyarwanda() {
        // Given — both politeness markers in same clause
        String input = "Mwambabarira mwampe ndabinginze";

        // When
        RuleResult result = rule.apply(input, FR, RW);

        // Then — collapsed to single canonical form
        assertThat(result.changed()).isTrue();
        assertThat(result.text()).isEqualTo("ndabinginze");
    }

    @Test
    void apply_collapsesNdabinginzeBeforeMwambabarira_whenTargetIsKinyarwanda() {
        // Given — reverse order still triggers the rule
        String input = "ndabinginze mwampe mwambabarira";

        // When
        RuleResult result = rule.apply(input, FR, RW);

        // Then
        assertThat(result.changed()).isTrue();
        assertThat(result.text()).isEqualTo("ndabinginze");
    }

    @Test
    void apply_collapsesNdakwinginzeWithMwambabarira_whenTargetIsKinyarwanda() {
        // Given — ndakwinginze (singular) stacked with mwambabarira
        String input = "ndakwinginze mwampe mwambabarira";

        // When
        RuleResult result = rule.apply(input, FR, RW);

        // Then
        assertThat(result.changed()).isTrue();
        assertThat(result.text()).isEqualTo("ndabinginze");
    }

    @Test
    void apply_textUnchanged_whenTargetIsNotKinyarwanda() {
        // Given
        String input = "Mwambabarira mwampe ndabinginze";

        // When
        RuleResult result = rule.apply(input, FR, FR);

        // Then
        assertThat(result.changed()).isFalse();
        assertThat(result.text()).isEqualTo(input);
    }

    @Test
    void apply_changedFlagFalse_whenNoStackingDetected() {
        // Given — single politeness marker is fine
        String input = "Ndabinginze gufasha";

        // When
        RuleResult result = rule.apply(input, FR, RW);

        // Then
        assertThat(result.changed()).isFalse();
        assertThat(result.text()).isEqualTo(input);
    }

    @Test
    void name_returnsAntiPolitenessStacking() {
        assertThat(rule.name()).isEqualTo(AntiPolitenessStackingRule.RULE_NAME);
    }
}
