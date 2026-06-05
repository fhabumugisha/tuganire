# STT Kinyarwanda Limitation Note

## Problem

OpenAI **whisper-1** does not natively support Kinyarwanda (`rw`).  
Whisper covers approximately 99 languages; Kinyarwanda is not among them.

`WhisperSttProvider.supportsLanguage("rw")` deliberately returns `false`.  
`SttProviderFactory.forLanguage("rw")` throws a `BusinessException("stt.provider.no-rw-provider")`  
rather than silently delegating to Whisper and producing garbage output.

This is intentional per the Sprint 1 architecture: the `SttProvider` interface exists so a real
Kinyarwanda provider can be plugged in during Sprint 2 without touching any calling code.

## Status

PRD §4 flags Kinyarwanda STT as **🟡 à confirmer Sprint 2**.  
French STT for Amahoro is handled by the browser Web Speech API (Task 20) — only Mukamana's  
Kinyarwanda audio needs a backend STT provider.

## Viable Alternatives for Sprint 2

### Option A — mbazaNLP/Whisper-Small-Kinyarwanda (recommended first)

A community fine-tune of Whisper Small specifically trained on Kinyarwanda speech.

- Model: `mbazaNLP/Whisper-Small-Kinyarwanda` on Hugging Face
- Inference: run via `transformers` pipeline locally or on a GPU instance
- Integration path: wrap in a new `KinyarwandaSttProvider implements SttProvider` that calls
  the Python MMS server (already present for TTS) with an additional `/stt` endpoint, or spin
  up a dedicated FastAPI endpoint serving the model via `pipeline("automatic-speech-recognition")`
- `name()` = `"whisper-kiny"`, `supportsLanguage("rw")` = `true`
- Quality: significantly better than stock Whisper for Kinyarwanda; still improving

### Option B — Meta MMS ASR (facebook/mms-1b-all)

Meta's Massively Multilingual Speech model covers 1,100+ languages including Kinyarwanda
(`target_lang = kin`).

- Model: `facebook/mms-1b-all`
- Inference: `transformers` `pipeline("automatic-speech-recognition", model="facebook/mms-1b-all")`
  with `model.config.forced_decoder_ids = processor.get_decoder_prompt_ids(language="kin", task="transcribe")`
- Integration path: add a `/stt` route to the existing Python MMS TTS server (`python-mms-tts-server/`)
  and implement `MmsSttProvider implements SttProvider`
- `name()` = `"mms-asr"`, `supportsLanguage("rw")` = `true`
- Quality: broad coverage, lower per-language quality than the Whisper fine-tune; suitable as fallback

## How to Add a New Provider

1. Implement `SttProvider` in package `com.tuganire.stt`.
2. Annotate with `@Component`; `supportsLanguage("rw")` returns `true`.
3. Spring auto-discovers it; `SttProviderFactory` adds it to the registry.
4. Update `tuganire.stt.default-provider` in `application.yml` if it should be the default.
5. No changes required to `SttService`, `SttServiceImpl`, or callers.
