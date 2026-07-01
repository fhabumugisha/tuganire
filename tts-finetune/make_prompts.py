#!/usr/bin/env python3
"""
make_prompts.py — Produce a phonetically-varied list of Kinyarwanda sentences for
your native speaker to READ ALOUD while recording the TTS dataset.

Two sources (both free):
  • Mozilla Common Voice Kinyarwanda sentences (CC0) via HuggingFace datasets.
  • Your own domain sentences (pastoral / Amahoro / Mukamana vocabulary) — append
    them to a text file and pass --extra so the voice learns YOUR words and numbers.

It selects sentences that are:
  • a sensible length (not too short, not a paragraph),
  • de-duplicated,
  • shuffled deterministically (fixed seed) for a balanced reading session.

Usage:
  pip install datasets
  python make_prompts.py --n 200 --out prompts.txt
  python make_prompts.py --n 200 --extra my_domain_sentences.txt --out prompts.txt

Output: prompts.txt — one sentence per line, numbered, ready to read.
Record each line as its own clip (filename = line number) so transcripts are free:
the prompt text IS the transcription. That skips Whisper entirely and guarantees
perfect alignment.
"""

from __future__ import annotations

import argparse
import re
from pathlib import Path

# Common Voice corpus on the Hub. We only need the *sentences* (text), not audio,
# so this download is light. Config name is the language code.
CV_DATASET = "mozilla-foundation/common_voice_17_0"
CV_LANG = "rw"

MIN_CHARS, MAX_CHARS = 25, 120  # one comfortable spoken sentence
SEED = 1337


def _clean(s: str) -> str:
    s = re.sub(r"\s+", " ", s).strip()
    return s


def _ok(s: str) -> bool:
    if not (MIN_CHARS <= len(s) <= MAX_CHARS):
        return False
    if any(ch.isdigit() for ch in s):
        # keep some, but most read sets want words; numbers are added via --extra
        return False
    return s.endswith((".", "!", "?")) or s[-1].isalpha()


def from_common_voice(n: int) -> list[str]:
    from datasets import load_dataset  # local import: heavy

    # streaming avoids pulling audio; we read text from the 'sentence' column.
    ds = load_dataset(CV_DATASET, CV_LANG, split="train", streaming=True)
    seen: set[str] = set()
    out: list[str] = []
    for row in ds:
        s = _clean(row.get("sentence", ""))
        if s and s.lower() not in seen and _ok(s):
            seen.add(s.lower())
            out.append(s)
        if len(out) >= n * 3:  # gather a surplus, trim after shuffle
            break
    return out


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--n", type=int, default=200, help="how many sentences to output")
    ap.add_argument("--out", default="prompts.txt")
    ap.add_argument("--extra", help="text file of your own sentences (one per line) to prepend")
    ap.add_argument("--no-cv", action="store_true", help="use only --extra, skip Common Voice")
    args = ap.parse_args()

    import random
    rng = random.Random(SEED)

    pool: list[str] = []
    if args.extra:
        extra = [_clean(l) for l in Path(args.extra).read_text(encoding="utf-8").splitlines()]
        pool.extend([s for s in extra if s])
        print(f"{len(pool)} domain sentence(s) from {args.extra}")

    if not args.no_cv:
        cv = from_common_voice(args.n)
        rng.shuffle(cv)
        pool.extend(cv)
        print(f"{len(cv)} Common Voice sentence(s)")

    # de-dup preserving order, then trim
    seen: set[str] = set()
    final: list[str] = []
    for s in pool:
        k = s.lower()
        if k not in seen:
            seen.add(k)
            final.append(s)
    final = final[: args.n]

    out = Path(args.out)
    with out.open("w", encoding="utf-8") as f:
        for i, s in enumerate(final, start=1):
            f.write(f"{i:04d}\t{s}\n")

    print(f"\n✅ {len(final)} prompts -> {out}")
    print("Record each line as clip <NNNN>.wav. The prompt text is the transcription,")
    print("so you can build metadata.csv directly with --transcripts prompts.txt (no Whisper).")


if __name__ == "__main__":
    main()
