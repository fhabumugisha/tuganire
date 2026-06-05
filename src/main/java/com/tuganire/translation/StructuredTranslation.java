package com.tuganire.translation;

import java.util.List;
import org.jspecify.annotations.Nullable;

public record StructuredTranslation(String text, double confidence, List<String> appliedCorrections,
        @Nullable String alternativeTranslation) {
}
