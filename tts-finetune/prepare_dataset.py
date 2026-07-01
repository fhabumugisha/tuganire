#!/usr/bin/env python3
"""
prepare_dataset.py — Build a clean single-speaker Kinyarwanda TTS dataset for
fine-tuning facebook/mms-tts-kin with ylacombe/finetune-hf-vits.

It takes raw recordings of ONE native Rwandan speaker and produces the exact
layout finetune-hf-vits expects:

    out/
      wavs/0001.wav 0002.wav ...   (mono, 16 kHz, trimmed, normalised)
      metadata.csv                 (file_name|transcription, '|'-separated)

Pipeline per input file:
  1. ffmpeg  -> mono, 16 kHz, loudness-normalised, leading/trailing silence trimmed
  2. (optional) silence-split long files into one-sentence clips
  3. Whisper (Kinyarwanda fine-tune) -> transcription you THEN proofread by hand

Run modes
---------
  # Already have one wav per sentence + a transcripts file? Just normalise + package:
  python prepare_dataset.py --in raw/ --out dataset/ --transcripts transcripts.tsv

  # Long single recording to auto-split + auto-transcribe (then PROOFREAD metadata.csv):
  python prepare_dataset.py --in long_session.wav --out dataset/ --split --transcribe

Quality rules (the model only sounds as good as this dataset):
  • ONE speaker, quiet room, no music/reverb, consistent mic & distance.
  • Aim 30 min minimum, 1 h comfortable, 2-3 h excellent.
  • Clean WAV beats lots of noisy WAV. Reject clipped/echoey clips.
  • metadata.csv transcriptions MUST match the audio exactly (proofread!).

Dependencies (CPU is fine for prep):
  pip install -r requirements.txt        # transformers, torch, soundfile, tqdm
  ffmpeg must be on PATH (brew install ffmpeg).
"""

from __future__ import annotations

import argparse
import csv
import subprocess
import sys
from pathlib import Path

TARGET_SR = 16_000
WHISPER_RW_MODEL = "mbazaNLP/Whisper-Small-Kinyarwanda"  # reused from your STT note


# ──────────────────────────────────────────────────────────────────────────────
# audio helpers (ffmpeg)
# ──────────────────────────────────────────────────────────────────────────────
def _run(cmd: list[str]) -> None:
    proc = subprocess.run(cmd, capture_output=True, text=True)
    if proc.returncode != 0:
        sys.exit(f"command failed: {' '.join(cmd)}\n{proc.stderr}")


def normalise(src: Path, dst: Path) -> None:
    """Mono, 16 kHz, EBU R128 loudness-normalised, silence trimmed at both ends."""
    dst.parent.mkdir(parents=True, exist_ok=True)
    _run([
        "ffmpeg", "-y", "-i", str(src),
        "-ac", "1", "-ar", str(TARGET_SR),
        "-af",
        "silenceremove=start_periods=1:start_silence=0.1:start_threshold=-40dB:"
        "detection=peak,areverse,"
        "silenceremove=start_periods=1:start_silence=0.1:start_threshold=-40dB:"
        "detection=peak,areverse,"
        "loudnorm=I=-23:LRA=7:tp=-2",
        "-c:a", "pcm_s16le", str(dst),
    ])


def split_on_silence(src: Path, out_dir: Path) -> list[Path]:
    """Cut a long recording into per-utterance clips on >=0.6s silence."""
    out_dir.mkdir(parents=True, exist_ok=True)
    pattern = str(out_dir / "seg_%04d.wav")
    # ffmpeg's silencedetect+segment is finicky; use the simple aresample+silence approach.
    _run([
        "ffmpeg", "-y", "-i", str(src),
        "-ac", "1", "-ar", str(TARGET_SR),
        "-af", "silencedetect=noise=-35dB:d=0.6",
        "-f", "segment", "-segment_time", "30",  # hard cap so nothing runs away
        "-c:a", "pcm_s16le", pattern,
    ])
    return sorted(out_dir.glob("seg_*.wav"))


# ──────────────────────────────────────────────────────────────────────────────
# transcription (optional, ALWAYS proofread afterwards)
# ──────────────────────────────────────────────────────────────────────────────
def transcribe_all(wavs: list[Path]) -> dict[str, str]:
    from transformers import pipeline  # local import: heavy
    import torch

    device = 0 if torch.cuda.is_available() else -1
    asr = pipeline(
        "automatic-speech-recognition",
        model=WHISPER_RW_MODEL,
        device=device,
    )
    out: dict[str, str] = {}
    for w in wavs:
        text = asr(str(w))["text"].strip()
        out[w.name] = text
        print(f"  {w.name}: {text}")
    return out


