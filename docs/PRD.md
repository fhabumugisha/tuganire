# 📋 PRD — Tuganire MVP

**Product Requirements Document**
**Version** : 1.1
**Date** : Mai 2026
**Auteur** : Fabrice Habumugisha
**Statut** : Validation terminée — prêt pour développement
**Stack cible** : Spring Boot 4.0 + Spring AI 2.0-M4 + Java 21 (cutting edge)

---

## 1. 🎯 Vision produit

### Le pitch en une phrase

> **Tuganire** est l'application de traduction conversationnelle français ↔ kinyarwanda, conçue par un locuteur natif rwandais, qui comprend les nuances culturelles et linguistiques que Google Translate ignore.

### Le problème

Lors d'un voyage au Rwanda, un voyageur francophone fait face à plusieurs frustrations majeures :

1. **Google Translate Live Conversation ne supporte pas le kinyarwanda** en mode temps réel
2. **La synthèse vocale kinyarwanda est inexistante ou inutilisable** sur les outils mainstream
3. **Les traductions produites sont grammaticalement faussées** (5 catégories d'erreurs structurelles identifiées chez GPT-4o et Claude)
4. **Aucun outil ne respecte les codes culturels rwandais** (politesse, négociation, alternance de code)

### La vision

Une application **mobile-first**, simple d'usage, qui permet une **conversation fluide** entre un francophone et un kinyarwandophone dans les situations courantes du voyage : transport, marché, restaurant, santé, social.

L'app combine :
- 🧠 Un **LLM (GPT-4o)** pour la traduction de base
- 🛡️ Une **couche d'expertise native** (dictionnaire d'or, règles correctives)
- 🎙️ Des **technologies vocales** adaptées (STT + TTS pour les deux langues)
- 🔄 Une **boucle de feedback utilisateur** pour amélioration continue

### Métrique de succès MVP

> **Deux personnes ne parlant pas la même langue tiennent une conversation utile de 3 minutes dans un contexte réel (transport, marché, restaurant) avec un taux de compréhension mutuelle supérieur à 80%.**

---

## 2. 👥 Personas et user stories

### Persona 1 — Marc, le voyageur francophone

**Profil :**
- 38 ans, ingénieur français
- Voyage 1-2 fois par an en Afrique
- N'a jamais appris le kinyarwanda
- Anglais correct mais préfère sa langue maternelle
- Smartphone Android, connexion 4G inégale

**Besoins :**
- Se faire comprendre dans les situations courantes
- Comprendre ce que les locaux lui répondent
- Négocier au marché sans paraître agressif
- Apprendre quelques mots clés au fil du voyage

### Persona 2 — Mukamana, vendeuse au marché de Kimironko

**Profil :**
- 45 ans, commerçante à Kigali
- Parle kinyarwanda + un peu de swahili
- Comprend quelques mots de français/anglais
- Smartphone Android d'entrée de gamme
- Habituée à servir des touristes étrangers

**Besoins :**
- Comprendre ce que veut le touriste
- Pouvoir répondre dans sa langue
- Négocier les prix sereinement
- Ne pas se sentir condescendue par la technologie

### User stories prioritaires MVP

| ID | User Story | Priorité | Sprint |
|----|------------|----------|--------|
| US-01 | En tant que Marc, je veux **parler en français** et que l'app **traduise vocalement en kinyarwanda** pour que Mukamana comprenne | P0 | 1-3 |
| US-02 | En tant que Mukamana, je veux **parler en kinyarwanda** et que l'app **traduise vocalement en français** pour que Marc comprenne | P0 | 1-3 |
| US-03 | En tant qu'utilisateur, je veux **voir le texte transcrit + traduit** à l'écran pour confirmer la compréhension | P0 | 1-3 |
| US-04 | En tant qu'utilisateur, je veux **choisir entre 2 modes** : "deux boutons" ou "split-screen face-à-face" | P1 | 4 |
| US-05 | En tant qu'utilisateur, je veux **réécouter la dernière traduction** d'une simple touche | P1 | 4 |
| US-06 | En tant qu'utilisateur, je veux **signaler une mauvaise traduction** via 👍/👎 pour améliorer l'app | P1 | 4 |
| US-07 | En tant qu'utilisateur, je veux **utiliser l'app sans créer de compte** au début | P0 | 1 |
| US-08 | En tant que Marc, je veux que les **toponymes rwandais** (Nyamirambo, Kimihurura) soient **préservés intacts** dans la traduction | P0 | 2 |
| US-09 | En tant qu'utilisateur, je veux pouvoir **changer la voix TTS** (provider) dans les réglages pour comparer | P2 | 5 |
| US-10 | En tant qu'utilisateur, l'app doit fonctionner avec une **connexion 4G classique** sans coupure | P0 | 1-3 |

