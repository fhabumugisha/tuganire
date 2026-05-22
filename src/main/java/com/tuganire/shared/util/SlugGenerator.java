package com.tuganire.shared.util;

import java.security.SecureRandom;
import java.text.Normalizer;
import java.util.Locale;

public final class SlugGenerator {

    private static final int DEFAULT_MAX_LEN = 80;
    private static final int SUFFIX_LEN = 6;
    private static final String SUFFIX_ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private SlugGenerator() {
    }

    public static String toKebabCase(String input, int maxLen) {
        if (input == null || input.isBlank()) {
            return "";
        }
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        String lower = normalized.toLowerCase(Locale.ROOT);
        String alphanum = lower.replaceAll("[^a-z0-9]+", "-");
        String trimmed = alphanum.replaceAll("^-+", "").replaceAll("-+$", "");
        if (trimmed.length() > maxLen) {
            trimmed = trimmed.substring(0, maxLen).replaceAll("-+$", "");
        }
        return trimmed;
    }

    public static String generateBlogSlug(String title) {
        String base = toKebabCase(title, DEFAULT_MAX_LEN);
        String suffix = randomSuffix();
        return base.isEmpty() ? suffix : base + "-" + suffix;
    }

    private static String randomSuffix() {
        StringBuilder sb = new StringBuilder(SUFFIX_LEN);
        for (int i = 0; i < SUFFIX_LEN; i++) {
            sb.append(SUFFIX_ALPHABET.charAt(RANDOM.nextInt(SUFFIX_ALPHABET.length())));
        }
        return sb.toString();
    }
}
