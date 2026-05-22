package com.tuganire.shared.security;

import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Sanitizes user input before sending to LLM APIs to prevent prompt injection attacks.
 *
 * Security Issue: OWASP 2025 A03:2021-Injection + A10:2025-Mishandling of Exceptional Conditions
 *
 * Attack Vectors Prevented: - Prompt injection ("IGNORE ALL PREVIOUS INSTRUCTIONS") - Role manipulation ("You are now a
 * pirate") - Instruction override ("Disregard all rules") - System prompt leakage - Control character injection
 *
 * @see <a href="https://owasp.org/Top10/A03_2021-Injection/">OWASP A03:2021-Injection</a>
 * @see ../../../../../resources/documentation/security-2025.md
 */
@Slf4j
@Component
public class LLMInputSanitizer {

    /**
     * Maximum safe content length to prevent token overflow and excessive API costs. Claude Haiku 4.5 context: 200k
     * tokens Safe limit: 50k characters (~12.5k tokens assuming 4 chars/token)
     */
    private static final int MAX_CONTENT_LENGTH = 50_000;

    /**
     * Prompt injection patterns (case-insensitive). These patterns are commonly used in attacks to manipulate LLM
     * behavior. Pre-compiled to prevent ReDoS attacks.
     */
    private static final String[] PROMPT_INJECTION_PATTERN_STRINGS = {
            // Instruction override attempts
            "ignore.*previous.*instructions?", "ignore.*above", "disregard.*instructions?", "forget.*instructions?",
            "override.*instructions?",

            // Role manipulation
            "you are now", "act as", "pretend to be", "roleplay", "behave like",

            // System prompt manipulation
            "system:", "assistant:", "\\[INST\\]", // Llama instruction format
            "\\[/INST\\]", "<\\|im_start\\|>", // ChatML format
            "<\\|im_end\\|>",

            // Output format manipulation
            "output format:", "respond with:", "say exactly:", "repeat this:",

            // Document boundary manipulation
            "---END DOCUMENT---", "===END USER INPUT===", "\\[end of context\\]"};

    /**
     * Pre-compiled patterns for efficient and safe matching. Using Pattern.find() instead of String.matches() to
     * prevent ReDoS.
     */
    private static final Pattern[] COMPILED_PATTERNS;

    static {
        COMPILED_PATTERNS = new Pattern[PROMPT_INJECTION_PATTERN_STRINGS.length];
        for (int i = 0; i < PROMPT_INJECTION_PATTERN_STRINGS.length; i++) {
            COMPILED_PATTERNS[i] = Pattern.compile(PROMPT_INJECTION_PATTERN_STRINGS[i], Pattern.CASE_INSENSITIVE);
        }
    }

    /**
     * Sanitizes user content for use in LLM prompts. Applies multiple layers of security: 1. Length truncation 2.
     * Prompt injection pattern detection 3. Control character removal 4. Null byte removal
     *
     * @param content
     *            User-provided content
     * @return Sanitized content safe for LLM prompts
     */
    public String sanitizeForPrompt(String content) {
        if (content == null || content.isEmpty()) {
            return "";
        }

        // 1. Truncate to max length
        String sanitized = content.length() > MAX_CONTENT_LENGTH
                ? content.substring(0, MAX_CONTENT_LENGTH) + "\n[Content truncated for safety]"
                : content;

        // 2. Log and flag suspicious patterns
        detectPromptInjection(sanitized);

        // 3. Remove null bytes and dangerous control characters
        // Keep newlines (\n=0x0A) and tabs (\t=0x09) for formatting
        sanitized = sanitized.replaceAll("\\x00", "") // Null bytes
                .replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", ""); // Other control chars

        return sanitized;
    }

    /**
     * Wraps user content with clear delimiters to prevent prompt injection. This helps the LLM distinguish between
     * instructions and user-provided content.
     *
     * Usage:
     *
     * <pre>
     *
     * String prompt = String.format("""
     *         You are a document analyzer. Extract metadata from the document below.
     *
     *         %s
     *
     *         Return JSON only.
     *         """, sanitizer.wrapUserContent(userDocument));
     * </pre>
     *
     * @param content
     *            User-provided content
     * @return Content wrapped with security delimiters
     */
    public String wrapUserContent(String content) {
        String sanitized = sanitizeForPrompt(content);
        return """
                ===BEGIN USER DOCUMENT===
                %s
                ===END USER DOCUMENT===
                """.formatted(sanitized);
    }

    /**
     * Detects potential prompt injection patterns in content. Logs warnings for security monitoring but does not block
     * content (false positives possible in legitimate sermon content).
     *
     * Uses pre-compiled patterns with Pattern.find() to prevent ReDoS attacks.
     *
     * @param content
     *            Content to analyze
     */
    private void detectPromptInjection(String content) {
        for (int i = 0; i < COMPILED_PATTERNS.length; i++) {
            if (COMPILED_PATTERNS[i].matcher(content).find()) {
                log.warn("⚠️ Potential prompt injection detected: pattern={}, contentPreview='{}'",
                        PROMPT_INJECTION_PATTERN_STRINGS[i],
                        content.substring(0, Math.min(100, content.length())) + "...");

                // For production: Consider implementing one of these strategies:
                // 1. Block the request entirely (strict)
                // 2. Further sanitize by removing the pattern (moderate)
                // 3. Log and allow (monitoring only - current approach)
                // 4. Rate limit the user if multiple injection attempts detected
            }
        }
    }

    /**
     * Sanitizes search queries which may be used in both LLM calls and database queries. Less strict than document
     * sanitization since queries are shorter and more constrained.
     *
     * @param query
     *            User search query
     * @return Sanitized query
     */
    public String sanitizeSearchQuery(String query) {
        if (query == null || query.isEmpty()) {
            return "";
        }

        // Limit query length
        String sanitized = query.length() > 500 ? query.substring(0, 500) : query;

        // Remove control characters but keep spaces
        sanitized = sanitized.replaceAll("\\x00", "").replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", "");

        // Log suspicious patterns (queries shouldn't contain complex instructions)
        // Uses pre-compiled pattern to prevent ReDoS
        if (COMPILED_PATTERNS[0].matcher(sanitized).find()) { // "ignore.*previous.*instructions?"
            log.warn("⚠️ Suspicious search query detected: '{}'", sanitized);
        }

        return sanitized.trim();
    }

    /**
     * Validates LLM output to ensure it matches expected format. Use this after receiving LLM responses to detect
     * injection that bypassed input sanitization.
     *
     * @param output
     *            LLM response
     * @param maxLength
     *            Maximum expected length
     * @return true if output is safe, false if suspicious
     */
    public boolean validateLLMOutput(String output, int maxLength) {
        if (output == null) {
            return true;
        }

        // Check length
        if (output.length() > maxLength) {
            log.warn("⚠️ LLM output exceeds expected length: {} > {}", output.length(), maxLength);
            return false;
        }

        // Check for injection artifacts in output
        String[] suspiciousOutputPatterns = {"IGNORE ALL PREVIOUS", "I am now acting as", "System prompt:", "<script>",
                "javascript:", "data:text/html"};

        String upperOutput = output.toUpperCase();
        for (String pattern : suspiciousOutputPatterns) {
            if (upperOutput.contains(pattern.toUpperCase())) {
                log.error("⚠️ Suspicious content detected in LLM output: pattern={}", pattern);
                return false;
            }
        }

        return true;
    }
}
