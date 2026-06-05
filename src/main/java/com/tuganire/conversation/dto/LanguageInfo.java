package com.tuganire.conversation.dto;

/**
 * A single supported language descriptor.
 *
 * @param code
 *            BCP-47 language code (e.g. {@code "fr"})
 * @param nativeName
 *            language name in its own script (e.g. {@code "Français"})
 * @param englishName
 *            English label for the language
 */
public record LanguageInfo(String code, String nativeName, String englishName) {
}