---

## 3. 🎯 Périmètre MVP

### ✅ Inclus (MVP V1)

**Fonctionnel :**
- Traduction vocale FR → RW (Marc parle)
- Traduction vocale RW → FR (Mukamana parle)
- Affichage du texte transcrit + traduit
- 2 modes d'interaction : "deux boutons" et "split-screen"
- Réécoute de la dernière traduction
- Système de feedback 👍/👎 par traduction
- Préservation automatique des **toponymes** et **emprunts** (cool, meeting, wifi)
- Adaptation du **registre** (pluriel de respect par défaut)
- **Dictionnaire d'or** d'overrides (~50 phrases courantes validées natif)

**Plateformes :**
- Application Android (Kotlin + Jetpack Compose)
- Application Web (Thymeleaf + Tailwind CSS 4 + HTMX + Alpine.js) en complément, pour démo et tests communautaires

**Backend :**
- API REST Spring Boot 4.0 + Java 21 + Spring AI 2.0-M4
- WebSocket pour streaming temps réel
- Pipeline STT → Traduction LLM + post-processing → TTS
- Cache Redis pour traductions fréquentes
- Logging structuré et observabilité basique
- API Versioning natif Spring Boot 4 (nouveauté)
- HTTP Service Clients `@HttpExchange` typés

**Internationalisation :**
- UI bilingue FR + RW (l'app doit être utilisable par Marc ET Mukamana)
- UI anglaise en option (touristes anglophones futurs)

### ❌ Exclus du MVP (Phase 2+)

- Authentification utilisateur
- Historique persistant des conversations
- Mode offline / packs de langues
- Multi-langues (swahili, anglais en traduction)
- Application iOS
- Mode "phrasebook culturel" / proverbes
- Fine-tuning d'un modèle custom
- Monétisation / freemium

---

## 4. 📊 Validation linguistique (déjà réalisée)

### Données empiriques collectées

| Test | Phrases testées | LLM gagnant | Score moyen | Erreurs structurelles identifiées |
|------|------------------|--------------|-------------|----------------------------------|
| Phrases voyageur de base | 25 | GPT-4o > Claude | 7/10 | 3 catégories |
| Registres de langue (soutenu → argot) | 10 | GPT-4o | ~6/10 | + 1 catégorie |
| Subtilités françaises (pragmatique, hedges) | 10 | GPT-4o | ~6/10 | confirmation |
| Différenciateurs rwandais (toponymes, négociation) | 20 | GPT-4o | 6/10 | + 1 catégorie |
| **TOTAL** | **65** | **GPT-4o** | **~6,5/10** | **5 catégories** |

### Les 5 catégories d'erreurs structurelles documentées

| # | Catégorie | Exemple erreur | Correction native | Détectabilité |
|---|-----------|----------------|---------------------|----------------|
| 1 | Infixe pronominal manquant | "Kurya" (manger) | "Kundya" (me manger) | Subtile |
| 2 | Confusion tu/vous | "Ndakwinginze" | "Ndabinginze" | Subtile |
| 3 | Empilement de politesse | "Mwambabarira ... ndabinginze" | "Ndabinginze" seul | Bizarre socialement |
| 4 | Invention de mots | "Arakool", "Ndziho" | "Ni cool", "umutegetsi wanjye" | **Critique** |
| 5 | Calque syntaxique français | "Mwampa kugabanyirizwa" | "Mwangabanyiriza" | Subtile mais bloquante |

### Conclusion stratégique

> **Aucun LLM occidental actuel (GPT-4o, Claude) ne maîtrise le kinyarwanda conversationnel à un niveau professionnel.**
> Tuganire DOIT contenir une **couche d'expertise native** (post-processor, dictionnaire d'or, few-shot prompting) en plus du LLM.

### Validation audio (réalisée)

