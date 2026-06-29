# tts-finetune — native Kinyarwanda voice for Tuganire

Fine-tune `facebook/mms-tts-kin` into a **natural single-speaker Kinyarwanda voice**,
replacing the robotic stock MMS output. **100% free tools.** See the full rationale in
[`docs/TTS-FINETUNE-PLAN.md`](../docs/TTS-FINETUNE-PLAN.md).

## Why this approach
- MMS-TTS-kin already knows Kinyarwanda phonetics → fine-tuning fixes *delivery*, not the language.
- VITS is tiny (83M params) → trains on a **free** Kaggle/Colab T4 in **< 1h**.
- Kinyarwanda is Latin-script → **no uroman / espeak phonemizer** needed (character tokenizer).
- Good results from **~20-30 min** of clean audio; 1-3h = excellent.

## Files
| File | Purpose |
|------|---------|
| `GUIDE-ENREGISTREMENT.md` | **Hand to the native speaker** — non-technical recording checklist (matériel, consignes, nommage) |
| `make_prompts.py` | Generate varied Kinyarwanda sentences to read (Common Voice CC0 + your domain words) |
| `prepare_dataset.py` | Normalise/segment/transcribe recordings → `wavs/` + `metadata.csv` |
| `finetune_config.json` | Config for `ylacombe/finetune-hf-vits` |
| `finetune_kaggle.ipynb` | Kaggle/Colab notebook: clone tool → train → A/B listen → push to Hub |
| `requirements.txt` | Local prep deps (training deps come from finetune-hf-vits) |

## Workflow

### 1. Local — generate prompts & record (one native speaker)
```bash
brew install ffmpeg uv
cd tts-finetune
uv venv && source .venv/bin/activate
uv pip install -r requirements.txt

# 200 reading prompts (Common Voice rw + optional your-domain sentences)
python make_prompts.py --n 200 --extra my_domain_sentences.txt --out prompts.txt
```
Record each numbered line as its own clip `0001.wav, 0002.wav, …` in a quiet room.
**Tip:** because the prompt text *is* the transcription, you skip Whisper and get perfect alignment.

### 2. Local — build the dataset
```bash
# clips already match prompts.txt (text = transcription):
python prepare_dataset.py --in raw/ --out dataset/ --transcripts prompts.txt

# OR one long recording to auto-split + auto-transcribe (then PROOFREAD metadata.csv):
python prepare_dataset.py --in session.wav --out dataset/ --split --transcribe
```
Produces `dataset/wavs/*.wav` + `dataset/metadata.csv` (`file_name|transcription`).
**Proofread `metadata.csv`** — transcriptions must match audio exactly.

Push it to a private HF dataset (or upload the folder to Kaggle):
```bash
huggingface-cli upload-large-folder YOUR_HF_USERNAME/kinyarwanda-tts-single-speaker dataset --repo-type=dataset
```

### 3. Cloud — fine-tune (free GPU)
Open `finetune_kaggle.ipynb` on **Kaggle** (GPU T4, ~30h/week free) or **Colab**.
Edit `finetune_config.json`: set `hub_model_id`, `dataset_name`, your HF write token.
Run all cells → trains → A/B listens → pushes `YOUR_HF_USERNAME/mms-tts-kin-native` to the Hub.

### 4. Deploy — zero code change
The server already reads the model id from an env var:
```bash
MMS_RW_MODEL=YOUR_HF_USERNAME/mms-tts-kin-native
```
Set it on the `tts` service and restart. `tts_server.py` loads your voice instead of stock MMS.
The `/tts` API, Docker image, and Spring integration are unchanged.

## Iterate
Not natural enough? Add more clean audio (or epochs), retrain, re-listen. Keep a fixed
hold-out test sentence set to compare versions by ear.
