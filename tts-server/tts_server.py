from fastapi import FastAPI, HTTPException, Response, Request
from pydantic import BaseModel
from transformers import VitsModel, AutoTokenizer, Wav2Vec2ForCTC, AutoProcessor
import torch
import numpy as np
import scipy.io.wavfile
import io
import time
import subprocess
import logging

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

app = FastAPI(title="Tuganire MMS-TTS + ASR")


def get_device() -> torch.device:
    if torch.backends.mps.is_available():
        return torch.device("mps")   # Mac M-series
    if torch.cuda.is_available():
        return torch.device("cuda")  # GPU NVIDIA
    return torch.device("cpu")


device = get_device()
logger.info(f"Using device: {device}")

# ── TTS models (speech synthesis) ───────────────────────────────────────────
MODELS: dict = {
    "rw": {
        "model": VitsModel.from_pretrained("facebook/mms-tts-kin").to(device),
        "tokenizer": AutoTokenizer.from_pretrained("facebook/mms-tts-kin"),
    },
    "fr": {
        "model": VitsModel.from_pretrained("facebook/mms-tts-fra").to(device),
        "tokenizer": AutoTokenizer.from_pretrained("facebook/mms-tts-fra"),
    },
}

# ── ASR model (speech recognition) ──────────────────────────────────────────
# Meta MMS covers 1000+ languages including Kinyarwanda ("kin") — far better than
# the browser Web Speech API, which loses negations and mangles Kinyarwanda.
ASR_MODEL_ID = "facebook/mms-1b-all"
ASR_SAMPLE_RATE = 16000

# Map app language codes → MMS ISO-639-3 adapter codes.
ASR_ADAPTERS: dict = {"rw": "kin", "fr": "fra"}

asr_processor = AutoProcessor.from_pretrained(ASR_MODEL_ID)
asr_model = Wav2Vec2ForCTC.from_pretrained(ASR_MODEL_ID).to(device)
# Pre-load the Kinyarwanda adapter (the primary use-case).
asr_processor.tokenizer.set_target_lang("kin")
asr_model.load_adapter("kin")
_current_adapter = "kin"
logger.info("ASR model loaded with Kinyarwanda (kin) adapter")


# Trim leading AND trailing silence (without touching pauses inside the speech) so the CTC
# model doesn't hallucinate text from the quiet head/tail of the clip — the main cause of the
# garbage that appears after a real-voice sentence. Trick: trim leading silence, reverse, trim
# leading (= original trailing) silence, reverse back. -45 dB only catches true silence/low noise.
_SILENCE_TRIM = (
    "silenceremove=start_periods=1:start_silence=0.1:start_threshold=-45dB:detection=peak,"
    "areverse,"
    "silenceremove=start_periods=1:start_silence=0.1:start_threshold=-45dB:detection=peak,"
    "areverse"
)


def _decode_to_waveform(raw: bytes) -> np.ndarray:
    """Decode arbitrary audio bytes (WebM/Opus, MP4, WAV…) to 16 kHz mono float32 via ffmpeg, trimming edge silence."""
    try:
        proc = subprocess.run(
            ["ffmpeg", "-hide_banner", "-loglevel", "error",
             "-i", "pipe:0", "-af", _SILENCE_TRIM,
             "-f", "f32le", "-ac", "1", "-ar", str(ASR_SAMPLE_RATE), "pipe:1"],
            input=raw, stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=True,
        )
    except subprocess.CalledProcessError as exc:
        logger.error(f"ffmpeg decode failed: {exc.stderr.decode('utf-8', 'ignore')[:500]}")
        raise HTTPException(status_code=400, detail="Audio decode failed")
    return np.frombuffer(proc.stdout, dtype=np.float32)


def _select_adapter(lang: str) -> None:
    """Switch the ASR adapter to the requested language if not already active."""
    global _current_adapter
    adapter = ASR_ADAPTERS[lang]
    if adapter != _current_adapter:
        asr_processor.tokenizer.set_target_lang(adapter)
        asr_model.load_adapter(adapter)
        _current_adapter = adapter


class TtsRequest(BaseModel):
    text: str
    lang: str = "rw"


class SttResponse(BaseModel):
    text: str


@app.get("/health")
def health() -> dict:
    return {
        "status": "ok",
        "device": str(device),
        "tts_languages": list(MODELS.keys()),
        "asr_languages": list(ASR_ADAPTERS.keys()),
    }


@app.post("/tts")
def synthesize(req: TtsRequest) -> Response:
    if req.lang not in MODELS:
        raise HTTPException(status_code=400, detail=f"Langue non supportée : {req.lang}")

    start = time.time()
    bundle = MODELS[req.lang]

    inputs = bundle["tokenizer"](req.text, return_tensors="pt").to(device)

    with torch.no_grad():
        output = bundle["model"](**inputs).waveform

    audio = output.squeeze().cpu().numpy()
    # sample rate is read from model config, not hardcoded
    rate: int = bundle["model"].config.sampling_rate

    buf = io.BytesIO()
    scipy.io.wavfile.write(buf, rate=rate, data=audio)

    duration_ms = int((time.time() - start) * 1000)
    logger.info(f"Generated {req.lang} TTS in {duration_ms}ms")

    return Response(
        content=buf.getvalue(),
        media_type="audio/wav",
        headers={"X-Generation-Time-Ms": str(duration_ms)},
    )


@app.post("/stt", response_model=SttResponse)
async def transcribe(request: Request, lang: str = "rw") -> SttResponse:
    """Transcribe raw audio bytes (request body) to text using MMS-ASR.

    The Spring backend POSTs the recorded audio bytes with a `lang` query param.
    """
    if lang not in ASR_ADAPTERS:
        raise HTTPException(status_code=400, detail=f"Langue non supportée : {lang}")

    raw = await request.body()
    if not raw:
        raise HTTPException(status_code=400, detail="Empty audio")

    start = time.time()
    waveform = _decode_to_waveform(raw)
    _select_adapter(lang)

    inputs = asr_processor(waveform, sampling_rate=ASR_SAMPLE_RATE, return_tensors="pt").to(device)
    with torch.no_grad():
        logits = asr_model(**inputs).logits
    ids = torch.argmax(logits, dim=-1)[0]
    text = asr_processor.decode(ids).strip()

    duration_ms = int((time.time() - start) * 1000)
    logger.info(f"Transcribed {lang} STT in {duration_ms}ms → {len(text)} chars")

    return SttResponse(text=text)
