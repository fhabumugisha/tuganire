# Plan — Voix native rwandaise par fine-tuning (outils 100% gratuits)

> Objectif : remplacer la voix Kinyarwanda **robotique** du stock `facebook/mms-tts-kin`
> par une voix **native, naturelle**, en fine-tunant un seul locuteur rwandais, sans aucun coût.
>
> Artefacts prêts à l'emploi : [`tts-finetune/`](../tts-finetune/README.md).

## 1. État actuel (repo)

`tts-server/` (FastAPI) sert `facebook/mms-tts-kin` (Meta MMS, modèle VITS mono-locuteur générique).
Limite connue : articulation plate, « accent étranger », mots collés — déjà atténué par des réglages
(`speaking_rate`, `noise_scale`) mais **pas suffisant pour du « natif optimal »**. La seule vraie
solution = **fine-tuning** sur la voix d'un locuteur rwandais réel.

## 2. Choix technique : fine-tuner MMS (VITS), pas cloner XTTS

| Option | Verdict Kinyarwanda |
|---|---|
| **Fine-tune `facebook/mms-tts-kin` (VITS)** ✅ | Connaît déjà la **phonétique rw**. 83M params, 1 GPU, bons résultats dès **20 min / 80-150 phrases**, entraînement ~20-60 min. Licence libre. Swap sans changer l'API. |
| Coqui XTTS-v2 ❌ | Coqui fermé (déc. 2025), fork communautaire. **Kinyarwanda non supporté** (16 langues). Licence CPML sans vendeur → risque pour un SaaS. |
| F5-TTS / StyleTTS2 ⚠️ | Meilleure qualité brute mais réentraîner une langue low-resource = bien plus de données/compute. Hors cadre « gratuit/rapide ». |

**Atout décisif :** le kinyarwanda s'écrit en **alphabet latin** → tokenizer MMS *character-based*,
**ni uroman ni espeak-ng** requis. C'est le cas de fine-tuning VITS le plus simple.

## 3. Données — le facteur n°1 de qualité (80% du résultat)

Deux sources gratuites, combinables :

1. **Enregistrer un locuteur natif (recommandé)** — la voix « de marque » du produit.
   - Volume : **30 min min**, 1h confortable, **2-3h excellent**.
   - Qualité : pièce silencieuse, micro correct, **mono, ≥16 kHz**, sans réverbe.
   - Phrases : variées phonétiquement + **ton vocabulaire** (pastoral, Amahoro/Mukamana, chiffres).
   - `make_prompts.py` génère la liste à lire (Common Voice rw + tes phrases).
   - Astuce : enregistrer une phrase = une piste → **le texte du prompt = la transcription**
     (alignement parfait, pas besoin de Whisper).

2. **Mozilla Common Voice — Kinyarwanda** (CC0) — ~**2000 h validées, 1181 locuteurs**
   (2ᵉ langue de Common Voice). Multi-locuteur crowdsourcé : sert de **banque de phrases** à lire,
   ou pour augmenter via un **sous-ensemble mono `client_id`** propre.

Pipeline (script `prepare_dataset.py`) :
`brut → ffmpeg (mono/16kHz/loudnorm/trim) → split silence → (Whisper-rw à relire) → metadata.csv`.
Transcription/vérif : `mbazaNLP/Whisper-Small-Kinyarwanda` (déjà repéré dans la note STT). **Relire metadata.csv.**

## 4. Fine-tuning — compute gratuit

Outil officiel : [`ylacombe/finetune-hf-vits`](https://github.com/ylacombe/finetune-hf-vits).
Étapes (notebook `finetune_kaggle.ipynb`) : clone → build discriminateur → config → `accelerate launch` → A/B → push Hub.

| Plateforme gratuite | GPU | Adapté |
|---|---|---|
| **Kaggle** | T4/P100, ~30h/sem | ✅ idéal (quota généreux pour itérer) |
| **Google Colab** | T4 16 Go | ✅ parfait (VITS = 83M) |
| HF Spaces / Lightning | crédits ponctuels | ⚠️ dépannage |

Durée : **< 1h sur T4** pour une première voix exploitable. Config dans `finetune_config.json`
(epochs ~200 pour 30-60 min ; baisser si artefacts métalliques, monter si encore robotique).

## 5. Évaluation & itération

- A/B sur phrases **hors training** (cellule 6 du notebook) : naturel, consonnes rw, prosodie, artefacts.
- Jeu de validation **figé** pour comparer objectivement les versions.
- Boucle : robotique → +données/+epochs ; artefacts/sur-apprentissage → −epochs ou +variété.

## 6. Déploiement — zéro changement d'archi

`tts_server.py` lit désormais l'id du modèle depuis une variable d'env :
```
MMS_RW_MODEL=YOUR_HF_USERNAME/mms-tts-kin-native
```
La définir sur le service `tts` et redémarrer. API `/tts`, Dockerfile, intégration Spring : **inchangés**.
C'est un simple swap de poids (`railway up --service tts`).

## 7. Coût total : 0 €

| Poste | Outil gratuit |
|---|---|
| Modèle de base | `facebook/mms-tts-kin` (libre) |
| Dataset | Enregistrement + Common Voice rw (CC0) |
| Prép/transcription | ffmpeg, Whisper-rw, (MFA) |
| Entraînement | `ylacombe/finetune-hf-vits` |
| GPU | Kaggle (~30h/sem) ou Colab T4 |
| Hébergement modèle | HuggingFace Hub (privé) |

## 8. Risques & garde-fous

- **Audio bruité = voix dégradée** : la propreté prime sur la quantité (30 min propres > 2h bruitées).
- **Transcriptions fausses** : VITS apprend les erreurs → relire chaque ligne.
- **Sur-apprentissage** < 1h : surveiller l'éval, ne pas empiler les epochs.
- **Licence** : Common Voice CC0 = OK commercial ; mais **ta** voix de marque donne le résultat le plus cohérent.

## 9. Chemin le plus court vers un premier résultat audible

1. `make_prompts.py` → ~150-200 phrases.
2. Enregistrer ~20-30 min (un locuteur natif).
3. `prepare_dataset.py --transcripts prompts.txt` → dataset.
4. `finetune_kaggle.ipynb` sur Kaggle → push Hub.
5. `MMS_RW_MODEL=...` sur le service `tts` → redémarrer.

## Sources
- [ylacombe/finetune-hf-vits](https://github.com/ylacombe/finetune-hf-vits)
- [Common Voice Kinyarwanda 25.0 — Mozilla Data Collective](https://mozilladatacollective.com/datasets/cmn60xnfi00wnnv07028xltoz) · [datasets](https://commonvoice.mozilla.org/datasets)
- [Licences TTS locales 2026 (Piper/XTTS/F5/Coqui)](https://www.promptquorum.com/power-local-llm/local-tts-voice-cloning-piper-coqui-xtts)
- [HF transformers — MMS tokenizer / uroman](https://github.com/huggingface/transformers/issues/32387)
