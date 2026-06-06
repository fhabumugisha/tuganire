package com.tuganire.stt;

import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Deterministic implementation of {@link KinyarwandaCorrectionService}: no LLM call, instant.
 *
 * <p>
 * Guarantees the orthographic rules MMS-ASR cannot produce: {@code Imana} (God) is always capitalised, the sentence
 * starts with a capital, and it ends with terminal punctuation. The richer LLM correction lives once in the streaming
 * translation pipeline ({@code StreamTranslationServiceImpl}); duplicating it here would add a redundant, blocking
 * round-trip to the transcription latency.
 */
@Service
@Slf4j
class KinyarwandaCorrectionServiceImpl implements KinyarwandaCorrectionService {

    /** Word "imana" as a standalone token, any case — enforced to "Imana" deterministically. */
    private static final Pattern IMANA = Pattern.compile("\\bimana\\b", Pattern.CASE_INSENSITIVE);

    @Override
    public String tidy(String raw) {
        String t = raw == null ? "" : raw.trim();
        if (t.isEmpty()) {
            return t;
        }
        t = IMANA.matcher(t).replaceAll("Imana");
        t = Character.toUpperCase(t.charAt(0)) + t.substring(1);
        char last = t.charAt(t.length() - 1);
        if (last != '.' && last != '!' && last != '?') {
            t = t + ".";
        }
        return t;
    }
}
