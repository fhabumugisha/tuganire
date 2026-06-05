package com.tuganire.llm.prompt;

import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Builds the system and user prompts used by every LLM translation call.
 *
 * <p>
 * The system prompt encodes the 10 linguistic rules for French ↔ Kinyarwanda translation plus ~10 few-shot examples
 * drawn from PRD section 4/6. Keeping these as constants makes them easy to audit and update without rebuilding the
 * Spring context.
 */
@Component
public class TranslationPromptBuilder {

    // -------------------------------------------------------------------------
    // 10 linguistic rules (PRD section 6)
    // -------------------------------------------------------------------------

    static final String RULE_PLURAL_RESPECT = "1. PLURIEL DE RESPECT (obligatoire): Toujours utiliser le pluriel de respect pour s'adresser "
            + "à un inconnu en kinyarwanda. Utiliser 'mwe' au lieu de 'we', 'muraho' au lieu de 'uraho', "
            + "'mwamfasha' → 'mwangafasha', 'ndakwinginze' → 'ndabinginze', 'mbwira' → 'mwambwira'.";

    static final String RULE_NO_POLITENESS_STACKING = "2. PAS D'EMPILEMENT DE POLITESSE: Ne jamais combiner plusieurs formules de politesse "
            + "('Mwambabarira ndabinginze' est redondant — utiliser uniquement 'Ndabinginze'). "
            + "Une seule formule de politesse par phrase suffit.";

    static final String RULE_BANTU_INFIXES = "3. INFIXES PRONOMINAUX BANTU CORRECTS: Respecter les infixes pronominaux de l'objet bantu. "
            + "Exemples: 'Kurya' (manger) devient 'Kundya' (me nourrir), 'guha' devient 'kumpa' (me donner). "
            + "Ne jamais omettre l'infixe pronominal quand le contexte l'exige.";

    static final String RULE_NATURAL_BANTU_STRUCTURES = "4. STRUCTURES BANTU NATURELLES: Utiliser des structures verbales naturelles du kinyarwanda. "
            + "Éviter les calques syntaxiques du français. Exemple: 'Mwangabanyiriza' (direct) "
            + "plutôt que 'Mwampa kugabanyirizwa' (calque). Préférer le verbe direct.";

    static final String RULE_ANTI_HALLUCINATION = "5. ANTI-HALLUCINATION — EMPRUNTS INVARIABLES: Les emprunts du français ou de l'anglais "
            + "restent invariables. Ne JAMAIS inventer de faux mots kinyarwanda. "
            + "Exemples corrects: 'cool' reste 'cool', 'meeting' reste 'meeting', 'wifi' reste 'wifi', "
            + "'taxi' reste 'taxi', 'bus' reste 'bus'. Si le mot exact n'existe pas en kinyarwanda, "
            + "utiliser l'emprunt tel quel.";

    static final String RULE_INTACT_TOPONYMS = "6. TOPONYMES INTACTS: Les noms de lieux rwandais doivent être préservés exactement "
            + "tels quels, sans modification. Exemples: Kimihurura, Nyamirambo, Kimironko, Kigali, "
            + "Musanze, Huye, Rubavu, Nyanza — ne jamais les traduire ou les modifier.";

    static final String RULE_CODE_SWITCHING = "7. ALTERNANCE DE CODE NATURELLE: Le kinyarwanda oral mélange naturellement FR/RW/EN. "
            + "Accepter et reproduire les alternances naturelles comme: 'Hari problem' (il y a un problème), "
            + "'Ni okay' (c'est okay), 'Ndashaka internet' (je veux internet). "
            + "Ne pas forcer une purisme linguistique artificiel.";

    static final String RULE_SPOKEN_NUMBERS = "8. CHIFFRES ET PRIX SOUS FORME PARLÉE: Exprimer les nombres et prix en forme parlée "
            + "kinyarwanda quand approprié. Exemples: 2000 francs = 'ibihumbi bibiri', "
            + "500 = 'magana atanu', 10 000 = 'ibihumbi icumi'. "
            + "Adapter selon le contexte (marché, transport, restaurant).";

    static final String RULE_POLITE_NEGOTIATION = "9. NÉGOCIATION POLIE NON-AGRESSIVE: Dans les contextes de marché ou négociation, "
            + "utiliser un registre poli et non-agressif. Éviter les formulations trop directes "
            + "qui pourraient sembler impolies culturellement. "
            + "Utiliser des formulations comme 'Mbese...' (est-ce que...) pour adoucir les demandes.";

    static final String RULE_DIRECT_VERB = "10. VERBE DIRECT PRIVILÉGIÉ: Privilégier la construction verbale directe kinyarwanda "
            + "plutôt que le calque syntaxique du français. "
            + "Exemple: 'Ndashaka kurya' (je veux manger) plutôt que 'Ndashaka gukora kurya'. "
            + "Le kinyarwanda a ses propres constructions verbales: les respecter.";

