package com.tuganire.postprocessor;

import static org.assertj.core.api.Assertions.assertThat;

import com.tuganire.postprocessor.rules.AntiHallucinationRule;
import com.tuganire.postprocessor.rules.AntiPolitenessStackingRule;
import com.tuganire.postprocessor.rules.AntiSyntacticCalqueRule;
import com.tuganire.postprocessor.rules.InfixPronounRule;
import com.tuganire.postprocessor.rules.PluralRespectRule;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Verifies the register-aware behaviour of {@link KinyarwandaPostProcessor}: the plural-of-respect rule is applied for
 * "vous"/unknown sources but skipped when the French source tutoie ("tu"), so the singular register is preserved.
 */
class KinyarwandaPostProcessorTest {

    private static final Locale FR = Locale.forLanguageTag("fr");
    private static final Locale RW = Locale.forLanguageTag("rw");

    private final KinyarwandaPostProcessor processor = new KinyarwandaPostProcessor(new PluralRespectRule(),
            new InfixPronounRule(), new AntiPolitenessStackingRule(), new AntiHallucinationRule(),
            new AntiSyntacticCalqueRule());

    @Test
    @DisplayName("vous source → plural of respect applied (wamfasha → mwamfasha)")
    void appliesPluralRespectForVous() {
        var result = processor.process("wamfasha", "Pouvez-vous m'aider ?", FR, RW);
        assertThat(result.text()).contains("mwamfasha");
        assertThat(result.appliedCorrections()).contains("PLURAL_RESPECT");
    }

    @Test
    @DisplayName("tu source → plural rule skipped, singular preserved (wamfasha stays)")
    void preservesSingularForTu() {
        var result = processor.process("wamfasha", "Tu peux m'aider ?", FR, RW);
        assertThat(result.text()).contains("wamfasha");
        assertThat(result.text()).doesNotContain("mwamfasha");
        assertThat(result.appliedCorrections()).doesNotContain("PLURAL_RESPECT");
    }

    @Test
    @DisplayName("null source → register detection off, plural rule applies")
    void appliesPluralRespectWhenSourceNull() {
        var result = processor.process("wamfasha", null, FR, RW);
        assertThat(result.text()).contains("mwamfasha");
    }
}
