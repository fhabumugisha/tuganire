package com.tuganire.stt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tuganire.config.SttConfig;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.audio.transcription.AudioTranscription;
import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.AudioTranscriptionResponse;
import org.springframework.ai.openai.OpenAiAudioTranscriptionModel;
import org.springframework.ai.openai.OpenAiAudioTranscriptionOptions;

class OpenAiSttProviderTest {

    private static final Locale RW = Locale.of("rw");
    private static final byte[] WEBM = {0x1A, 0x45, (byte) 0xDF, (byte) 0xA3, 0, 0, 0, 0};

    private OpenAiAudioTranscriptionModel model;
    private MmsSttProvider mms;
    private OpenAiSttProvider provider;

    @BeforeEach
    void setUp() {
        model = mock(OpenAiAudioTranscriptionModel.class);
        mms = mock(MmsSttProvider.class);
        provider = new OpenAiSttProvider(model, new SttConfig("whisper", "openai", "gpt-transcribe"), mms);
    }

    private static AudioTranscriptionResponse response(String text) {
        return new AudioTranscriptionResponse(new AudioTranscription(text));
    }

    @Test
    void transcribesWithKinyarwandaPromptModelAndSniffedFileName() {
        when(model.call(any(AudioTranscriptionPrompt.class))).thenReturn(response(" Muraho, amakuru yawe? "));

        assertThat(provider.transcribe(WEBM, RW)).isEqualTo("Muraho, amakuru yawe?");

        ArgumentCaptor<AudioTranscriptionPrompt> captor = ArgumentCaptor.forClass(AudioTranscriptionPrompt.class);
        verify(model).call(captor.capture());
        OpenAiAudioTranscriptionOptions options = (OpenAiAudioTranscriptionOptions) captor.getValue().getOptions();
        assertThat(options.getModel()).isEqualTo("gpt-transcribe");
        assertThat(options.getPrompt()).isEqualTo(OpenAiSttProvider.KINYARWANDA_PROMPT);
        // The API rejects "rw": no language code may be sent.
        assertThat(options.getLanguage()).isNull();
        assertThat(captor.getValue().getInstructions().getFilename()).isEqualTo("audio.webm");
        verify(mms, never()).transcribe(any(), any());
    }

    @Test
    void fallsBackToMmsWhenOpenAiFails() {
        when(model.call(any(AudioTranscriptionPrompt.class))).thenThrow(new IllegalStateException("429"));
        when(mms.transcribe(WEBM, RW)).thenReturn("muraho");

        assertThat(provider.transcribe(WEBM, RW)).isEqualTo("muraho");
    }

    @Test
    void fallsBackToMmsOnEmptyTranscript() {
        when(model.call(any(AudioTranscriptionPrompt.class))).thenReturn(response("  "));
        when(mms.transcribe(WEBM, RW)).thenReturn("murakoze");

        assertThat(provider.transcribe(WEBM, RW)).isEqualTo("murakoze");
    }

    @Test
    void supportsOnlyKinyarwanda() {
        assertThat(provider.supportsLanguage("rw")).isTrue();
        assertThat(provider.supportsLanguage("fr")).isFalse();
    }
}
