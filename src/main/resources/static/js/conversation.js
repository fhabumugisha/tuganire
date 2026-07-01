/**
 * conversation.js — Alpine.js component for the Tuganire "deux boutons" screen.
 *
 * Responsibilities:
 *  - Detect Web Speech API support (Chrome / Chromium only).
 *  - Obtain an anonymous sessionId from POST /api/v1/sessions.
 *  - Toggle speech recognition on button press (fr-FR or rw-RW).
 *  - On a final SpeechRecognition result, stream the translation via the SSE endpoint
 *    (/api/v1/stream/translate): corrected text → translation → audio, rendered into a
 *    client-built bubble with replay / listen / copy / voice-compare / feedback controls.
 *  - Pause recognition during audio playback to avoid self-triggering.
 *  - Expose scrollToBottom(listId) to keep the active list scrolled.
 *  - Toggle between 'twoButtons' and 'splitScreen' modes (US-04) without page reload.
 *  - In split-screen mode, stream the bubble into the LISTENER's panel (the target-language
 *    half) so the rotated top panel faces the right reader — same pipeline as two-button mode.
 */
// A 44-byte silent WAV. Played once within a user gesture (the mic press) to unlock programmatic
// audio playback on strict browsers (iOS/Safari), so the translation auto-reads when the pipeline ends.
const SILENT_AUDIO = 'data:audio/wav;base64,UklGRiQAAABXQVZFZm10IBAAAAABAAEAQB8AAEAfAAABAAgAZGF0YQAAAAA=';

