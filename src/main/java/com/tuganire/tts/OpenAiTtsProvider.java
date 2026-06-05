package com.tuganire.tts;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.audio.tts.TextToSpeechModel;
import org.springframework.ai.audio.tts.TextToSpeechPrompt;
import org.springframework.ai.openai.OpenAiAudioSpeechOptions;
import org.springframework.ai.openai.OpenAiAudioSpeechOptions.AudioResponseFormat;
import org.springframework.ai.openai.OpenAiAudioSpeechOptions.Voice;
import org.springframework.stereotype.Component;

/**
 * {@link TtsProvider} backed by the OpenAI TTS API via Spring AI.
 *
 * <p>
 * Uses {@code gpt-4o-mini-tts} with the {@code alloy} voice and MP3 response format. This is the designated provider
 * for French audio (ADR-006). Spring AI imports are intentionally confined to this class (ADR-008).
 */
@Component
@RequiredArgsConstructor
@Slf4j
class OpenAiTtsProvider implements TtsProvider {

    static final String PROVIDER_NAME = "openai";

    private static final String TTS_MODEL = "gpt-4o-mini-tts";

    private final TextToSpeechModel speechModel;

    @Override
    public String name() {
        return PROVIDER_NAME;
    }

    @Override
    public boolean supportsLanguage(String languageCode) {
        return true;
    }

    @Override
    public byte[] synthesize(String text, String languageCode) {
        log.debug("OpenAI TTS synthesize: lang={}, text length={}", languageCode, text.length());
        OpenAiAudioSpeechOptions options = OpenAiAudioSpeechOptions.builder().model(TTS_MODEL).voice(Voice.ALLOY)
                .responseFormat(AudioResponseFormat.MP3).build();
        TextToSpeechPrompt prompt = new TextToSpeechPrompt(text, options);
        return speechModel.call(prompt).getResult().getOutput();
    }
}