# ──────────────────────────────────────────────────────────────────────────────
# transcripts file loader (file<TAB>text  OR  file|text)
# ──────────────────────────────────────────────────────────────────────────────
def load_transcripts(path: Path) -> dict[str, str]:
    out: dict[str, str] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line:
            continue
        sep = "\t" if "\t" in line else "|"
        name, _, text = line.partition(sep)
        out[Path(name.strip()).name] = text.strip()
    return out


# ──────────────────────────────────────────────────────────────────────────────
def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--in", dest="inp", required=True, help="raw dir of wavs OR a single long wav")
    ap.add_argument("--out", dest="out", required=True, help="output dataset dir")
    ap.add_argument("--transcripts", help="file mapping clip -> text (TSV or '|'); skips Whisper")
    ap.add_argument("--split", action="store_true", help="split a long input recording on silence")
    ap.add_argument("--transcribe", action="store_true", help="auto-transcribe with Whisper-rw (proofread after!)")
    args = ap.parse_args()

    inp, out = Path(args.inp), Path(args.out)
    wav_dir = out / "wavs"
    wav_dir.mkdir(parents=True, exist_ok=True)

    # 1. collect / split raw clips
    if args.split:
        if not inp.is_file():
            sys.exit("--split expects a single input file")
        print(f"splitting {inp} on silence ...")
        raw_clips = split_on_silence(inp, out / "_segments")
    elif inp.is_dir():
        raw_clips = sorted([*inp.glob("*.wav"), *inp.glob("*.mp3"), *inp.glob("*.m4a")])
    else:
        raw_clips = [inp]
    if not raw_clips:
        sys.exit(f"no audio found in {inp}")
    print(f"{len(raw_clips)} raw clip(s)")

    # 2. normalise -> wavs/NNNN.wav
    final: list[Path] = []
    for i, clip in enumerate(raw_clips, start=1):
        dst = wav_dir / f"{i:04d}.wav"
        normalise(clip, dst)
        final.append(dst)
    print(f"normalised -> {wav_dir}")

    # 3. transcriptions
    if args.transcripts:
        provided = load_transcripts(Path(args.transcripts))
        # map provided (keyed by original name) onto the new NNNN order
        texts = {
            final[i].name: provided.get(raw_clips[i].name, "")
            for i in range(len(final))
        }
        missing = [k for k, v in texts.items() if not v]
        if missing:
            print(f"WARNING: {len(missing)} clip(s) have no transcript: {missing[:5]}...")
    elif args.transcribe:
        print("transcribing with Whisper-rw (PROOFREAD metadata.csv afterwards) ...")
        texts = transcribe_all(final)
    else:
        texts = {w.name: "" for w in final}
        print("no transcripts: metadata.csv created with EMPTY text — fill it in by hand.")

    # 4. metadata.csv  (file_name|transcription  — finetune-hf-vits format)
    meta = out / "metadata.csv"
    with meta.open("w", encoding="utf-8", newline="") as f:
        writer = csv.writer(f, delimiter="|")
        writer.writerow(["file_name", "transcription"])
        for w in final:
            writer.writerow([f"wavs/{w.name}", texts.get(w.name, "")])

    total_min = sum(_dur(w) for w in final) / 60.0
    print(f"\n✅ dataset ready: {out}")
    print(f"   clips: {len(final)}   audio: ~{total_min:.1f} min")
    print(f"   metadata: {meta}")
    print("\nNEXT:")
    print("  1. PROOFREAD metadata.csv — every transcription must match the audio exactly.")
    print("  2. Push to a (private) HF dataset, or zip and upload to Kaggle/Colab.")
    print("  3. Run the fine-tune notebook (see tts-finetune/README.md).")


def _dur(wav: Path) -> float:
    out = subprocess.run(
        ["ffprobe", "-v", "error", "-show_entries", "format=duration",
         "-of", "default=noprint_wrappers=1:nokey=1", str(wav)],
        capture_output=True, text=True,
    )
    try:
        return float(out.stdout.strip())
    except ValueError:
        return 0.0


if __name__ == "__main__":
    main()
