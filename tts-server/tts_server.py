import os

# ── CPU thread pinning (MUST run before torch/transformers import) ───────────
# On a shared/virtualised CPU (Railway, k8s…) PyTorch otherwise spawns one thread per HOST core
# (often 32) while the container is only granted a fraction of a vCPU. The oversubscribed threads
# blow through the cgroup CFS quota each scheduling period and get throttled (frozen) for the rest
# of it — inflating a ~2s Kinyarwanda transcription to ~80s. Pinning every BLAS/OpenMP/torch pool
# to the real allocation removes the throttle-storm. Tune via TORCH_NUM_THREADS to match the plan's
# vCPU (e.g. 2 on a small instance); the default is conservative.
_THREADS = os.environ.get("TORCH_NUM_THREADS", "4")
for _var in ("OMP_NUM_THREADS", "MKL_NUM_THREADS", "OPENBLAS_NUM_THREADS", "NUMEXPR_NUM_THREADS"):
    os.environ.setdefault(_var, _THREADS)

from fastapi import FastAPI, HTTPException, Response, Request
from pydantic import BaseModel
from transformers import VitsModel, AutoTokenizer, Wav2Vec2ForCTC, AutoProcessor
import torch
import numpy as np
import scipy.io.wavfile
import io
import re
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
# Pin the intra-op thread pool too (env vars above cover OpenMP/BLAS; this covers torch's own pool).
torch.set_num_threads(int(_THREADS))
logger.info(f"Using device: {device} (torch threads: {torch.get_num_threads()})")

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
    pauses: bool = False


# Seconds of silence inserted AFTER each punctuation mark when `pauses=True`. Sentence-final marks
# get a longer breath than intra-sentence ones, which is what the native MMS voice lacks (it runs
# words together with no articulation). Tuned by ear on Kinyarwanda phrases.
_PAUSE_AFTER = {".": 0.35, "!": 0.35, "?": 0.35, ";": 0.25, ":": 0.25, ",": 0.18}


def _synthesize_waveform(bundle: dict, text: str) -> np.ndarray:
    """Run the VITS model on one text chunk and return its float32 mono waveform."""
    inputs = bundle["tokenizer"](text, return_tensors="pt").to(device)
    with torch.no_grad():
        output = bundle["model"](**inputs).waveform
    return output.squeeze().cpu().numpy()


def _encode_mp3(audio: np.ndarray, rate: int) -> bytes:
    """Encode a float32 waveform to MP3 via ffmpeg.

    MMS produces 32-bit IEEE-float WAV, which mobile browsers (iOS Safari, Android Chrome)
    refuse to decode — so playback silently fails when the bytes are served as audio/mpeg.
    Transcoding to real MP3 matches the audio/mpeg content-type and plays everywhere.
    """
    buf = io.BytesIO()
    scipy.io.wavfile.write(buf, rate=rate, data=audio)
    proc = subprocess.run(
        ["ffmpeg", "-hide_banner", "-loglevel", "error",
         "-f", "wav", "-i", "pipe:0",
         "-codec:a", "libmp3lame", "-q:a", "4", "-f", "mp3", "pipe:1"],
        input=buf.getvalue(), stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=True,
    )
    return proc.stdout


def _synthesize_with_pauses(bundle: dict, text: str, rate: int) -> np.ndarray:
    """Synthesise `text` segment by segment, concatenating with silence at punctuation.

    Splitting on punctuation lets the model reset its prosody per clause (clearer articulation)
    and lets us insert an explicit breath the native model never produces on its own.
    """
    # Split into tokens, keeping the punctuation marks as their own entries.
    tokens = re.split(r"([.!?;:,])", text)
    pieces: list[np.ndarray] = []
    i = 0
    while i < len(tokens):
        segment = tokens[i].strip()
        punct = tokens[i + 1] if i + 1 < len(tokens) else ""
        i += 2
        if segment:
            pieces.append(_synthesize_waveform(bundle, segment + punct))
        pause = _PAUSE_AFTER.get(punct, 0.0)
        if pause > 0.0:
            pieces.append(np.zeros(int(rate * pause), dtype=np.float32))

    if not pieces:
        return _synthesize_waveform(bundle, text)
    return np.concatenate(pieces).astype(np.float32)


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

    # sample rate is read from model config, not hardcoded
    rate: int = bundle["model"].config.sampling_rate

    if req.pauses:
        audio = _synthesize_with_pauses(bundle, req.text, rate)
    else:
        audio = _synthesize_waveform(bundle, req.text)

    mp3_bytes = _encode_mp3(audio, rate)

    duration_ms = int((time.time() - start) * 1000)
    logger.info(f"Generated {req.lang} TTS ({len(mp3_bytes)} bytes MP3) in {duration_ms}ms")

    return Response(
        content=mp3_bytes,
        media_type="audio/mpeg",
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
