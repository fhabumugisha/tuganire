package com.tuganire.stt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.tuganire.config.SttConfig;
import java.util.List;
import org.junit.jupiter.api.Test;

class SttProviderFactoryTest {

    private static SttProvider provider(String name, String... langs) {
        SttProvider p = mock(SttProvider.class);
        when(p.name()).thenReturn(name);
        for (String lang : langs) {
            when(p.supportsLanguage(lang)).thenReturn(true);
        }
        return p;
    }

    private final SttProvider whisper = provider("whisper", "fr", "en");
    private final SttProvider mms = provider("mms", "rw");
    private final SttProvider openai = provider("openai", "rw");

    @Test
    void routesKinyarwandaToConfiguredProvider() {
        // mms is registered first: "first match" would pick it, the config must win.
        SttProviderFactory factory = new SttProviderFactory(List.of(whisper, mms, openai),
                new SttConfig("whisper", "openai", "gpt-transcribe"));
        assertThat(factory.forLanguage("rw")).isSameAs(openai);

        SttProviderFactory mmsFactory = new SttProviderFactory(List.of(whisper, openai, mms),
                new SttConfig("whisper", "mms", "gpt-transcribe"));
        assertThat(mmsFactory.forLanguage("rw")).isSameAs(mms);
    }

    @Test
    void frenchStillUsesWhisper() {
        SttProviderFactory factory = new SttProviderFactory(List.of(openai, mms, whisper),
                new SttConfig("whisper", "openai", "gpt-transcribe"));
        assertThat(factory.forLanguage("fr")).isSameAs(whisper);
    }
}
