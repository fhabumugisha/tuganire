# Guide d'enregistrement — voix native rwandaise pour Tuganire

> À remettre à la personne qui enregistre (le **locuteur**). Pas besoin d'être technicien :
> suis les étapes dans l'ordre. Objectif : ~30 min à 1 h de voix **claire et régulière**,
> qui servira à créer la voix kinyarwanda de l'application.

---

## 1. Ce qu'il faut (matériel)

| Indispensable | Détail |
|---|---|
| 🎙️ Un micro correct | Casque-micro USB, ou micro de smartphone récent tenu à ~15 cm. Pas le micro d'un vieux PC. |
| 🤫 Une pièce silencieuse | Chambre avec rideaux/tapis = bien (absorbe l'écho). Évite cuisine carrelée, salle vide qui résonne. |
| 📵 Téléphone en mode avion | Pour qu'aucune notification ne sonne pendant une prise. |
| 💻 Audacity (gratuit) | https://www.audacityteam.org — pour enregistrer. (Ou l'app Dictaphone du téléphone.) |
| 📄 `prompts.txt` | La liste numérotée des phrases à lire (fournie séparément). |

**Une seule et même personne** enregistre tout. On ne mélange pas deux voix.

---

## 2. Préparer la pièce et le son

1. Ferme portes et fenêtres. Coupe ventilateur, clim, frigo proche, musique, télé.
2. Place le micro à **~15 cm de la bouche**, légèrement sur le côté (pas pile devant) pour éviter les « pop » sur les **p / b / t**.
3. Dans Audacity : règle l'entrée pour que ta voix normale fasse osciller la barre autour de **-12 dB** (zone jaune), **jamais dans le rouge** (saturation = prise perdue).
4. Fais **un test de 10 secondes**, réécoute : pas d'écho, pas de souffle fort, pas de saturation. Tant que ce n'est pas propre, on ne commence pas.

---

## 3. Comment lire (le plus important pour le naturel)

- 🗣️ **Voix normale et posée**, comme si tu racontais quelque chose à un ami. Ni théâtral, ni monocorde.
- 🐢 **Débit régulier**, ni trop vite ni trop lent. Garde le **même rythme et la même énergie** du début à la fin de la session (c'est ça qui rend la voix cohérente).
- 🎯 **Articule** clairement, sans exagérer.
- ⏸️ Avant CHAQUE phrase : **respire, attends 1 seconde de silence, puis parle**. Et laisse **1 seconde de silence** après la phrase avant de couper. Ces silences sont précieux.
- ❌ Tu t'es trompé(e) ou tu as toussé ? **Ne corrige pas en cours** : arrête, et **recommence la phrase entière** dans une nouvelle prise.
- 🚱 Bois de l'eau, évite la fatigue : fais une **pause toutes les 20-30 min**. Une voix fatiguée s'entend.

---

## 4. Nommer les fichiers (règle d'or)

**Une phrase = un fichier**, et le **numéro du fichier = le numéro du prompt** dans `prompts.txt`.

```
prompts.txt :
  0001   Muraho neza, murakaza neza.
  0002   Iki ni ijwi nyarwanda.
  ...

→ enregistre la phrase 0001 dans   0001.wav
→ enregistre la phrase 0002 dans   0002.wav
```

Range tous les `.wav` dans **un seul dossier** appelé `raw/`.

> Pourquoi : comme le fichier `0007.wav` correspond exactement à la phrase `0007` du texte,
> l'ordinateur sait déjà ce qui est dit. Pas besoin de retranscrire à la main. **Respecte les numéros.**

**Format à l'export Audacity :** `Fichier → Exporter → Exporter en WAV`, en **WAV mono, 16 bits**.
(Si tu enregistres au téléphone en `.m4a`/`.mp3`, ce n'est pas grave : le script convertira. Garde juste les bons numéros de fichier.)

---

## 5. Combien en faire

| Quantité | Résultat attendu |
|---|---|
| ~150 phrases (~20-30 min de voix) | Premier résultat correct, déjà bien mieux que la voix actuelle |
| ~300 phrases (~1 h) | Voix confortable et naturelle ✅ recommandé |
| 500+ phrases (2-3 h) | Excellent — à viser si possible, étalé sur plusieurs sessions |

> Plusieurs sessions sont OK. Mais dans **une même session**, garde la même voix, le même micro,
> la même distance et le même niveau. Si tu changes de jour, refais le test du point 2.

---

## 6. Avant de transmettre — petit contrôle

- [ ] Tous les fichiers sont dans `raw/`, nommés `0001.wav`, `0002.wav`… (sans trous fantaisistes).
- [ ] Réécoute 5 fichiers au hasard : voix claire, pas de saturation, pas de bruit de fond gênant, ~1 s de silence avant/après.
- [ ] Les phrases vides/ratées ont été **refaites**, pas laissées à moitié.
- [ ] Tu peux supprimer les prises ratées : seules les bonnes comptent.

Transmets le dossier `raw/` **et** le `prompts.txt` utilisé.

---

## 7. Et après (côté technique — pas pour le locuteur)

Une fois `raw/` reçu :
```bash
python prepare_dataset.py --in raw/ --out dataset/ --transcripts prompts.txt
```
→ nettoie, normalise et construit `dataset/metadata.csv`. On relit, puis on lance le fine-tuning
(voir [`README.md`](README.md) et [`../docs/TTS-FINETUNE-PLAN.md`](../docs/TTS-FINETUNE-PLAN.md)).
