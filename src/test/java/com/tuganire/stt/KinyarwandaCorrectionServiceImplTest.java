package com.tuganire.stt;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the deterministic {@link KinyarwandaCorrectionServiceImpl#tidy(String)} safety net: it must always
 * capitalise {@code Imana}, capitalise the first letter, and ensure terminal punctuation — regardless of what the LLM
 * returned.
 */
class KinyarwandaCorrectionServiceImplTest {

    @Test
    @DisplayName("capitalises a standalone 'imana' to 'Imana' anywhere in the sentence")
    void capitalisesImana() {
        assertThat(KinyarwandaCorrectionServiceImpl.tidy("ubwo imana yazabaha umugisha"))
                .isEqualTo("Ubwo Imana yazabaha umugisha.");
    }

    @Test
    @DisplayName("capitalises every occurrence and any case of 'imana'")
    void capitalisesAllImanaOccurrences() {
        assertThat(KinyarwandaCorrectionServiceImpl.tidy("imana ni nziza, IMANA irakora."))
                .isEqualTo("Imana ni nziza, Imana irakora.");
    }

    @Test
    @DisplayName("does not capitalise 'imana' inside a larger word")
    void doesNotTouchImanaSubstring() {
        // "imandwa" contains "imana" as a prefix but is not the standalone word, so it stays lowercase
        // (only the sentence-initial capital applies to the first word, not to "imandwa" here).
        assertThat(KinyarwandaCorrectionServiceImpl.tidy("ndi imandwa")).isEqualTo("Ndi imandwa.");
    }

    @Test
    @DisplayName("capitalises the first letter of the sentence")
    void capitalisesFirstLetter() {
        assertThat(KinyarwandaCorrectionServiceImpl.tidy("muraho neza")).isEqualTo("Muraho neza.");
    }

    @Test
    @DisplayName("appends a period when terminal punctuation is missing")
    void appendsTerminalPunctuation() {
        assertThat(KinyarwandaCorrectionServiceImpl.tidy("nayobye")).isEqualTo("Nayobye.");
    }

    @Test
    @DisplayName("keeps an existing question mark or exclamation")
    void keepsExistingTerminalPunctuation() {
        assertThat(KinyarwandaCorrectionServiceImpl.tidy("Gare iri he ?")).isEqualTo("Gare iri he ?");
        assertThat(KinyarwandaCorrectionServiceImpl.tidy("yego!")).isEqualTo("Yego!");
    }

    @Test
    @DisplayName("trims surrounding whitespace and returns empty for blank input")
    void handlesBlankAndWhitespace() {
        assertThat(KinyarwandaCorrectionServiceImpl.tidy("  muraho  ")).isEqualTo("Muraho.");
        assertThat(KinyarwandaCorrectionServiceImpl.tidy("   ")).isEmpty();
    }
}
