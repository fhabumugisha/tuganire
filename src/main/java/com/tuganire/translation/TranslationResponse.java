package com.tuganire.translation;

import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public record TranslationResponse(@NonNull String originalText, @NonNull String translatedText,
        @Nullable String detectedLanguage, double confidence, boolean fromCache, boolean fromGoldenDictionary,
        List<String> appliedCorrections, @NonNull Instant translatedAt) {

    public static TranslationResponse fromCache(@NonNull String originalText, @NonNull String translatedText,
            @Nullable String detectedLanguage, double confidence, List<String> appliedCorrections) {
        return new TranslationResponse(originalText, translatedText, detectedLanguage, confidence, true, false,
                appliedCorrections, Instant.now());
    }

    public static TranslationResponse fromGoldenDictionary(@NonNull String originalText, @NonNull String translatedText,
            List<String> appliedCorrections) {
        return new TranslationResponse(originalText, translatedText, null, 1.0, false, true, appliedCorrections,
                Instant.now());
    }

    public static TranslationResponse fromLlm(@NonNull String originalText, @NonNull String translatedText,
            List<String> appliedCorrections) {
        return new TranslationResponse(originalText, translatedText, null, 0.0, false, false, appliedCorrections,
                Instant.now());
    }
}
