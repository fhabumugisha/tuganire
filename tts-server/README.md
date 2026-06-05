# Tuganire MMS-TTS Server

FastAPI server that synthesizes speech for Kinyarwanda (`rw`) and French (`fr`) using Meta MMS-TTS models from HuggingFace.

## Models used

| Language | Model |
|----------|-------|
| Kinyarwanda (`rw`) | `facebook/mms-tts-kin` |
| French (`fr`) | `facebook/mms-tts-fra` |

First startup downloads model weights (several hundred MB each) — allow 2–5 minutes.

## Local setup (Mac, recommended with uv)

```bash
# Install uv (fast Python package manager)
brew install uv

# Create virtual environment and activate
cd tts-server/
uv venv
source .venv/bin/activate

# Install pinned dependencies
uv pip install -r requirements.txt

# Start server (downloads models on first run)
uvicorn tts_server:app --host 0.0.0.0 --port 8000
```

## Local setup (pip)

```bash
cd tts-server/
python3.11 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
uvicorn tts_server:app --host 0.0.0.0 --port 8000
```

## Docker

```bash
# Build
docker build -t tuganire/tts:1.0 .

# Run (CPU)
docker run -p 8000:8000 tuganire/tts:1.0

# Run (GPU — Hetzner GEX44 with NVIDIA RTX)
docker run --gpus all -p 8000:8000 tuganire/tts:1.0
```

## API contract

### GET /health

Returns server status, active device, and supported languages.

**Response 200:**
```json
{
  "status": "ok",
  "device": "mps",
  "languages": ["rw", "fr"]
}
```

### POST /tts

Synthesize speech and return a WAV audio file.

**Request body:**
```json
{
  "text": "muraho",
  "lang": "rw"
}
```

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `text` | string | required | Text to synthesize |
| `lang` | string | `"rw"` | Language code — `"rw"` or `"fr"` |

**Response 200:**
- `Content-Type: audio/wav`
- `X-Generation-Time-Ms: <ms>` — server-side synthesis time
- Body: binary WAV audio

**Response 400** (unsupported language):
```json
{
  "detail": "Langue non supportée : sw"
}
```

## Device selection

The server auto-selects the best available device at startup:

1. `mps` — Apple Silicon (Mac M-series)
2. `cuda` — NVIDIA GPU
3. `cpu` — fallback, works on any machine (slower)

The active device is reported in `GET /health` and logged at startup.

## Example with curl

```bash
# Health check
curl http://localhost:8000/health

# Synthesize Kinyarwanda (save WAV to file)
curl -X POST http://localhost:8000/tts \
  -H "Content-Type: application/json" \
  -d '{"text":"muraho","lang":"rw"}' \
  --output muraho.wav -v

# Synthesize French
curl -X POST http://localhost:8000/tts \
  -H "Content-Type: application/json" \
  -d '{"text":"bonjour","lang":"fr"}' \
  --output bonjour.wav
```
