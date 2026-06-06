package com.tuganire.stt;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the deterministic {@link KinyarwandaCorrectionServiceImpl#tidy(String)} safety net: it must always
 * capitalise {@code Imana}, capitalise the first letter, and ensure terminal punctuation.
 */
class KinyarwandaCorrectionServiceImplTest {

    private final KinyarwandaCorrectionServiceImpl service = new KinyarwandaCorrectionServiceImpl();

    @Test
    @DisplayName("capitalises a standalone 'imana' to 'Imana' anywhere in the sentence")
    void capitalisesImana() {
        assertThat(service.tidy("ubwo imana yazabaha umugisha")).isEqualTo("Ubwo Imana yazabaha umugisha.");
    }

    @Test
    @DisplayName("capitalises every occurrence and any case of 'imana'")
    void capitalisesAllImanaOccurrences() {
        assertThat(service.tidy("imana ni nziza, IMANA irakora.")).isEqualTo("Imana ni nziza, Imana irakora.");
    }

    @Test
    @DisplayName("does not capitalise 'imana' inside a larger word")
    void doesNotTouchImanaSubstring() {
        // "imandwa" contains "imana" as a prefix but is not the standalone word, so it stays lowercase
        // (only the sentence-initial capital applies to the first word, not to "imandwa" here).
        assertThat(service.tidy("ndi imandwa")).isEqualTo("Ndi imandwa.");
    }

    @Test
    @DisplayName("capitalises the first letter of the sentence")
    void capitalisesFirstLetter() {
        assertThat(service.tidy("muraho neza")).isEqualTo("Muraho neza.");
    }

    @Test
    @DisplayName("appends a period when terminal punctuation is missing")
    void appendsTerminalPunctuation() {
        assertThat(service.tidy("nayobye")).isEqualTo("Nayobye.");
    }

    @Test
    @DisplayName("keeps an existing question mark or exclamation")
    void keepsExistingTerminalPunctuation() {
        assertThat(service.tidy("Gare iri he ?")).isEqualTo("Gare iri he ?");
        assertThat(service.tidy("yego!")).isEqualTo("Yego!");
    }

    @Test
    @DisplayName("trims surrounding whitespace and returns empty for blank input")
    void handlesBlankAndWhitespace() {
        assertThat(service.tidy("  muraho  ")).isEqualTo("Muraho.");
        assertThat(service.tidy("   ")).isEmpty();
    }
}