| Brique | Outil testé | Verdict |
|--------|-------------|---------|
| **TTS kinyarwanda** | Meta MMS-TTS (`facebook/mms-tts-kin`) | ✅ **Validé** — voix intelligible, comprise par un locuteur natif. Utilisable pour le MVP sans recourir à ElevenLabs payant |
| TTS français | Web Speech API navigateur | ✅ Validé (qualité native) |
| STT français | Web Speech API navigateur | 🟢 Quasi-certain (API mature) |
| STT kinyarwanda | OpenAI Whisper | 🟡 À confirmer pendant le développement Sprint 2 |

**Conséquence architecture** : MMS-TTS retenu comme provider TTS kinyarwanda principal. Pas de coût TTS récurrent pour le MVP.

---

## 5. 🎨 UX et interactions

### Écran principal — Mode "deux boutons"

```
┌─────────────────────────────────────┐
│ Tuganire           ⚙️ 🎙️ 📊         │ ← Barre du haut
├─────────────────────────────────────┤
│                                       │
│   "Bonjour, je cherche le marché"    │ ← Bulle FR
│                              🇫🇷 👤  │
│                                       │
│       "Muraho, ndashaka isoko"       │ ← Bulle RW
│   🤖 👍 👎  🔊                      │
│                                       │
│   "Iri kuwa Kimihurura"              │ ← Bulle RW (Mukamana)
│   👤 🇷🇼                            │
│                                       │
│       "C'est à Kimihurura"           │ ← Bulle FR
│   🤖 👍 👎  🔊                      │
│                                       │
├─────────────────────────────────────┤
│                                       │
│  ┌──────────┐         ┌──────────┐  │
│  │   🇫🇷    │         │   🇷🇼    │  │
│  │ Parler   │         │ Kuvuga   │  │
│  │ français │         │ ikinyaRW │  │
│  └──────────┘         └──────────┘  │
│                                       │
└─────────────────────────────────────┘
```

### Écran principal — Mode "split-screen face-à-face"

```
┌─────────────────────────────────────┐
│  🇫🇷 Marc          ↻         🇷🇼 Muka │
├─────────────────────────────────────┤
│                                       │
│  Bonjour, je cherche                  │
│  le marché                            │  ← Côté Marc
│                                       │
│  🎤 Parler                            │
│                                       │
├──────────────  ↕  ───────────────────│
│                                       │
│  Muraho, ndashaka                     │
│  isoko                                │  ← Côté Mukamana (inversé)
│                                       │
│  🎤 Kuvuga                            │
│                                       │
└─────────────────────────────────────┘
```

Le **côté Mukamana est physiquement inversé** (rotation 180°) pour qu'elle puisse lire confortablement quand le téléphone est posé entre les deux.

### Règles UX strictes

| Règle | Justification |
|-------|---------------|
| **0 friction au démarrage** : pas d'inscription, pas de tutoriel obligatoire | Marc utilise l'app en situation d'urgence sociale |
| **Boutons gros et accessibles** | Mukamana et Marc partagent le téléphone, conditions variables (lumière, stress) |
| **Indicateur visuel d'écoute** clair (animation pulsée) | Indispensable quand le LLM met 1-2s à répondre |
| **Lecture vocale automatique** dès traduction prête | Sinon l'interlocuteur attend |
| **Affichage texte ET audio** systématiques | Permet vérification visuelle si on a mal entendu |
| **Feedback 👍/👎 visible mais non intrusif** | Pour collecter les corrections sans alourdir l'usage |

---

## 6. 🌍 Spécifications linguistiques

### Stratégie de traduction Tuganire

```
┌─────────────────────────────────────────────────────────────┐
│  Pipeline de traduction FR → RW                              │
└─────────────────────────────────────────────────────────────┘

  Marc parle FR
       │
       ▼
  ┌────────────────────────────┐
  │ STT : Web Speech API       │
  │ (français, gratuit)        │
  └─────────┬──────────────────┘
            │ texte FR
            ▼
  ┌────────────────────────────┐
  │ NORMALIZER FR              │
  │ - Argot → standard         │
  │ - Hedges supprimés         │
  │ - "On" désambiguïsé        │
  └─────────┬──────────────────┘
            │ texte FR normalisé
            ▼
  ┌────────────────────────────┐
  │ DICTIONNAIRE D'OR          │
  │ (50+ phrases pré-validées) │
  └─────────┬──────────────────┘
            │ HIT ? → traduction directe (skip LLM)
            │ MISS ? → 
            ▼
  ┌────────────────────────────┐
  │ LLM (GPT-4o)               │
  │ + prompt système strict    │
  │ + few-shot (10 exemples)   │
  └─────────┬──────────────────┘
            │ traduction RW brute
            ▼
  ┌────────────────────────────┐
  │ POST-PROCESSOR             │
  │ - Correction infixes -n-   │
  │ - Pluriel respect forcé    │
  │ - Anti-empilement politesse│
  │ - Détection mots inventés  │
  │ - Anti-calque syntaxique   │
  └─────────┬──────────────────┘
            │ traduction RW corrigée
            ▼
  ┌────────────────────────────┐
  │ TTS RW (MMS-TTS)           │
  │ ou OpenAI gpt-4o-mini-tts  │
  └─────────┬──────────────────┘
            │ audio MP3
            ▼
       Mukamana entend
```