function conversation() {
    return {
        // ── state ──────────────────────────────────────────────────────────
        recording: false,
        translating: false,
        /**
         * Drives the slim indeterminate progress bar under the header. True from the moment a
         * translation begins (SSE stream open) until the stream completes (`done`) or errors.
         */
        processing: false,
        activeLang: null,        // 'fr' | 'rw' | null
        speechSupported: false,
        sessionId: null,
        bubbleCount: 0,
        errorMessage: null,

        /** Active EventSource for the streaming translation (so we can close it on cleanup). */
        _eventSource: null,

        /**
         * Single reusable <audio> for TTS playback. Unlocked on the first mic press (a user gesture)
         * so the translation can auto-read aloud when the pipeline finishes — including on iOS/Safari,
         * which only allow programmatic play() on an element previously played within a gesture.
         */
        _ttsAudio: null,
        _audioUnlocked: false,

        /** 'twoButtons' (default) | 'splitScreen' — persisted in localStorage. */
        mode: 'twoButtons',

        /**
         * French STT engine: 'webspeech' (on-device Web Speech API, default) or
         * 'openai' (server-side Whisper + LLM correction). Persisted in localStorage.
         * Only affects the French side; Kinyarwanda always uses Web Speech API.
         */
        frenchStt: 'webspeech',

        /** SpeechRecognition instance (created fresh per recording session). */
        _recognition: null,

        /** MediaRecorder + stream + chunks for the OpenAI (server) French STT path. */
        _mediaRecorder: null,
        _mediaStream: null,
        _audioChunks: [],

        /** Finals committed from earlier keep-alive sessions (preserved across restarts). */
        _committedTranscript: '',
        /** Finals rebuilt from the CURRENT session's results on each onresult (idempotent — no dup). */
        _sessionFinal: '',
        /** Latest interim (not-yet-final) transcript text. */
        _interimTranscript: '',
        /** True when the user pressed stop or playback paused us — suppresses keep-alive restart. */
        _manualStop: false,
        /** Live transcript shown while recording (Web Speech path only). */
        liveTranscript: '',

        /**
         * True while a server-side recording (MediaRecorder → upload) is in progress. The Kinyarwanda
         * and French-OpenAI paths give NO live transcript, so the UI shows an elapsed-time + "tap to
         * stop" hint instead — otherwise the user only sees the listening ring with no confirmation.
         */
        serverRecording: false,
        /** Seconds elapsed in the current server recording — shown in the recording hint. */
        recordingSeconds: 0,
        /** setInterval handle ticking recordingSeconds every second. */
        _recordingTimer: null,

        /** Web Audio plumbing for the live mic-level meter that animates the listening ring. */
        _micAudioCtx: null,
        _micAnalyser: null,
        _micRafId: null,
        /** 0..1 live microphone level (RMS), bound by the listening ring scale. */
        micLevel: 0,

        /** True while waiting on a slow (cold-starting) MMS server — drives the "server waking" banner. */
        warmingUp: false,

        // ── lifecycle ──────────────────────────────────────────────────────

        async init() {
            // Detect Web Speech API (Chrome / Chromium; not available in Firefox)
            const SpeechRecognition =
                window.SpeechRecognition || window.webkitSpeechRecognition;
            this.speechSupported = !!SpeechRecognition;

            // Restore persisted mode from localStorage
            const savedMode = localStorage.getItem('tuganire.mode');
            if (savedMode === 'splitScreen' || savedMode === 'twoButtons') {
                this.mode = savedMode;
            }

            // Restore persisted French STT engine choice
            const savedStt = localStorage.getItem('tuganire.frenchStt');
            if (savedStt === 'openai' || savedStt === 'webspeech') {
                this.frenchStt = savedStt;
            }

            // Listen for pause-recognition events dispatched by translation bubbles
            // before auto-playing audio (avoids the mic picking up the speaker output).
            this.$el.addEventListener('pause-recognition', () => {
                this._pauseRecognition();
            });

            // Obtain anonymous session from the backend
            await this._fetchSession();
        },

        // ── session ────────────────────────────────────────────────────────

        async _fetchSession() {
            try {
                const csrfToken  = document.querySelector('meta[name="_csrf"]')?.content || '';
                const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content || 'X-CSRF-TOKEN';

                const resp = await fetch('/api/v1/sessions', {
                    method: 'POST',
                    headers: { [csrfHeader]: csrfToken }
                });

                if (!resp.ok) throw new Error('session-fetch-failed');
                const data = await resp.json();
                this.sessionId = data.sessionId;
            } catch (_) {
                // Fall back to a client-generated ID; translation will still work
                // but server-side caching won't be session-scoped.
                this.sessionId = crypto.randomUUID();
            }
        },

        // ── mode toggle ────────────────────────────────────────────────────

        /**
         * Toggle between 'twoButtons' and 'splitScreen' modes.
         * Stops any active recording before switching, then persists the choice.
         */
        toggleMode() {
            // Tear down whichever capture is active: Web Speech (_cancelRecognition) AND the server
            // MediaRecorder path (_discardOpenAiRecording, which also stops the timer + mic meter).
            // Both are no-ops when their target is inactive, so calling both is safe.
            if (this.recording || this._mediaRecorder) {
                this._cancelRecognition();
                this._discardOpenAiRecording();
            }
            // Tear down any in-flight stream so its bubble doesn't keep mutating the
            // now-hidden layout, and reset the progress bar.
            this._closeStream();
            this.processing = false;
            this.mode = this.mode === 'twoButtons' ? 'splitScreen' : 'twoButtons';
            localStorage.setItem('tuganire.mode', this.mode);
        },

        // ── recognition ────────────────────────────────────────────────────

        /**
         * Toggle recognition for the given language code ('fr' or 'rw'). The translation bubble's
         * destination (the active conversation list, or the listener's panel in split-screen mode)
         * is decided later in {@link _submitTranscript} from the target language.
         *
         * @param {string} lang - 'fr' | 'rw'
         */
        toggleRecognition(lang) {
            // Unlock audio within this user gesture so the translation can auto-read aloud later
            // (the pipeline finishes seconds after, outside any gesture — too late on iOS/Safari).
            this._unlockAudio();

            // Pressing the active button again stops it. For the OpenAI French path this
            // finalises the recording and uploads it; for Web Speech it aborts.
            if (this.recording && this.activeLang === lang) {
                this._stopActive();
                return;
            }
            if (this.recording) {
                this._stopActive();
            }

            // Re-read the engine/mode choices each press so a setting saved since page load takes effect.
            const savedStt = localStorage.getItem('tuganire.frenchStt');
            if (savedStt === 'openai' || savedStt === 'webspeech') {
                this.frenchStt = savedStt;
            }

            // Kinyarwanda → always record + server-side MMS-ASR, then GPT-5.5 cleans the transcript.
            // The browser Web Speech API does not handle Kinyarwanda (it drops negations and mangles words).
            if (lang === 'rw') {
                this._startServerRecording(lang, '/api/v1/stt/transcribe-rw');
                return;
            }
            // French + OpenAI engine → record audio and transcribe server-side (Whisper + correction).
            if (lang === 'fr' && this.frenchStt === 'openai') {
                this._startServerRecording(lang, '/api/v1/stt/transcribe-fr');
                return;
            }
            // French + Web Speech (on-device).
            this._startRecognition(lang);
        },

        /** Stops whichever capture is currently active (MediaRecorder or Web Speech). */
        _stopActive() {
            if (this._mediaRecorder) {
                this._stopOpenAiRecording();
            } else {
                this._stopRecognition();
            }
        },

        _startRecognition(lang) {
            const SpeechRecognition =
                window.SpeechRecognition || window.webkitSpeechRecognition;
            if (!SpeechRecognition) return;

            const recognition = new SpeechRecognition();
            // Map app lang codes to BCP-47 for SpeechRecognition
            // Kinyarwanda is not widely supported; rw-RW will attempt it.
            recognition.lang           = lang === 'fr' ? 'fr-FR' : 'rw-RW';
            recognition.interimResults = true;    // show live text as the user speaks
            recognition.maxAlternatives = 1;
            recognition.continuous      = true;   // keep listening through pauses until the user stops

            // Reset the transcript buffers (once per press; NOT reset on keep-alive restart).
            this._committedTranscript = '';
            this._sessionFinal        = '';
            this._interimTranscript   = '';
            this.liveTranscript       = '';
            this._manualStop          = false;

            recognition.onstart = () => {
                this.recording  = true;
                this.activeLang = lang;
            };

            recognition.onresult = (event) => {
                // Rebuild the CURRENT session's text from the full results list on each event
                // (idempotent — avoids the snowball duplication when resultIndex doesn't advance
                // on mobile Chrome in continuous mode). Text from earlier keep-alive sessions is
                // kept in _committedTranscript. Submission is deferred to _stopRecognition.
                let sessionFinal = '';
                let interim = '';
                for (let i = 0; i < event.results.length; i++) {
                    const res = event.results[i];
                    if (res.isFinal) {
                        sessionFinal += res[0].transcript + ' ';
                    } else {
                        interim += res[0].transcript;
                    }
                }
                this._sessionFinal = sessionFinal.trim();
                this._interimTranscript = interim.trim();
                this.liveTranscript = [this._committedTranscript, this._sessionFinal, this._interimTranscript]
                    .filter(Boolean).join(' ').trim();
            };

            recognition.onerror = (event) => {
                if (event.error !== 'no-speech' && event.error !== 'aborted') {
                    this.errorMessage = this._sttErrorMessage(event.error);
                    // Auto-dismiss after 5 s
                    setTimeout(() => { this.errorMessage = null; }, 5000);
                    // Fatal error: stop the keep-alive loop.
                    this._manualStop = true;
                }
            };

            recognition.onend = () => {
                // The browser ends recognition on its own after a silence even when
                // continuous=true. If the user hasn't pressed stop, commit this session's finals
                // (the next session starts with a fresh, empty results list) and restart.
                if (!this._manualStop && this._recognition) {
                    this._committedTranscript = [this._committedTranscript, this._sessionFinal]
                        .filter(Boolean).join(' ').trim();
                    this._sessionFinal = '';
                    this._interimTranscript = '';
                    try {
                        this._recognition.start();
                        return;
                    } catch (_) { /* already (re)starting → fall through to cleanup */ }
                }
                this.recording    = false;
                this.activeLang   = null;
                this._recognition = null;
            };

            this._recognition = recognition;
            recognition.start();
        },

        /** User pressed stop: finalise the captured transcript and submit it for translation. */
        _stopRecognition() {
            // Capture language + transcript BEFORE clearing state.
            const lang = this.activeLang;
            const transcript = [this._committedTranscript, this._sessionFinal, this._interimTranscript]
                .filter(Boolean).join(' ').trim();

            this._manualStop = true;   // prevent onend keep-alive from restarting
            if (this._recognition) {
                try { this._recognition.abort(); } catch (_) { /* ignored */ }
            }
            this.recording    = false;
            this.activeLang   = null;
            this._recognition = null;
            this._committedTranscript = '';
            this._sessionFinal        = '';
            this._interimTranscript   = '';
            this.liveTranscript       = '';

            if (transcript && lang) {
                this._submitTranscript(transcript, lang);
            }
        },

        /** Stops recognition WITHOUT submitting (used when switching mode / cancelling). */
        _cancelRecognition() {
            this._manualStop = true;
            if (this._recognition) {
                try { this._recognition.abort(); } catch (_) { /* ignored */ }
            }
            this.recording    = false;
            this.activeLang   = null;
            this._recognition = null;
            this._committedTranscript = '';
            this._sessionFinal        = '';
            this._interimTranscript   = '';
            this.liveTranscript       = '';
        },

        /** Called before audio playback to prevent the mic from picking up speaker output. */
        _pauseRecognition() {
            if (this._recognition && this.recording) {
                this._manualStop = true;   // suppress keep-alive restart during playback
                try { this._recognition.abort(); } catch (_) { /* ignored */ }
                // Do not reset activeLang — the user may resume manually.
                this.recording = false;
            }
            // Also release any active MediaRecorder stream so the speaker output is not captured.
            if (this._mediaRecorder) {
                this._discardOpenAiRecording();
            }
        },

        // ── Server-side STT (record → upload) ───────────────────────────────────
        // French OpenAI/Whisper path and Kinyarwanda MMS-ASR path share this code;
        // only the upload endpoint differs.

        /**
         * Starts capturing microphone audio with MediaRecorder. On stop, the audio is
         * uploaded to the given endpoint for server-side transcription.
         *
         * @param {string} lang - 'fr' (Whisper) or 'rw' (MMS-ASR)
         * @param {string} endpoint - the transcription endpoint to upload to
         */
        async _startServerRecording(lang, endpoint) {
            if (!navigator.mediaDevices?.getUserMedia || typeof MediaRecorder === 'undefined') {
                this._flashError(this._sttErrorMessage('audio-capture'));
                return;
            }
            try {
                // Enable the browser's audio cleanup. Without these the raw mic feed (noise, echo,
                // low level) makes the MMS-ASR / Whisper models hallucinate — especially on the
                // silence captured before the user presses stop.
                const stream = await navigator.mediaDevices.getUserMedia({
                    audio: { echoCancellation: true, noiseSuppression: true, autoGainControl: true },
                });
                this._mediaStream = stream;
                this._audioChunks = [];

                // Let the browser pick a container it can actually produce. iOS Safari only
                // supports audio/mp4 (AAC) — NOT audio/webm — so we must not force a mimeType.
                // The chosen type is read back from recorder.mimeType when building the upload.
                const recorder = new MediaRecorder(stream);
                recorder.ondataavailable = (e) => {
                    if (e.data && e.data.size > 0) this._audioChunks.push(e.data);
                };
                recorder.onstop = () => this._uploadServerAudio(lang, endpoint);

                this._mediaRecorder = recorder;
                recorder.start();
                this.recording  = true;
                this.activeLang = lang;
                // Server STT gives no live transcript, so show an elapsed-time + "tap to stop" hint
                // and animate the listening ring from the live mic level — the only "we hear you" cue.
                this._startRecordingFeedback(stream);
            } catch (_) {
                this._releaseStream();
                this._flashError(this._sttErrorMessage('not-allowed'));
            }
        },

        /** Stops the recorder, which triggers onstop → upload. */
        _stopOpenAiRecording() {
            if (this._mediaRecorder && this._mediaRecorder.state !== 'inactive') {
                try { this._mediaRecorder.stop(); } catch (_) { /* ignored */ }
            }
            this.recording  = false;
            this.activeLang = null;
            this._stopRecordingFeedback();
        },

        /** Cancels the recorder and drops any captured audio without uploading. */
        _discardOpenAiRecording() {
            if (this._mediaRecorder) {
                this._mediaRecorder.onstop = null;
                if (this._mediaRecorder.state !== 'inactive') {
                    try { this._mediaRecorder.stop(); } catch (_) { /* ignored */ }
                }
            }
            this._mediaRecorder = null;
            this._audioChunks = [];
            this.recording = false;
            this._stopRecordingFeedback();
            this._releaseStream();
        },

        // ── recording feedback (server STT path: elapsed timer + mic-level meter) ──

        /** Begin the elapsed-second counter and live mic meter for a server recording. */
        _startRecordingFeedback(stream) {
            this.serverRecording = true;
            this.recordingSeconds = 0;
            if (this._recordingTimer) clearInterval(this._recordingTimer);
            this._recordingTimer = setInterval(() => { this.recordingSeconds++; }, 1000);
            this._startMicMeter(stream);
        },

        /** Stop the counter + meter and reset the recording-feedback state. */
        _stopRecordingFeedback() {
            if (this._recordingTimer) {
                clearInterval(this._recordingTimer);
                this._recordingTimer = null;
            }
            this.serverRecording = false;
            this.recordingSeconds = 0;
            this._stopMicMeter();
        },

        /**
         * Drive {@link micLevel} (0..1) from the live microphone RMS via a Web Audio AnalyserNode, so the
         * listening ring pulses with the user's voice. Best-effort: any failure (no AudioContext, blocked
         * autoplay policy) silently no-ops — the timer + hint still provide feedback.
         */
        _startMicMeter(stream) {
            try {
                // Respect reduced-motion: skip the voice-reactive pulse, leave the static ring as the cue.
                if (window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches) return;
                const Ctx = window.AudioContext || window.webkitAudioContext;
                if (!Ctx || !stream) return;
                const ctx = new Ctx();
                // Some browsers start the context suspended until resumed inside a gesture (we are in
                // the mic-press gesture here). Best-effort — a rejection just leaves the meter idle.
                if (ctx.state === 'suspended' && ctx.resume) { ctx.resume().catch(() => {}); }
                const source = ctx.createMediaStreamSource(stream);
                const analyser = ctx.createAnalyser();
                analyser.fftSize = 512;
                source.connect(analyser);
                this._micAudioCtx = ctx;
                this._micAnalyser = analyser;
                const buf = new Uint8Array(analyser.fftSize);
                const tick = () => {
                    if (!this._micAnalyser) return;
                    analyser.getByteTimeDomainData(buf);
                    let sum = 0;
                    for (let i = 0; i < buf.length; i++) {
                        const v = (buf[i] - 128) / 128;
                        sum += v * v;
                    }
                    const rms = Math.sqrt(sum / buf.length);
                    // Scale up (speech RMS is small) and clamp to 0..1 for a lively but bounded ring.
                    this.micLevel = Math.min(1, rms * 4);
                    this._micRafId = requestAnimationFrame(tick);
                };
                tick();
            } catch (_) { /* meter is optional — ignore */ }
        },

        /** Tear down the mic meter and reset the level. */
        _stopMicMeter() {
            if (this._micRafId) {
                cancelAnimationFrame(this._micRafId);
                this._micRafId = null;
            }
            this._micAnalyser = null;
            if (this._micAudioCtx) {
                try { this._micAudioCtx.close(); } catch (_) { /* ignored */ }
                this._micAudioCtx = null;
            }
            this.micLevel = 0;
        },

        /** Stops all microphone tracks to turn off the recording indicator. */
        _releaseStream() {
            if (this._mediaStream) {
                this._mediaStream.getTracks().forEach((t) => t.stop());
                this._mediaStream = null;
            }
        },

        /**
         * Uploads the recorded audio to the given transcription endpoint, then submits the
         * resulting transcript through the normal /translate flow.
         *
         * @param {string} lang - 'fr' or 'rw'
         * @param {string} endpoint - the transcription endpoint to upload to
         */
        async _uploadServerAudio(lang, endpoint) {
            this._releaseStream();
            this._stopRecordingFeedback();
            const chunks = this._audioChunks || [];
            this._audioChunks = [];
            this._mediaRecorder = null;
            // Nothing captured (mic produced no data) — tell the user instead of failing silently.
            if (chunks.length === 0) {
                this._flashError(this._noSpeechMessage());
                return;
            }

            const { blob, ext } = this._recordedBlob(chunks);
            this.translating = true;
            this.processing  = true;   // show the progress bar across STT → translation
            // The MMS server is scale-to-zero: a request after idle can take 30-60 s to wake (model
            // reload). After a short delay, tell the user we're waiting so it doesn't look frozen.
            const warmupTimer = setTimeout(() => { this.warmingUp = true; }, 6000);
            try {
                const csrfToken  = document.querySelector('meta[name="_csrf"]')?.content || '';
                const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content || 'X-CSRF-TOKEN';

                const formData = new FormData();
                formData.append('audio', blob, `speech.${ext}`);

                const resp = await fetch(endpoint, {
                    method: 'POST',
                    headers: { [csrfHeader]: csrfToken },
                    body: formData,
                });
                // A non-OK response is an STT failure (bad audio, model error), not a connectivity
                // problem — tag it so the catch shows the STT message rather than "network".
                if (!resp.ok) {
                    const err = new Error('stt-failed');
                    err.sttHttp = true;
                    throw err;
                }

                const data = await resp.json();
                const text = (data.corrected || data.raw || '').trim();
                if (text) {
                    // _submitTranscript opens the SSE stream which keeps `processing` true
                    // until `done`/`error`; only clear it here if we have nothing to submit.
                    this._submitTranscript(text, lang);
                } else {
                    // STT succeeded but heard nothing (silence / too far from mic). Never leave the
                    // user staring at a screen that did nothing — surface a clear, actionable message.
                    this.processing = false;
                    this._flashError(this._noSpeechMessage());
                }
            } catch (e) {
                this.processing = false;
                this._flashError(e && e.sttHttp
                    ? this._sttErrorMessage('stt-failed')
                    : this._sttErrorMessage('network'));
            } finally {
                this.translating = false;
                clearTimeout(warmupTimer);
                this.warmingUp = false;
            }
        },

        /**
         * Builds the upload Blob from the recorded chunks, preserving the browser's actual container.
         * iOS Safari records audio/mp4, Chrome/Firefox audio/webm; mislabelling the part trips the
         * server's MIME allowlist and the wrong extension can break decoding. Falls back to webm.
         *
         * @param {Blob[]} chunks - the recorded media chunks
         * @returns {{blob: Blob, ext: string}} the upload blob and its filename extension
         */
        _recordedBlob(chunks) {
            const recordedType = (chunks[0] && chunks[0].type) || 'audio/webm';
            const ext = recordedType.includes('mp4') ? 'mp4'
                : recordedType.includes('ogg') ? 'ogg'
                : recordedType.includes('wav') ? 'wav'
                : 'webm';
            return { blob: new Blob(chunks, { type: recordedType }), ext };
        },

        /** Recording hint text with the elapsed seconds substituted (i18n, with a French fallback). */
        recordingHintText() {
            const tpl = (window.tuganireMessages && window.tuganireMessages.recordingHint)
                || 'Enregistrement… {0} s · touchez pour arrêter';
            return tpl.replace('{0}', this.recordingSeconds);
        },

        /** The "I heard nothing" message, from i18n, with a sensible fallback. */
        _noSpeechMessage() {
            const m = window.tuganireMessages || {};
            return m.noSpeech || m.sttFailed || m.errorGeneric || '';
        },

        /** Show a transient error toast (auto-dismiss after 5 s), no-op when the message is empty. */
        _flashError(message) {
            if (!message) return;
            this.errorMessage = message;
            setTimeout(() => { this.errorMessage = null; }, 5000);
        },

        // ── transcript submission ───────────────────────────────────────────

        /**
         * Route a finalised transcript into the translation flow via the SSE streaming pipeline.
         *  - Two-button mode: the bubble streams into the single conversation list.
         *  - Split-screen mode: the bubble streams into the listener's (target-language) panel.
         */
        _submitTranscript(transcript, lang) {
            const sourceLang = lang;
            const targetLang = lang === 'fr' ? 'rw' : 'fr';

            // In split-screen mode the translation streams into the LISTENER's panel — the half
            // showing the TARGET language — so the rotated top panel faces the right reader. In
            // two-button mode it streams into the single conversation list. Both use the same
            // progressive SSE pipeline (corrected text → translation → audio + rich bubble controls).
            const listId = this.mode === 'splitScreen'
                ? (targetLang === 'rw' ? 'split-transcript-rw' : 'split-transcript-fr')
                : 'conversation-list';

            this._streamTranslate(transcript, sourceLang, targetLang, listId);
        },

        // ── streaming translation (SSE) ─────────────────────────────────────────

        /**
         * Open an EventSource against /api/v1/stream/translate and render the conversation bubble
         * progressively: the corrected source text streams into the left bubble, the translation
         * streams into the right bubble, audio auto-plays when ready, and the progress bar shows
         * until `done`/`error`. The bubble markup mirrors fragments/translation.html.
         *
         * @param {string} text       - the raw transcript to correct + translate
         * @param {string} sourceLang - 'fr' | 'rw'
         * @param {string} targetLang - 'rw' | 'fr'
         */
        _streamTranslate(text, sourceLang, targetLang, listId) {
            // Only one stream at a time — tear down any previous one.
            this._closeStream();

            const translationId = (crypto.randomUUID && crypto.randomUUID())
                || `t-${Date.now()}-${Math.random().toString(16).slice(2)}`;

            // Build the (initially empty) bubble and insert it into the target list (the conversation
            // list in two-button mode, or the listener's panel in split-screen mode).
            const refs = this._buildStreamingBubble(translationId, sourceLang, targetLang, listId);
            if (!refs) return;
            const scroll = () => this.scrollToBottom(refs.listId);

            // Show the transcript immediately (deterministic tidy from STT) so the user sees text right away,
            // without waiting on the streamed LLM correction. The first `correction` token clears it before
            // appending the refined version (and `correction-done` sets the final text).
            refs.sourceText.textContent = text;
            let correctionStarted = false;

            this.bubbleCount++;
            this.processing  = true;
            this.translating = true;
            scroll();

            const params = new URLSearchParams({
                text,
                sourceLang,
                targetLang,
                sessionId: this.sessionId || '',
            });
            const es = new EventSource(`/api/v1/stream/translate?${params.toString()}`);
            this._eventSource = es;
            // If no token arrives within a few seconds (a slow dependency waking mid-pipeline), show the
            // warm-up banner so the stream doesn't look frozen; cleared on the first token / close.
            this._armStreamWarmup();

            // 1. correction token → append to the SOURCE bubble
            es.addEventListener('correction', (e) => {
                this._clearStreamWarmup();
                const token = this._eventToken(e);
                if (token) {
                    // Drop the immediately-shown transcript on the first streamed token, then append the refined one.
                    if (!correctionStarted) {
                        refs.sourceText.textContent = '';
                        correctionStarted = true;
                    }
                    refs.sourceText.textContent += token;
                    scroll();
                }
            });

            // 2. correction-done → set the final corrected source text
            es.addEventListener('correction-done', (e) => {
                const data = this._parseEvent(e);
                if (data && typeof data.text === 'string') {
                    refs.sourceText.textContent = data.text;
                    scroll();
                }
            });

            // 3. translation token → append to the TARGET bubble
            es.addEventListener('translation', (e) => {
                this._clearStreamWarmup();
                const token = this._eventToken(e);
                if (token) {
                    refs.targetText.textContent += token;
                    this.scrollToBottom(refs.listId);
                }
            });

            // 4. translation-done → set final translation, wire + autoplay audio
            es.addEventListener('translation-done', (e) => {
                const data = this._parseEvent(e);
                if (!data) return;
                if (typeof data.text === 'string') {
                    refs.targetText.textContent = data.text;
                }
                if (data.audioUrl) {
                    this._attachAudio(refs, data.audioUrl);
                }
                this.scrollToBottom(refs.listId);
            });

            // 5. done → close, hide progress bar, ensure controls are present
            es.addEventListener('done', () => {
                this._closeStream();
                this.processing  = false;
                this.translating = false;
                this.scrollToBottom(refs.listId);
            });

            // 6. error (server-sent) → close, hide progress bar, show toast
            es.addEventListener('error', (e) => {
                // Distinguish a server-sent `error` event (has JSON data) from a transport
                // failure (EventSource fires a dataless `error` on network drop / close).
                const data = this._parseEvent(e);
                if (data || es.readyState === EventSource.CLOSED) {
                    this._closeStream();
                    this.processing  = false;
                    this.translating = false;
                    // If nothing rendered, drop the empty bubble so the list stays clean.
                    if (refs.root && !refs.targetText.textContent && !refs.sourceText.textContent) {
                        refs.root.remove();
                        if (this.bubbleCount > 0) this.bubbleCount--;
                    }
                    const msgs = window.tuganireMessages || {};
                    const message = (data && data.message)
                        ? data.message
                        : (msgs.translateFailed || msgs.errorGeneric || '');
                    if (message) {
                        this.errorMessage = message;
                        setTimeout(() => { this.errorMessage = null; }, 5000);
                    }
                }
            });
        },

        /** Close + null out the active EventSource (idempotent). */
        _closeStream() {
            this._clearStreamWarmup();
            if (this._eventSource) {
                try { this._eventSource.close(); } catch (_) { /* ignored */ }
                this._eventSource = null;
            }
        },

        /** Arm a timer that shows the warm-up banner if the SSE stream stalls before its first token. */
        _armStreamWarmup() {
            this._clearStreamWarmup();
            this._streamWarmupTimer = setTimeout(() => { this.warmingUp = true; }, 7000);
        },

        /** Cancel the SSE warm-up timer and hide the banner. */
        _clearStreamWarmup() {
            if (this._streamWarmupTimer) {
                clearTimeout(this._streamWarmupTimer);
                this._streamWarmupTimer = null;
            }
            this.warmingUp = false;
        },

        /** Safely JSON.parse an SSE event's `data`; returns null when absent/invalid. */
        _parseEvent(e) {
            if (!e || !e.data) return null;
            try { return JSON.parse(e.data); } catch (_) { return null; }
        },

        /** Extract a `{token}` string from an SSE event payload. */
        _eventToken(e) {
            const data = this._parseEvent(e);
            return data && typeof data.token === 'string' ? data.token : '';
        },

        /**
         * Build a conversation bubble client-side, mirroring fragments/translation.html:
         * a left SOURCE bubble and a right TARGET bubble (with replay + feedback controls and a
         * hidden audio element added later). Returns handles to the mutable text nodes / containers.
         *
         * @returns {{root: HTMLElement, sourceText: HTMLElement, targetText: HTMLElement,
         *            targetBubble: HTMLElement, actionRow: HTMLElement, targetLang: string}|null}
         */
        _buildStreamingBubble(translationId, sourceLang, targetLang, listId) {
            const resolvedListId = listId || 'conversation-list';
            const list = document.getElementById(resolvedListId);
            if (!list) return null;
            const msgs = window.tuganireMessages || {};

            const langName = (code) => code === 'fr'
                ? (msgs.langNameFr || 'Français')
                : (msgs.langNameRw || 'Ikinyarwanda');
            const fmt = (tpl, val) => (tpl || '').replace('{0}', val);

            const root = document.createElement('div');
            root.className = 'animate-slide-in-up';
            root.id = `bubble-${translationId}`;

            // ── Source bubble (left) ──────────────────────────────────────────
            const srcRow = document.createElement('div');
            srcRow.className = 'flex justify-start mb-2';
            const srcBubble = document.createElement('div');
            srcBubble.className = 'max-w-[85%] sm:max-w-[70%] rounded-lg rounded-tl-none px-4 py-3 bg-base-200 border-l-4 shadow-sm '
                + (sourceLang === 'fr' ? 'border-primary' : 'border-accent');
            srcBubble.setAttribute('role', 'group');
            srcBubble.setAttribute('aria-label', fmt(msgs.ariaBubbleSource, langName(sourceLang)));

            const srcLabel = document.createElement('p');
            srcLabel.className = 'text-xs font-medium mb-1 tracking-wide '
                + (sourceLang === 'fr' ? 'text-primary/70' : 'text-accent/70');
            srcLabel.innerHTML =
                `<span class="uppercase">${sourceLang}</span>`
                + `<span aria-hidden="true"> · </span>`
                + `<span></span>`;
            srcLabel.lastElementChild.textContent = langName(sourceLang);

            const srcText = document.createElement('p');
            srcText.className = 'text-sm text-base-content/80 leading-relaxed';
            srcText.setAttribute('aria-live', 'polite');
            srcText.textContent = '';

            srcBubble.appendChild(srcLabel);
            srcBubble.appendChild(srcText);
            srcRow.appendChild(srcBubble);

            // ── Target bubble (right) ─────────────────────────────────────────
            const tgtRow = document.createElement('div');
            tgtRow.className = 'flex justify-end mb-4';
            const tgtBubble = document.createElement('div');
            tgtBubble.className = 'max-w-[85%] sm:max-w-[70%] rounded-lg rounded-tr-none px-4 py-3 bg-base-200 border-r-4 shadow-sm '
                + (targetLang === 'fr' ? 'border-primary' : 'border-accent');
            tgtBubble.setAttribute('role', 'group');
            tgtBubble.setAttribute('aria-label', fmt(msgs.ariaBubbleTarget, langName(targetLang)));

            const tgtLabel = document.createElement('p');
            tgtLabel.className = 'text-xs font-medium mb-1 tracking-wide '
                + (targetLang === 'fr' ? 'text-primary/70' : 'text-accent/70');
            tgtLabel.innerHTML =
                `<span class="uppercase">${targetLang}</span>`
                + `<span aria-hidden="true"> · </span>`
                + `<span></span>`;
            tgtLabel.lastElementChild.textContent = langName(targetLang);

            const tgtText = document.createElement('p');
            tgtText.className = 'text-base font-medium text-base-content leading-relaxed mb-3';
            tgtText.setAttribute('aria-live', 'polite');
            tgtText.textContent = '';

            // Action row (replay + feedback) — populated once audio/text arrive.
            const actionRow = document.createElement('div');
            // flex-wrap so the controls never overflow the bubble on narrow phones.
            actionRow.className = 'flex flex-wrap items-center gap-x-2 gap-y-1 pt-1 border-t border-base-300/50';

            tgtBubble.appendChild(tgtLabel);
            tgtBubble.appendChild(tgtText);
            tgtBubble.appendChild(actionRow);
            tgtRow.appendChild(tgtBubble);

            root.appendChild(srcRow);
            root.appendChild(tgtRow);

            // Insert before the error toast node if present, else append.
            list.appendChild(root);

            return {
                root,
                sourceText: srcText,
                targetText: tgtText,
                targetBubble: tgtBubble,
                actionRow,
                translationId,
                sourceLang,
                targetLang,
                listId: resolvedListId,
                _controlsBuilt: false,
            };
        },

        /**
         * Render the replay + feedback controls, then auto-read the translation aloud through the
         * shared, gesture-unlocked player so playback starts as soon as the pipeline finishes.
         */
        _attachAudio(refs, audioUrl) {
            refs.audioUrl = audioUrl;
            this._buildBubbleControls(refs);
            this._playTts(audioUrl);
        },

        /** Lazily creates the single reusable hidden <audio> used for all TTS playback. */
        _ensureSharedAudio() {
            if (!this._ttsAudio) {
                const audio = document.createElement('audio');
                audio.id = 'tuganire-tts-player';
                audio.preload = 'auto';
                audio.className = 'sr-only';
                document.body.appendChild(audio);
                this._ttsAudio = audio;
            }
            return this._ttsAudio;
        },

        /**
         * Play a short silent clip on the shared player within the current user gesture. This grants
         * future programmatic play() calls (the auto-read) permission on iOS/Safari; a no-op elsewhere.
         */
        _unlockAudio() {
            if (this._audioUnlocked) return;
            const audio = this._ensureSharedAudio();
            try {
                audio.src = SILENT_AUDIO;
                const p = audio.play();
                if (p && p.then) {
                    // Mark unlocked only once the gesture-play actually succeeds, so a press that the
                    // browser rejects is retried on the next press rather than silently giving up.
                    p.then(() => { this._audioUnlocked = true; audio.pause(); audio.currentTime = 0; })
                        .catch(() => { /* retry on next press */ });
                } else {
                    this._audioUnlocked = true;
                }
            } catch (_) { /* ignored */ }
        },

        /**
         * Read {@code url} aloud through the shared player, pausing recognition first so the mic does
         * not capture the speaker output. Returns the play() promise (rejection swallowed).
         */
        _playTts(url) {
            if (!url) return Promise.resolve();
            const audio = this._ensureSharedAudio();
            // Pause recognition before playback so the mic doesn't capture the speaker output.
            this.$el.dispatchEvent(new CustomEvent('pause-recognition'));
            audio.src = url;
            const p = audio.play();
            return p && p.catch ? p.catch(() => { /* autoplay may be blocked; replay button remains */ }) : Promise.resolve();
        },

        /** Render the replay button + feedback (👍/👎) controls into the bubble action row. */
        _buildBubbleControls(refs) {
            if (refs._controlsBuilt) return;
            refs._controlsBuilt = true;
            const msgs = window.tuganireMessages || {};

            // Replay button
            const replayBtn = document.createElement('button');
            replayBtn.type = 'button';
            replayBtn.setAttribute('aria-label', msgs.replay || 'Rejouer');
            replayBtn.className = 'btn btn-ghost btn-xs gap-1 text-base-content/60 hover:text-accent transition-colors duration-150';
            replayBtn.innerHTML =
                `<svg xmlns="http://www.w3.org/2000/svg" class="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2" aria-hidden="true">`
                + `<path stroke-linecap="round" stroke-linejoin="round" d="M19.114 5.636a9 9 0 010 12.728M16.463 8.288a5.25 5.25 0 010 7.424M6.75 8.25l4.72-4.72a.75.75 0 011.28.53v15.88a.75.75 0 01-1.28.53l-4.72-4.72H4.51c-.88 0-1.704-.507-1.938-1.354A9.009 9.009 0 012.25 12c0-.83.112-1.633.322-2.396C2.806 8.756 3.63 8.25 4.51 8.25H6.75z"/></svg>`
                + `<span class="text-xs hidden sm:inline"></span>`;
            replayBtn.lastElementChild.textContent = msgs.replay || 'Rejouer';
            replayBtn.addEventListener('click', () => {
                this._playTts(refs.audioUrl);
            });
            refs.actionRow.appendChild(replayBtn);

            // Listen-in-other-language button — translate the target text BACK to the source
            // language and speak it (e.g. hear the French meaning of a Kinyarwanda bubble).
            const toLangName = refs.sourceLang === 'fr'
                ? (msgs.langNameFr || 'Français')
                : (msgs.langNameRw || 'Ikinyarwanda');
            const listenLabel = (msgs.listenIn || 'Écouter en {0}').replace('{0}', toLangName);
            const backBtn = document.createElement('button');
            backBtn.type = 'button';
            backBtn.setAttribute('aria-label', listenLabel);
            backBtn.className = 'btn btn-ghost btn-xs gap-1 text-base-content/60 hover:text-accent transition-colors duration-150';
            backBtn.innerHTML =
                `<svg xmlns="http://www.w3.org/2000/svg" class="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2" aria-hidden="true">`
                + `<path stroke-linecap="round" stroke-linejoin="round" d="m10.5 21 5.25-11.25L21 21m-9-3h7.5M3 5.621a48.474 48.474 0 0 1 6-.371m0 0c1.12 0 2.233.038 3.334.114M9 5.25V3m3.334 2.364C11.176 10.658 7.69 15.08 3 17.502m9.334-12.138c.896.061 1.785.147 2.666.257m-4.589 8.495a18.023 18.023 0 0 1-3.827-5.802"/></svg>`
                + `<span class="text-xs hidden sm:inline"></span>`;
            backBtn.lastElementChild.textContent = listenLabel;

            // Hidden <audio> for the back-translation (translate target → source, then speak).
            const backAudio = document.createElement('audio');
            backAudio.id = `audio-back-${refs.translationId}`;
            backAudio.preload = 'none';
            backAudio.className = 'sr-only';
            backAudio.setAttribute('aria-label', listenLabel);
            refs.targetBubble.insertBefore(backAudio, refs.actionRow);

            backBtn.addEventListener('click', () => {
                // Read the (final) target text at click time so it always matches the rendered bubble.
                const targetText = (refs.targetText.textContent || '').trim();
                if (!targetText) return;
                backAudio.src = `/api/v1/audio/translate-speak.mp3?text=${encodeURIComponent(targetText)}`
                    + `&from=${encodeURIComponent(refs.targetLang)}&to=${encodeURIComponent(refs.sourceLang)}`;
                this.$el.dispatchEvent(new CustomEvent('pause-recognition'));
                backAudio.currentTime = 0;
                backAudio.play().catch(() => { /* autoplay may be blocked */ });
            });
            refs.actionRow.appendChild(backBtn);

            // Copy button — copies the current TARGET text to the clipboard.
            const copyLabel = msgs.copy || 'Copier';
            const copiedLabel = msgs.copied || 'Copié';
            const copyBtn = document.createElement('button');
            copyBtn.type = 'button';
            copyBtn.setAttribute('aria-label', copyLabel);
            copyBtn.className = 'btn btn-ghost btn-xs gap-1 text-base-content/60 hover:text-accent transition-colors duration-150';
            copyBtn.innerHTML =
                `<svg xmlns="http://www.w3.org/2000/svg" class="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2" aria-hidden="true">`
                + `<path stroke-linecap="round" stroke-linejoin="round" d="M15.75 17.25v3.375c0 .621-.504 1.125-1.125 1.125h-9.75a1.125 1.125 0 01-1.125-1.125V7.875c0-.621.504-1.125 1.125-1.125H6.75a9.06 9.06 0 011.5.124m7.5 10.376h3.375c.621 0 1.125-.504 1.125-1.125V11.25c0-4.46-3.243-8.161-7.5-8.876a9.06 9.06 0 00-1.5-.124H9.375c-.621 0-1.125.504-1.125 1.125v3.5m7.5 10.375H9.375a1.125 1.125 0 01-1.125-1.125v-9.25m12 6.625v-1.875a3.375 3.375 0 00-3.375-3.375h-1.5a1.125 1.125 0 01-1.125-1.125v-1.5a3.375 3.375 0 00-3.375-3.375H9.75"/></svg>`
                + `<span class="text-xs hidden sm:inline"></span>`;
            copyBtn.lastElementChild.textContent = copyLabel;

            let copyResetTimer = null;
            copyBtn.addEventListener('click', () => {
                // Capture whatever is currently displayed in the target text node (it may have
                // been finalised on translation-done).
                const targetText = (refs.targetText.textContent || '').trim();
                if (!targetText || !navigator.clipboard) return;
                navigator.clipboard.writeText(targetText).then(() => {
                    copyBtn.lastElementChild.textContent = copiedLabel;
                    copyBtn.setAttribute('aria-label', copiedLabel);
                    if (copyResetTimer) clearTimeout(copyResetTimer);
                    copyResetTimer = setTimeout(() => {
                        copyBtn.lastElementChild.textContent = copyLabel;
                        copyBtn.setAttribute('aria-label', copyLabel);
                    }, 1500);
                }).catch(() => { /* clipboard may be unavailable */ });
            });
            refs.actionRow.appendChild(copyBtn);

            // Feedback (👍 / 👎) — posts form-encoded to /feedback with CSRF + sessionId,
            // mirroring fragments/feedback.html. On success the group is replaced by a thanks note.
            const feedbackGroup = document.createElement('div');
            feedbackGroup.className = 'flex items-center gap-1 ml-auto';
            feedbackGroup.id = `feedback-${refs.translationId}`;

            const makeFeedbackBtn = (type, label, hoverClass, iconPath) => {
                const btn = document.createElement('button');
                btn.type = 'button';
                btn.setAttribute('aria-label', label);
                btn.setAttribute('title', label);
                btn.className = `btn btn-ghost btn-xs text-base-content/50 ${hoverClass} transition-colors duration-150 cursor-pointer`;
                btn.innerHTML =
                    `<svg xmlns="http://www.w3.org/2000/svg" class="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2" aria-hidden="true">`
                    + `<path stroke-linecap="round" stroke-linejoin="round" d="${iconPath}"/></svg>`;
                btn.addEventListener('click', () => this._sendFeedback(refs.translationId, type));
                return btn;
            };

            feedbackGroup.appendChild(makeFeedbackBtn(
                'THUMBS_UP', msgs.thumbsUp || 'Bon', 'hover:text-success',
                'M6.633 10.5c.806 0 1.533-.446 2.031-1.08a9.041 9.041 0 012.861-2.4c.723-.384 1.35-.956 1.653-1.715a4.498 4.498 0 00.322-1.672V3a.75.75 0 01.75-.75A2.25 2.25 0 0116.5 4.5c0 1.152-.26 2.243-.723 3.218-.266.558.107 1.282.725 1.282h3.126c1.026 0 1.945.694 2.054 1.715.045.422.068.85.068 1.285a11.95 11.95 0 01-2.649 7.521c-.388.482-.987.729-1.605.729H13.48c-.483 0-.964-.078-1.423-.23l-3.114-1.04a4.501 4.501 0 00-1.423-.23H5.904M14.25 9h2.25M5.904 18.75c.083.205.173.405.27.602.197.4-.078.898-.523.898h-.908c-.889 0-1.713-.518-1.972-1.368a12 12 0 01-.521-3.507c0-1.553.295-3.036.831-4.398C3.387 10.203 4.167 9.75 5 9.75h1.053c.472 0 .745.556.5.96a8.958 8.958 0 00-1.302 4.665c0 1.194.232 2.333.654 3.375z'));
            feedbackGroup.appendChild(makeFeedbackBtn(
                'THUMBS_DOWN', msgs.thumbsDown || 'Mauvais', 'hover:text-error',
                'M7.5 15h2.25m8.024-9.75c.011.05.028.1.052.148.591 1.2.924 2.55.924 3.977a8.96 8.96 0 01-.999 4.125m.023-8.25c-.076-.365.183-.75.575-.75h.908c.889 0 1.713.518 1.972 1.368.339 1.11.521 2.287.521 3.507 0 1.553-.295 3.036-.831 4.398C20.613 14.547 19.833 15 19 15h-1.053c-.472 0-.745-.556-.5-.96a8.95 8.95 0 00.303-.54m.023-8.25H16.48a4.5 4.5 0 01-1.423-.23l-3.114-1.04a4.5 4.5 0 00-1.423-.23H6.504c-.618 0-1.217.247-1.605.729A11.95 11.95 0 002.25 12c0 .434.023.863.068 1.285C2.427 14.306 3.346 15 4.372 15h3.126c.618 0 .991.724.725 1.282A7.471 7.471 0 007.5 19.5a2.25 2.25 0 002.25 2.25.75.75 0 00.75-.75v-.633c0-.573.11-1.14.322-1.672.304-.76.93-1.33 1.653-1.715a9.04 9.04 0 002.86-2.4c.498-.634 1.226-1.08 2.032-1.08h.384'));

            refs.actionRow.appendChild(feedbackGroup);
        },

        /** POST a feedback vote (form-encoded, with CSRF + sessionId), then swap in a thanks note. */
        _sendFeedback(translationId, type) {
            const csrfToken     = document.querySelector('meta[name="_csrf"]')?.content || '';
            const csrfHeader    = document.querySelector('meta[name="_csrf_header"]')?.content || 'X-CSRF-TOKEN';
            const csrfParamName = '_csrf';

            const body = new URLSearchParams();
            body.append(csrfParamName, csrfToken);
            body.append('translationId', translationId);
            body.append('sessionId', this.sessionId || '');
            body.append('type', type);

            fetch('/feedback', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded',
                    [csrfHeader]: csrfToken,
                },
                body: body.toString(),
            }).then(() => {
                const group = document.getElementById(`feedback-${translationId}`);
                if (group) {
                    const msgs = window.tuganireMessages || {};
                    const thanks = document.createElement('span');
                    thanks.className = 'text-xs text-success font-medium animate-fadeIn';
                    thanks.textContent = msgs.feedbackThanks || 'Merci !';
                    group.replaceChildren(thanks);
                }
            }).catch(() => { /* feedback is best-effort */ });
        },


        // ── DOM helpers ────────────────────────────────────────────────────

        /**
         * Scroll a conversation list to the bottom after a new bubble.
         *
         * @param {string} [listId] - target list id; defaults to the two-button conversation list.
         *                             In split-screen mode the streaming flow passes the active panel id.
         */
        scrollToBottom(listId) {
            const list = document.getElementById(listId || 'conversation-list');
            if (list) {
                list.scrollTo({ top: list.scrollHeight, behavior: 'smooth' });
            }
        },

        // ── error messages ─────────────────────────────────────────────────

        /**
         * Map Web Speech API error codes to user-friendly messages.
         * Strings come from window.tuganireMessages, which is populated by
         * Thymeleaf's th:inline="javascript" in index.html — always in the
         * active locale, zero hardcoded strings in this JS file.
         */
        _sttErrorMessage(error) {
            const m = window.tuganireMessages || {};
            const map = {
                'not-allowed':         m.notAllowed    || m.sttFailed || '',
                'audio-capture':       m.audioCapture  || m.sttFailed || '',
                'network':             m.networkError  || m.sttFailed || '',
                'service-not-allowed': m.httpsRequired || m.sttFailed || '',
            };
            return map[error] || m.sttFailed || m.errorGeneric || '';
        },
    };
}
