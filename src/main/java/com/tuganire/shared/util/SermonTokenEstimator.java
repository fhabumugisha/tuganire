package com.tuganire.shared.util;

/**
 * Utility class for estimating token count in text content. Used for AI context window management.
 */
public final class SermonTokenEstimator {

    private static final int CHARS_PER_TOKEN = 4;

    public static final int MAX_SAFE_INPUT_TOKENS = 100_000;

    private SermonTokenEstimator() {
    }

    public static int estimateTokenCount(String content) {
        if (content == null || content.isEmpty()) {
            return 0;
        }
        return content.length() / CHARS_PER_TOKEN;
    }

    public static boolean isContentTooLarge(String content, String additionalPrompt) {
        int contentTokens = estimateTokenCount(content);
        int promptTokens = estimateTokenCount(additionalPrompt);
        return (contentTokens + promptTokens) > MAX_SAFE_INPUT_TOKENS;
    }
}