### Pipeline inverse RW → FR

```
  Mukamana parle RW
       │
       ▼
  STT : OpenAI Whisper (kinyarwanda supporté)
       │
       ▼
  LLM (GPT-4o) avec prompt système pragmatique RW→FR
       │
       ▼
  Post-processor FR (nettoyage, naturel)
       │
       ▼
  TTS FR : Web Speech API (gratuit, qualité native)
       │
       ▼
  Marc entend
```

### Règles linguistiques implémentées (les 10 règles)

1. Pluriel de respect obligatoire (interactions inconnu)
2. Pas d'empilement de politesse
3. Infixes pronominaux bantu corrects
4. Structures bantu naturelles (pas de calque)
5. Anti-hallucination : emprunts invariables
6. Toponymes rwandais intacts
7. Alternance de code naturelle (FR/RW/EN)
8. Chiffres et prix sous forme parlée
9. Négociation polie non-agressive
10. Verbe direct privilégié au calque syntaxique

---

## 7. 📈 Métriques de succès

### Métriques produit (à mesurer post-lancement)

| Métrique | Cible MVP | Méthode de mesure |
|----------|-----------|---------------------|
| Taux de conversations menées à terme | ≥ 70% | Session > 3 min sans abandon |
| Score moyen 👍/👎 | ≥ 75% positif | Feedback intégré |
| Latence aller-retour FR→RW→audio | ≤ 3 secondes p95 | Logs backend |
| Taux d'utilisation du mode split-screen | ≥ 30% | Tracking d'interaction |
| Nombre de corrections natives collectées | ≥ 100 en 1er mois | Système feedback |

### Métriques techniques

| Métrique | Cible MVP |
|----------|-----------|
| Disponibilité backend | 99% |
| Temps de réponse API REST p95 | < 500ms |
| Taux d'erreur TTS | < 5% |
| Taux cache hit (traductions fréquentes) | > 40% (après 1 mois) |
| Coût API par conversation moyenne | < 0,05 € |

---

## 8. 🛤️ Roadmap MVP (10 semaines, 5-10h/sem)

### Sprint 1 — Backend foundations (semaines 1-2)
- ✅ Setup projet Spring Boot 4.0 + Java 21 + Maven
- ✅ Configuration Spring AI 2.0-M4 + Jackson 3 + JSpecify null-safety
- ✅ Endpoint REST `/api/v1/translate` avec Spring AI + GPT-5 (default) ou GPT-4o
- ✅ Prompt système avec les 10 règles linguistiques
- ✅ Dictionnaire d'or initial (50 entrées validées)
- ✅ Tests unitaires JUnit 5 + Mockito
- ✅ Déploiement Cloud Run
- **Démo** : `curl POST /translate` retourne une traduction FR→RW correcte

### Sprint 2 — Pipeline complet (semaines 3-4)
- ✅ Intégration STT (Whisper côté backend pour RW)
- ✅ Intégration TTS (MMS-TTS auto-hébergé + OpenAI TTS configurable)
- ✅ Post-processor avec les 5 catégories d'erreurs
- ✅ Cache Redis
- **Démo** : envoi d'un .wav, retour d'un .mp3 traduit en bout-en-bout

### Sprint 3 — POC Web (semaines 5-6)
- ✅ Interface Thymeleaf + Tailwind CSS 4 + HTMX + Alpine.js
- ✅ Mode "deux boutons" fonctionnel
- ✅ Web Speech API pour STT français
- ✅ Système feedback 👍/👎
- **Démo** : URL publique partageable, conversation FR↔RW fonctionnelle

