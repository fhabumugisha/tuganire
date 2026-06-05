package com.tuganire.translation;

import java.util.Locale;
import java.util.Objects;
import org.jspecify.annotations.NonNull;

public record TranslationRequest(@NonNull String sourceText, @NonNull Locale sourceLanguage,
        @NonNull Locale targetLanguage, String sessionId) {

    public TranslationRequest {
        Objects.requireNonNull(sourceText, "Source text required");
        Objects.requireNonNull(sourceLanguage, "Source language required");
        Objects.requireNonNull(targetLanguage, "Target language required");
    }
}
