package com.tuganire.postprocessor.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.tuganire.postprocessor.RuleResult;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link InfixPronounRule}.
 *
 * <p>
 * Verifies that verb forms missing pronominal infixes are corrected when targeting Kinyarwanda, and that
 * non-Kinyarwanda targets pass through unchanged.
 */
class InfixPronounRuleTest {

    private static final Locale FR = Locale.forLanguageTag("fr");
    private static final Locale RW = Locale.forLanguageTag("rw");

    private InfixPronounRule rule;

    @BeforeEach
    void setUp() {
        rule = new InfixPronounRule();
    }

    @Test
    void apply_replacesKuryaWithKundya_whenTargetIsKinyarwanda() {
        // Given
        String input = "Amfasha kurya ibiryo";

        // When
        RuleResult result = rule.apply(input, FR, RW);

        // Then
        assertThat(result.changed()).isTrue();
        assertThat(result.text()).contains("kundya");
        assertThat(result.text()).doesNotContain("kurya");
    }

    @Test
    void apply_replacesKubwiraWithKumbwira_whenTargetIsKinyarwanda() {
        // Given
        String input = "kubwira umuturage";

        // When
        RuleResult result = rule.apply(input, FR, RW);

        // Then
        assertThat(result.changed()).isTrue();
        assertThat(result.text()).contains("kumbwira");
    }

    @Test
    void apply_replacesKubona_whenTargetIsKinyarwanda() {
        // Given
        String input = "kubona inzira";

        // When
        RuleResult result = rule.apply(input, FR, RW);

        // Then
        assertThat(result.changed()).isTrue();
        assertThat(result.text()).contains("kundabona");
    }

    @Test
    void apply_textUnchanged_whenTargetIsNotKinyarwanda() {
        // Given
        String input = "kurya kubwira kubona";

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
    void name_returnsInfixPronoun() {
        assertThat(rule.name()).isEqualTo(InfixPronounRule.RULE_NAME);
    }
}
