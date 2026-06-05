package com.tuganire.translation.normalizer;

import java.util.List;
import java.util.Map;

/** Constants for French text normalization: hedges to strip and slang-to-standard mappings. */
public final class NormalizerConstants {

    private NormalizerConstants() {
    }

    /**
     * Hedge words/phrases that carry no semantic content and should be removed before lookup. Each entry is matched as
     * a whole word/token (case-insensitive).
     */
    public static final List<String> HEDGES = List.of("du coup", "genre", "en fait", "en gros", "genre de", "enfin",
            "bref", "quoi", "hein", "bon", "voilà", "eh bien", "ben", "ouais", "alors");

    /**
     * Slang-to-standard replacements applied before hedge removal. Keys are lowercase slang forms; values are their
     * normalized equivalents.
     */
    public static final Map<String, String> SLANG_TO_STANDARD = Map.ofEntries(Map.entry("c'est", "c'est"),
            Map.entry("c est", "c'est"), Map.entry("jsuis", "je suis"), Map.entry("j'suis", "je suis"),
            Map.entry("j suis", "je suis"), Map.entry("j'ai", "j'ai"), Map.entry("j ai", "j'ai"),
            Map.entry("t'as", "tu as"), Map.entry("t as", "tu as"), Map.entry("t'es", "tu es"),
            Map.entry("t es", "tu es"), Map.entry("y'a", "il y a"), Map.entry("y a", "il y a"),
            Map.entry("ya", "il y a"), Map.entry("c'était", "c'était"), Map.entry("c etait", "c'était"),
            Map.entry("chais pas", "je ne sais pas"), Map.entry("ch'ais pas", "je ne sais pas"),
            Map.entry("sais pas", "je ne sais pas"), Map.entry("sé pas", "je ne sais pas"), Map.entry("nan", "non"),
            Map.entry("ouais", "oui"), Map.entry("ouai", "oui"), Map.entry("mwa", "moi"), Map.entry("twa", "toi"),
            Map.entry("chuis", "je suis"), Map.entry("faut", "il faut"), Map.entry("on va", "nous allons"),
            Map.entry("on peut", "nous pouvons"), Map.entry("on est", "nous sommes"), Map.entry("on a", "nous avons"),
            Map.entry("on fait", "nous faisons"), Map.entry("on dit", "nous disons"),
            Map.entry("on doit", "nous devons"), Map.entry("on veut", "nous voulons"), Map.entry("ça", "cela"),
            Map.entry("ca", "cela"), Map.entry("truc", "chose"), Map.entry("machin", "chose"),
            Map.entry("sympa", "sympathique"), Map.entry("super", "très bien"), Map.entry("nickel", "parfait"),
            Map.entry("cool", "bien"), Map.entry("kiffer", "apprécier"), Map.entry("kiffe", "apprécie"),
            Map.entry("kiffé", "apprécié"), Map.entry("grave", "vraiment"), Map.entry("trop", "très"),
            Map.entry("vachement", "vraiment"), Map.entry("carrément", "vraiment"),
            Map.entry("franchement", "vraiment"), Map.entry("bouffe", "nourriture"), Map.entry("bouffer", "manger"),
            Map.entry("boulot", "travail"), Map.entry("taff", "travail"), Map.entry("mec", "homme"),
            Map.entry("meuf", "femme"), Map.entry("pote", "ami"), Map.entry("fric", "argent"),
            Map.entry("thune", "argent"), Map.entry("flotte", "eau"), Map.entry("becqueter", "manger"),
            Map.entry("se casser", "partir"), Map.entry("se barrer", "partir"), Map.entry("se tirer", "partir"),
            Map.entry("vite fait", "rapidement"), Map.entry("pas mal", "bien"), Map.entry("de ouf", "extraordinaire"),
            Map.entry("ouf", "extraordinaire"), Map.entry("chelou", "bizarre"), Map.entry("zarbi", "bizarre"),
            Map.entry("relou", "ennuyeux"), Map.entry("galère", "difficulté"),
            Map.entry("galérer", "avoir des difficultés"));

    /** Pattern fragment matching any one hedge token; built from HEDGES at class load time. */
    public static final String HEDGE_REGEX_FRAGMENT = buildHedgePattern();

    private static String buildHedgePattern() {
        // Sort descending by length so longer phrases match before shorter ones.
        return HEDGES.stream().sorted((a, b) -> Integer.compare(b.length(), a.length()))
                .map(h -> "\\b" + java.util.regex.Pattern.quote(h) + "\\b").reduce((a, b) -> a + "|" + b).orElse("");
    }
}
