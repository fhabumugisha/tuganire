package com.tuganire.golden;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * Form payload for creating or editing a {@link GoldenEntry} from the admin dashboard.
 *
 * @param sourceText
 *            the source phrase; required
 * @param sourceLang
 *            BCP-47 source language code (e.g. {@code "fr"}); required, length 2
 * @param targetText
 *            the validated translation; required
 * @param targetLang
 *            BCP-47 target language code (e.g. {@code "rw"}); required, length 2
 * @param context
 *            optional usage context (e.g. {@code "santé"}); may be blank
 * @param validatedBy
 *            who validated this entry; required
 */
public record GoldenEntryRequest(@NotBlank String sourceText, @NotBlank @Size(min = 2, max = 2) String sourceLang,
        @NotBlank String targetText, @NotBlank @Size(min = 2, max = 2) String targetLang,
        @Nullable @Size(max = 100) String context, @NotBlank @Size(max = 100) String validatedBy) {
}
