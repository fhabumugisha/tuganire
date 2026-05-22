package com.tuganire.shared.util;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Parser for biblical references in French natural language queries. Extracts and normalizes biblical references
 * without using AI.
 *
 * <p>
 * Examples:
 * <ul>
 * <li>"sermon sur Matthieu 8:21" → { book: "Matthieu", chapter: 8, verse: 21 }</li>
 * <li>"Jean 3:16-18" → { book: "Jean", chapter: 3, verseStart: 16, verseEnd: 18 }</li>
 * <li>"Romains 8" → { book: "Romains", chapter: 8 }</li>
 * <li>"actes 4.3 et galates 5:2-8" → 2 references</li>
 * </ul>
 */
@Component
@Slf4j
public class BiblicalReferenceParser {

    // Comprehensive French biblical reference pattern
    // Matches: "Matthieu 8:21", "Jean 3:16-18", "Jn 3:16", "1 Corinthiens 13", etc.
    private static final Pattern BIBLE_REF_PATTERN = Pattern.compile(
            // Optional number prefix for numbered books (1, 2, 3)
            "(?<prefix>[1-3]\\s*)?" +
            // Book name (French + common abbreviations)
                    "(?<book>" +
                    // New Testament (most common in sermons)
                    "matthieu|mathieu|mt|mat|" + "marc|mc|" + "luc|lc|" + "jean|jn|" + "actes|ac|"
                    + "romains?|rm|rom|ro|" + "corinthiens?|co|cor|" + "galates?|ga|gal|" + "ephesiens?|ep|eph|"
                    + "philippiens?|ph|phil|" + "colossiens?|col|" + "thessaloniciens?|th|thess|" + "timothee|tm|tim|"
                    + "tite|tt|tit|" + "philemon|phm|" + "hebreux|he|heb|" + "jacques|jc|jac|" + "pierre|pi|"
                    + "jude|jd|" + "apocalypse|ap|apo|rev|" +
                    // Old Testament
                    "genese|gen|gn|" + "exode|ex|" + "levitique|lev|lv|" + "nombres|nb|nom|" + "deuteronome|dt|deut|"
                    + "josue|jos|" + "juges|jg|jug|" + "ruth|rt|ru|" + "samuel|sm|" + "rois|" + "chroniques|ch|chr|"
                    + "esdras|esd|" + "nehemie|neh|ne|" + "esther|est|" + "job|jb|" + "psaumes?|ps|psa|"
                    + "proverbes?|pr|prov|" + "ecclesiaste|ec|ecc|" + "cantique|ct|cant|" + "esaie|es|esa|"
                    + "jeremie|jer|je|" + "lamentations?|lam|" + "ezechiel|ez|eze|" + "daniel|dn|da|" + "osee|os|"
                    + "joel|jl|joe|" + "amos|am|" + "abdias|abd|" + "jonas|jon|" + "michee|mi|mic|" + "nahum|na|nah|"
                    + "habakuk|hab|" + "sophonie|so|sop|" + "aggee|ag|agg|" + "zacharie|za|zac|" + "malachie|mal" + ")"
                    +
                    // Whitespace between book and chapter
                    "\\s+" +
                    // Chapter number (required)
                    "(?<chapter>\\d+)" +
                    // Optional verse(s)
                    "(?:" + "\\s*[:.\\-,]\\s*" + // Separator: colon, dot, hyphen, comma, or space
                    "(?<verseStart>\\d+)" + // Verse start
                    "(?:" + "\\s*[-–—]\\s*" + // Range separator
                    "(?<verseEnd>\\d+)" + // Verse end (for ranges like 3:16-18)
                    ")?" + ")?" +
                    // Optional version tag (LSG, NBS, S21, etc.)
                    "(?:\\s+(?<version>lsg|nbs|s21|nfc|seg|neg|tob))?" +
                    // Word boundary to avoid false matches
                    "\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS);

    // Canonical French book names mapping
    private static final Map<String, String> CANONICAL_BOOKS = Map.ofEntries(
            // New Testament
            Map.entry("matthieu", "Matthieu"), Map.entry("mathieu", "Matthieu"), // Common typo
            Map.entry("mt", "Matthieu"), Map.entry("mat", "Matthieu"),

            Map.entry("marc", "Marc"), Map.entry("mc", "Marc"),

            Map.entry("luc", "Luc"), Map.entry("lc", "Luc"),

            Map.entry("jean", "Jean"), Map.entry("jn", "Jean"),

            Map.entry("actes", "Actes"), Map.entry("ac", "Actes"),

            Map.entry("romains", "Romains"), Map.entry("romain", "Romains"), Map.entry("rm", "Romains"),
            Map.entry("rom", "Romains"), Map.entry("ro", "Romains"),

            Map.entry("corinthiens", "Corinthiens"), Map.entry("corinthien", "Corinthiens"),
            Map.entry("co", "Corinthiens"), Map.entry("cor", "Corinthiens"),

            Map.entry("galates", "Galates"), Map.entry("galate", "Galates"), Map.entry("ga", "Galates"),
            Map.entry("gal", "Galates"),

            Map.entry("ephesiens", "Éphésiens"), Map.entry("ephesien", "Éphésiens"), Map.entry("ep", "Éphésiens"),
            Map.entry("eph", "Éphésiens"),

            Map.entry("philippiens", "Philippiens"), Map.entry("philippien", "Philippiens"),
            Map.entry("ph", "Philippiens"), Map.entry("phil", "Philippiens"),

            Map.entry("colossiens", "Colossiens"), Map.entry("colossien", "Colossiens"), Map.entry("col", "Colossiens"),

            Map.entry("thessaloniciens", "Thessaloniciens"), Map.entry("thessalonicien", "Thessaloniciens"),
            Map.entry("th", "Thessaloniciens"), Map.entry("thess", "Thessaloniciens"),

            Map.entry("timothee", "Timothée"), Map.entry("tm", "Timothée"), Map.entry("tim", "Timothée"),

            Map.entry("tite", "Tite"), Map.entry("tt", "Tite"), Map.entry("tit", "Tite"),

            Map.entry("philemon", "Philémon"), Map.entry("phm", "Philémon"),

            Map.entry("hebreux", "Hébreux"), Map.entry("he", "Hébreux"), Map.entry("heb", "Hébreux"),

            Map.entry("jacques", "Jacques"), Map.entry("jc", "Jacques"), Map.entry("jac", "Jacques"),

            Map.entry("pierre", "Pierre"), Map.entry("pi", "Pierre"),

            Map.entry("jude", "Jude"), Map.entry("jd", "Jude"),

            Map.entry("apocalypse", "Apocalypse"), Map.entry("ap", "Apocalypse"), Map.entry("apo", "Apocalypse"),
            Map.entry("rev", "Apocalypse"),

            // Old Testament
            Map.entry("genese", "Genèse"), Map.entry("gen", "Genèse"), Map.entry("gn", "Genèse"),

            Map.entry("exode", "Exode"), Map.entry("ex", "Exode"),

            Map.entry("levitique", "Lévitique"), Map.entry("lev", "Lévitique"), Map.entry("lv", "Lévitique"),

            Map.entry("nombres", "Nombres"), Map.entry("nb", "Nombres"), Map.entry("nom", "Nombres"),

            Map.entry("deuteronome", "Deutéronome"), Map.entry("dt", "Deutéronome"), Map.entry("deut", "Deutéronome"),

            Map.entry("josue", "Josué"), Map.entry("jos", "Josué"),

            Map.entry("juges", "Juges"), Map.entry("jg", "Juges"), Map.entry("jug", "Juges"),

            Map.entry("ruth", "Ruth"), Map.entry("rt", "Ruth"), Map.entry("ru", "Ruth"),

            Map.entry("samuel", "Samuel"), Map.entry("sm", "Samuel"),

            Map.entry("rois", "Rois"),

            Map.entry("chroniques", "Chroniques"), Map.entry("ch", "Chroniques"), Map.entry("chr", "Chroniques"),

            Map.entry("esdras", "Esdras"), Map.entry("esd", "Esdras"),

            Map.entry("nehemie", "Néhémie"), Map.entry("neh", "Néhémie"), Map.entry("ne", "Néhémie"),

            Map.entry("esther", "Esther"), Map.entry("est", "Esther"),

            Map.entry("job", "Job"), Map.entry("jb", "Job"),

            Map.entry("psaumes", "Psaumes"), Map.entry("psaume", "Psaumes"), Map.entry("ps", "Psaumes"),
            Map.entry("psa", "Psaumes"),

            Map.entry("proverbes", "Proverbes"), Map.entry("proverbe", "Proverbes"), Map.entry("pr", "Proverbes"),
            Map.entry("prov", "Proverbes"),

            Map.entry("ecclesiaste", "Ecclésiaste"), Map.entry("ec", "Ecclésiaste"), Map.entry("ecc", "Ecclésiaste"),

            Map.entry("cantique", "Cantique"), Map.entry("ct", "Cantique"), Map.entry("cant", "Cantique"),

            Map.entry("esaie", "Ésaïe"), Map.entry("es", "Ésaïe"), Map.entry("esa", "Ésaïe"),

            Map.entry("jeremie", "Jérémie"), Map.entry("jer", "Jérémie"), Map.entry("je", "Jérémie"),

            Map.entry("lamentations", "Lamentations"), Map.entry("lamentation", "Lamentations"),
            Map.entry("lam", "Lamentations"),

            Map.entry("ezechiel", "Ézéchiel"), Map.entry("ez", "Ézéchiel"), Map.entry("eze", "Ézéchiel"),

            Map.entry("daniel", "Daniel"), Map.entry("dn", "Daniel"), Map.entry("da", "Daniel"),

            Map.entry("osee", "Osée"), Map.entry("os", "Osée"),

            Map.entry("joel", "Joël"), Map.entry("jl", "Joël"), Map.entry("joe", "Joël"),

            Map.entry("amos", "Amos"), Map.entry("am", "Amos"),

            Map.entry("abdias", "Abdias"), Map.entry("abd", "Abdias"),

            Map.entry("jonas", "Jonas"), Map.entry("jon", "Jonas"),

            Map.entry("michee", "Michée"), Map.entry("mi", "Michée"), Map.entry("mic", "Michée"),

            Map.entry("nahum", "Nahum"), Map.entry("na", "Nahum"), Map.entry("nah", "Nahum"),

            Map.entry("habakuk", "Habakuk"), Map.entry("hab", "Habakuk"),

            Map.entry("sophonie", "Sophonie"), Map.entry("so", "Sophonie"), Map.entry("sop", "Sophonie"),

            Map.entry("aggee", "Aggée"), Map.entry("ag", "Aggée"), Map.entry("agg", "Aggée"),

            Map.entry("zacharie", "Zacharie"), Map.entry("za", "Zacharie"), Map.entry("zac", "Zacharie"),

            Map.entry("malachie", "Malachie"), Map.entry("mal", "Malachie"));

    /**
     * Parses a natural language query and extracts all biblical references. Supports multiple references in a single
     * query.
     *
     * @param query
     *            Natural language query (e.g., "sermon sur Matthieu 8:21")
     * @return ParseResult containing extracted references and text without references
     */
    public ParseResult parse(String query) {
        if (query == null || query.isBlank()) {
            return new ParseResult(query, List.of());
        }

        List<BiblicalReference> references = new ArrayList<>();
        StringBuilder textWithoutRefs = new StringBuilder(query);
        int offsetAdjustment = 0;

        // Normalize query for matching (remove accents) but keep original for text extraction
        String normalizedQuery = removeAccents(query);
        Matcher matcher = BIBLE_REF_PATTERN.matcher(normalizedQuery);

        while (matcher.find()) {
            String prefix = matcher.group("prefix"); // "1 ", "2 ", "3 " or null
            String bookName = matcher.group("book"); // "corinthiens", "jn", etc.
            String chapter = matcher.group("chapter"); // "13"
            String verseStart = matcher.group("verseStart"); // "4" or null
            String verseEnd = matcher.group("verseEnd"); // "7" or null
            String version = matcher.group("version"); // "lsg", "nbs", etc. or null

            // Normalize and canonicalize book name (without prefix)
            String normalized = removeAccents(bookName.toLowerCase().trim());
            String canonical = CANONICAL_BOOKS.get(normalized);

            if (canonical != null) {
                // Add number prefix back to canonical name for numbered books
                if (prefix != null && !prefix.isBlank()) {
                    canonical = prefix.trim() + " " + canonical;
                }

                // Create biblical reference object
                BiblicalReference ref = new BiblicalReference();
                ref.setBook(canonical);
                ref.setChapter(chapter != null ? Integer.parseInt(chapter) : null);
                ref.setVerseStart(verseStart != null ? Integer.parseInt(verseStart) : null);
                ref.setVerseEnd(verseEnd != null ? Integer.parseInt(verseEnd) : null);
                ref.setVersion(version != null ? version.toLowerCase() : null);
                // Extract original text from the original query (not normalized)
                ref.setOriginalText(query.substring(matcher.start(), matcher.end()));
                ref.setStartIndex(matcher.start());
                ref.setEndIndex(matcher.end());

                references.add(ref);

                // Remove reference from text
                int start = matcher.start() - offsetAdjustment;
                int end = matcher.end() - offsetAdjustment;
                textWithoutRefs.delete(start, end);
                offsetAdjustment += (end - start);

                log.debug("Found biblical reference: '{}' → {}", matcher.group(0), ref.toNormalizedForm());
            }
        }

        // Clean up multiple spaces
        String cleanedText = textWithoutRefs.toString().replaceAll("\\s+", " ").trim();

        log.debug("Parsed query: '{}' → {} references, remaining text: '{}'", query, references.size(), cleanedText);

        return new ParseResult(cleanedText, references);
    }

    /**
     * Checks if a query contains biblical references without full parsing. Useful for quick classification.
     *
     * @param query
     *            Query to check
     * @return true if query contains at least one biblical reference
     */
    public boolean hasReferences(String query) {
        if (query == null || query.isBlank()) {
            return false;
        }
        return BIBLE_REF_PATTERN.matcher(query).find();
    }

    /**
     * Removes accents from text for normalization.
     */
    private String removeAccents(String text) {
        if (text == null) {
            return null;
        }
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{M}", "");
    }

    /**
     * Result of parsing operation.
     */
    @Data
    public static class ParseResult {

        private final String textWithoutReferences;
        private final List<BiblicalReference> references;

        public boolean hasReferences() {
            return references != null && !references.isEmpty();
        }
    }

    /**
     * Represents a parsed biblical reference.
     */
    @Data
    public static class BiblicalReference {

        private String book; // Canonical name: "Matthieu", "1 Corinthiens"
        private Integer chapter; // 8
        private Integer verseStart; // 21
        private Integer verseEnd; // null or 23 (for ranges)
        private String version; // "lsg", "nbs", etc. or null
        private String originalText; // Original matched text
        private int startIndex; // Position in original query
        private int endIndex;

        /**
         * Converts to normalized searchable format. Format: "livre_chapitre_versetDebut_versetFin"
         *
         * Examples: - "Matthieu 8:21" → "matthieu_8_21" - "Jean 3:16-18" → "jean_3_16_18" - "Romains 8" → "romains_8"
         */
        public String toNormalizedForm() {
            StringBuilder sb = new StringBuilder();

            // Book (lowercase, no accents, no spaces)
            String bookSlug = removeAccentsStatic(book.toLowerCase().replaceAll("\\s+", ""));
            sb.append(bookSlug);

            if (chapter != null) {
                sb.append("_").append(chapter);

                if (verseStart != null) {
                    sb.append("_").append(verseStart);

                    if (verseEnd != null && !verseEnd.equals(verseStart)) {
                        sb.append("_").append(verseEnd);
                    }
                }
            }

            return sb.toString();
        }

        /**
         * Converts to canonical display format. Format: "Livre chapitre:verset" or "Livre chapitre:verset-verset"
         *
         * Examples: - "Matthieu 8:21" - "Jean 3:16-18" - "Romains 8"
         */
        public String toCanonicalForm() {
            if (chapter == null) {
                return book;
            }

            if (verseStart == null) {
                return book + " " + chapter;
            }

            String result = book + " " + chapter + ":" + verseStart;

            if (verseEnd != null && !verseEnd.equals(verseStart)) {
                result += "-" + verseEnd;
            }

            return result;
        }

        private static String removeAccentsStatic(String text) {
            if (text == null) {
                return null;
            }
            String normalized = Normalizer.normalize(text, Normalizer.Form.NFD);
            return normalized.replaceAll("\\p{M}", "");
        }
    }
}
