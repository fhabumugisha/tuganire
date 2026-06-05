# Build stage: Python 3.11 slim base
FROM python:3.11-slim

WORKDIR /app

# ffmpeg is required to decode the browser's WebM/Opus uploads into 16 kHz mono PCM for ASR.
RUN apt-get update && apt-get install -y --no-install-recommends ffmpeg \
    && rm -rf /var/lib/apt/lists/*

# Install dependencies first for layer caching
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Bake the MMS models into the image BEFORE copying the app code, so editing tts_server.py
# does not invalidate (and re-download) these multi-GB layers. Cached under
# /root/.cache/huggingface, which is also where tts_server.py loads them from at runtime.
#  - TTS: mms-tts-kin / mms-tts-fra (small VITS models)
#  - ASR: mms-1b-all base + the Kinyarwanda (kin) adapter (large — ~3-4 GB)
RUN python -c "from transformers import VitsModel, AutoTokenizer; \
[ (VitsModel.from_pretrained(m), AutoTokenizer.from_pretrained(m)) \
for m in ('facebook/mms-tts-kin', 'facebook/mms-tts-fra') ]"
RUN python -c "from transformers import Wav2Vec2ForCTC, AutoProcessor; \
p = AutoProcessor.from_pretrained('facebook/mms-1b-all'); \
m = Wav2Vec2ForCTC.from_pretrained('facebook/mms-1b-all'); \
p.tokenizer.set_target_lang('kin'); m.load_adapter('kin')"

# Copy application code last (cheap layer — changes here don't bust the model cache above).
COPY tts_server.py .

EXPOSE 8000

# CPU-only by default; for GPU pass: docker run --gpus all -p 8000:8000 tuganire/tts:1.0
CMD ["uvicorn", "tts_server:app", "--host", "0.0.0.0", "--port", "8000"]
