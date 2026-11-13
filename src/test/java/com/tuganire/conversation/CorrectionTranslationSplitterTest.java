package com.tuganire.conversation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CorrectionTranslationSplitterTest {

    private static final String SEP = "§§§";

    private final List<String> corrections = new ArrayList<>();
    private final List<String> translations = new ArrayList<>();
    private final List<String> splits = new ArrayList<>();
    private CorrectionTranslationSplitter splitter;

    @BeforeEach
    void setUp() {
        splitter = new CorrectionTranslationSplitter(SEP, corrections::add, translations::add, splits::add);
    }

    private void feed(String... chunks) {
        for (String chunk : chunks) {
            splitter.accept(chunk);
        }
        splitter.finish();
    }

    @Test
    void splitsCorrectionAndTranslationInSingleChunk() {
        feed("Bonjour, où est le marché ?\n§§§\nMuraho, isoko iri he?");

        assertThat(splitter.isSplit()).isTrue();
        assertThat(splitter.correction()).isEqualTo("Bonjour, où est le marché ?");
        assertThat(splitter.translation()).isEqualTo("Muraho, isoko iri he?");
        assertThat(splits).containsExactly("Bonjour, où est le marché ?");
        assertThat(String.join("", translations)).isEqualTo("Muraho, isoko iri he?");
    }

    @Test
    void handlesSeparatorCutAcrossChunks() {
        feed("Merci", " beaucoup.\n§", "§", "§\nMura", "koze cyane");

        assertThat(splitter.correction()).isEqualTo("Merci beaucoup.");
        assertThat(splitter.translation()).isEqualTo("Murakoze cyane");
        // No separator character ever leaks into either stream.
        assertThat(String.join("", corrections)).doesNotContain("§");
        assertThat(String.join("", translations)).doesNotContain("§").isEqualTo("Murakoze cyane");
    }

    @Test
    void correctionDoneFiresBeforeFirstTranslationToken() {
        List<String> order = new ArrayList<>();
        CorrectionTranslationSplitter s = new CorrectionTranslationSplitter(SEP, c -> order.add("c"),
                t -> order.add("t"), d -> order.add("done"));
        s.accept("Salut\n§§§");
        s.accept("\nBite");
        s.finish();

        assertThat(order).containsExactly("c", "done", "t");
    }

    @Test
    void withoutSeparatorEverythingIsCorrection() {
        feed("Murakoze", " cyane");

        assertThat(splitter.isSplit()).isFalse();
        assertThat(splitter.correction()).isEqualTo("Murakoze cyane");
        assertThat(splitter.translation()).isEmpty();
        assertThat(splits).isEmpty();
        assertThat(String.join("", corrections)).isEqualTo("Murakoze cyane");
    }

    @Test
    void ignoresNullAndEmptyChunks() {
        feed(null, "", "Oui\n§§§\nYego");

        assertThat(splitter.correction()).isEqualTo("Oui");
        assertThat(splitter.translation()).isEqualTo("Yego");
    }
}