### Sprint 4 — App Android (semaines 7-8)
- ✅ Setup projet Kotlin + Jetpack Compose
- ✅ Capture audio AudioRecord + ExoPlayer
- ✅ Client WebSocket OkHttp
- ✅ Mode "deux boutons" Android
- **Démo** : APK installable, fonctionnalités identiques au web

### Sprint 5 — Polish & launch (semaines 9-10)
- ✅ Mode "split-screen face-à-face"
- ✅ Onboarding minimal (3 écrans)
- ✅ Provider TTS switchable dans les réglages
- ✅ Vidéo démo + screenshots
- ✅ Publication GitHub + README
- **Démo** : APK + URL web publiés, prêts pour portfolio et beta-testeurs

---

## 9. ⚠️ Risques et mitigations

| Risque | Probabilité | Impact | Mitigation |
|--------|-------------|--------|------------|
| Qualité TTS kinyarwanda décevante (MMS-TTS robotique) | Faible | Élevé | ✅ **Risque levé** : MMS-TTS validé en test par locuteur natif. Plan B ElevenLabs conservé si besoin de qualité premium en Phase 2 |
| Latence cumulée STT+LLM+TTS > 3s | Moyenne | Élevé | Cache Redis agressif, streaming partiel TTS sur transcript progressif |
| Coût API GPT-4o/GPT-5 explose avec usage | Faible | Moyen | Rate limiting par session, dictionnaire d'or absorbe 40% des requêtes |
| Le LLM continue d'halluciner malgré le prompt | Élevée | Élevé | Post-processor strict + dictionnaire d'or + boucle feedback natif |
| Pas assez de feedback utilisateurs en V1 | Moyenne | Moyen | Sollicitation explicite communauté rwandaise expatriée (groupes FB, WhatsApp) |
| Bugs critiques Android (compatibilité) | Moyenne | Moyen | Tests sur 3-4 modèles Android (Samsung, Xiaomi entrée gamme, Pixel) |
| **Spring AI 2.0-M4 → GA : breaking changes API** | **Élevée** | **Moyen** | **Veille active sur les milestones, migration prévue 1-2 fois entre M4 et GA. Code organisé en couches pour minimiser l'impact** |
| **Documentation Spring AI 2.0 moins fournie** | **Moyenne** | **Faible** | **Référence : code source GitHub Spring AI, articles 2026 récents, communauté Slack** |

---

## 10. 🎯 Critères d'acceptation MVP

Tuganire MVP est considéré comme **terminé et publiable** quand :

- ✅ Les 10 user stories P0 sont implémentées et testées
- ✅ Une conversation type voyage (transport + marché + restaurant) tient 3 minutes sans crash
- ✅ Le score moyen 👍/👎 sur 50 traductions test est ≥ 70%
- ✅ La latence aller-retour FR→RW→audio est ≤ 3s sur connexion 4G
- ✅ L'APK est installable sur Android 10+
- ✅ Le repo GitHub est public avec README complet et démo vidéo
- ✅ Le dictionnaire d'or contient ≥ 50 entrées validées natif
- ✅ Au moins 5 testeurs Rwandais natifs ont validé le produit en conditions réelles

---

## 11. 🚀 Phase 2 et au-delà (post-MVP)

### Phase 2 — Différenciation (mois 4-6)
- Mode offline (modèles quantizés MMS + Whisper sur device)
- Fine-tuning NLLB-200 sur corpus FR-RW collecté
- Authentification + historique
- Publication d'un papier de recherche NLP

### Phase 3 — Expansion (mois 7-12)
- Application iOS
- Ajout du swahili (Tanzanie, Kenya, Ouganda)
- Ajout de l'anglais
- Monétisation freemium (voix premium, illimité)

### Phase 4 — Communauté (an 2)
- Dictionnaire d'or collaboratif (corrections crowdsourcées)
- Mode apprentissage (phrasebook culturel)
- Partenariat tourisme rwandais
- Ouverture aux autres langues low-resource africaines (lingala, kikuyu)

---

## 12. 📚 Annexes

- `ARCHI.md` : architecture technique détaillée
- `corrections.yaml` : dictionnaire d'or et catalogue d'erreurs
- `validation_results.md` : résultats détaillés des 65 phrases testées
- Repo GitHub : `github.com/fhabumugisha/tuganire` (à créer)

---

**Document validé pour démarrage du développement.**
*Tuganire — parce que la langue ne devrait jamais être une barrière.* 🌍