    // -------------------------------------------------------------------------
    // Few-shot examples (PRD sections 4/6 — FR→RW validated translations)
    // -------------------------------------------------------------------------

    static final String EXAMPLE_01 = "FR: \"Bonjour, où est le marché ?\" → RW: \"Muraho, isoko iri he?\"";

    static final String EXAMPLE_02 = "FR: \"Excusez-moi, combien ça coûte ?\" → RW: \"Ndabinginze, bigurisha angahe?\"";

    static final String EXAMPLE_03 = "FR: \"Je voudrais aller à Kimihurura\" → RW: \"Ndashaka kujya Kimihurura\"";

    static final String EXAMPLE_04 = "FR: \"C'est trop cher, pouvez-vous baisser le prix ?\" → "
            + "RW: \"Bihenze cyane, mbese mwangabanyiriza?\"";

    static final String EXAMPLE_05 = "FR: \"Merci beaucoup\" → RW: \"Murakoze cyane\"";

    static final String EXAMPLE_06 = "FR: \"Où est la pharmacie la plus proche ?\" → RW: \"Farmasi iri hafi iri he?\"";

    static final String EXAMPLE_07 = "FR: \"Je ne comprends pas, pouvez-vous répéter ?\" → "
            + "RW: \"Sintekereza, mbese mwongera kuvuga?\"";

    static final String EXAMPLE_08 = "FR: \"Le taxi pour Nyamirambo, combien ça coûte ?\" → "
            + "RW: \"Taxi igana Nyamirambo ni angahe?\"";

    static final String EXAMPLE_09 = "FR: \"Avez-vous du wifi ici ?\" → RW: \"Mufite wifi hano?\"";

    static final String EXAMPLE_10 = "FR: \"Je voudrais une chambre pour deux nuits\" → "
            + "RW: \"Ndashaka icumbi kir'ijoro ibiri\"";

    // -------------------------------------------------------------------------
    // Prompt construction
    // -------------------------------------------------------------------------

    /**
     * Builds the complete system prompt encoding all 10 linguistic rules and at least 8 few-shot examples.
     *
     * @return the system prompt string
     */
    public String buildSystemPrompt() {
        return """
                Tu es un traducteur expert en français ↔ kinyarwanda, conçu pour des conversations \
                courantes de voyage au Rwanda (transport, marché, restaurant, santé, social).

                Tu dois suivre ces 10 règles linguistiques OBLIGATOIRES :

                """ + RULE_PLURAL_RESPECT + "\n\n" + RULE_NO_POLITENESS_STACKING + "\n\n" + RULE_BANTU_INFIXES + "\n\n"
                + RULE_NATURAL_BANTU_STRUCTURES + "\n\n" + RULE_ANTI_HALLUCINATION + "\n\n" + RULE_INTACT_TOPONYMS
                + "\n\n" + RULE_CODE_SWITCHING + "\n\n" + RULE_SPOKEN_NUMBERS + "\n\n" + RULE_POLITE_NEGOTIATION
                + "\n\n" + RULE_DIRECT_VERB + "\n\n" + """
                        EXEMPLES DE RÉFÉRENCE (traductions validées par locuteur natif):

                        """ + EXAMPLE_01 + "\n" + EXAMPLE_02 + "\n" + EXAMPLE_03 + "\n" + EXAMPLE_04 + "\n" + EXAMPLE_05
                + "\n" + EXAMPLE_06 + "\n" + EXAMPLE_07 + "\n" + EXAMPLE_08 + "\n" + EXAMPLE_09 + "\n" + EXAMPLE_10
                + "\n" + """

                        INSTRUCTIONS DE FORMAT:
                        - Retourne UNIQUEMENT la traduction, sans explication ni commentaire.
                        - Ne mets pas de guillemets autour de la traduction.
                        - Si tu n'es pas certain d'un mot, utilise l'emprunt plutôt qu'inventer.
                        """;
    }

    /**
     * Builds the user-turn prompt for a specific translation request.
     *
     * @param text
     *            the text to translate
     * @param src
     *            the source language locale
     * @param tgt
     *            the target language locale
     * @return the user prompt string
     */
    public String buildUserPrompt(String text, Locale src, Locale tgt) {
        String srcName = localeName(src);
        String tgtName = localeName(tgt);
        return "Traduis ce texte de " + srcName + " en " + tgtName + ":\n" + text;
    }

    private static String localeName(Locale locale) {
        return switch (locale.getLanguage()) {
            case "fr" -> "français";
            case "rw" -> "kinyarwanda";
            case "en" -> "anglais";
            default -> locale.getDisplayLanguage(Locale.FRENCH);
        };
    }
}
