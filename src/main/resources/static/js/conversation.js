/**
 * conversation.js — Alpine.js component for the Tuganire "deux boutons" screen.
 *
 * Responsibilities:
 *  - Detect Web Speech API support (Chrome / Chromium only).
 *  - Obtain an anonymous sessionId from POST /api/v1/sessions.
 *  - Toggle speech recognition on button press (fr-FR or rw-RW).
 *  - On a final SpeechRecognition result, submit the transcript to POST /translate
 *    via the hidden HTMX form so Thymeleaf fragment swapping handles the DOM update.
 *  - Pause recognition during audio playback to avoid self-triggering.
 *  - Expose scrollToBottom() so HTMX's after-request hook can keep the list scrolled.
 *  - Toggle between 'twoButtons' and 'splitScreen' modes (US-04) without page reload.
 *  - In split-screen mode, route HTMX submissions to the correct side transcript list.
 */
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

        /** 'twoButtons' (default) | 'splitScreen' — persisted in localStorage. */
        mode: 'twoButtons',

        /**
         * French STT engine: 'webspeech' (on-device Web Speech API, default) or
         * 'openai' (server-side Whisper + LLM correction). Persisted in localStorage.
         * Only affects the French side; Kinyarwanda always uses Web Speech API.
         */
        frenchStt: 'webspeech',

        /**
         * Kinyarwanda A/B comparison mode (per-device, set in /settings). When on, each Kinyarwanda
         * utterance is cleaned by two models and the user picks the better transcription.
         */
        compareRw: false,

        /** Target list ID for the current split-screen recording side. */
        _splitTarget: null,

        /** SpeechRecognition instance (created fresh per recording session). */
        _recognition: null,

        /** MediaRecorder + stream + chunks for the OpenAI (server) French STT path. */
        _mediaRecorder: null,
        _mediaStream: null,
        _audioChunks: [],

        /** Accumulated final transcript segments, persisted across keep-alive restarts. */
        _finalTranscript: '',
        /** Latest interim (not-yet-final) transcript text. */
        _interimTranscript: '',
        /** True when the user pressed stop or playback paused us — suppresses keep-alive restart. */
        _manualStop: false,
        /** Live transcript (final + interim) shown while recording. */
        liveTranscript: '',

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

            // Restore the Kinyarwanda A/B comparison-mode toggle
            this.compareRw = localStorage.getItem('tuganire.compareRw') === '1';

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
            if (this.recording) {
                this._cancelRecognition();
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
         * Toggle recognition for the given language code ('fr' or 'rw').
         * In split-screen mode, pass the target transcript list ID so HTMX
         * injects the response bubble into the correct half.
         *
         * @param {string} lang - 'fr' | 'rw'
         * @param {string|null} splitTargetId - DOM ID of the target transcript list (split mode only)
         */
        toggleRecognition(lang, splitTargetId) {
            // Pressing the active button again stops it. For the OpenAI French path this
            // finalises the recording and uploads it; for Web Speech it aborts.
            if (this.recording && this.activeLang === lang) {
                this._stopActive();
                return;
            }
            if (this.recording) {
                this._stopActive();
            }
            this._splitTarget = splitTargetId || null;

            // Re-read the engine/mode choices each press so a setting saved since page load takes effect.
            const savedStt = localStorage.getItem('tuganire.frenchStt');
            if (savedStt === 'openai' || savedStt === 'webspeech') {
                this.frenchStt = savedStt;
            }
            this.compareRw = localStorage.getItem('tuganire.compareRw') === '1';

            // Kinyarwanda → always record + server-side MMS-ASR. The browser Web Speech API
            // does not handle Kinyarwanda (it drops negations and mangles words). In comparison
            // mode, two models clean the transcript and the user picks the better one.
            if (lang === 'rw') {
                const endpoint = this.compareRw
                    ? '/api/v1/stt/transcribe-rw/compare'
                    : '/api/v1/stt/transcribe-rw';
                this._startServerRecording(lang, endpoint, this.compareRw);
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

            // Reset the per-session transcript buffers.
            this._finalTranscript   = '';
            this._interimTranscript = '';
            this.liveTranscript     = '';
            this._manualStop        = false;

            recognition.onstart = () => {
                this.recording  = true;
                this.activeLang = lang;
            };

            recognition.onresult = (event) => {
                // Append newly finalised segments to our own buffer (it survives keep-alive
                // restarts), and track the latest interim text for the live display.
                // Submission is deferred to _stopRecognition (the user's second press).
                let interim = '';
                for (let i = event.resultIndex; i < event.results.length; i++) {
                    const res = event.results[i];
                    if (res.isFinal) {
                        this._finalTranscript = (this._finalTranscript + ' ' + res[0].transcript).trim();
                    } else {
                        interim += res[0].transcript;
                    }
                }
                this._interimTranscript = interim.trim();
                this.liveTranscript = (this._finalTranscript + ' ' + this._interimTranscript).trim();
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
                // continuous=true. If the user hasn't pressed stop, restart to keep listening.
                if (!this._manualStop && this._recognition) {
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
            const transcript = (this._finalTranscript + ' ' + this._interimTranscript).trim();

            this._manualStop = true;   // prevent onend keep-alive from restarting
            if (this._recognition) {
                try { this._recognition.abort(); } catch (_) { /* ignored */ }
            }
            this.recording    = false;
            this.activeLang   = null;
            this._recognition = null;
            this._finalTranscript   = '';
            this._interimTranscript = '';
            this.liveTranscript     = '';

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
            this._finalTranscript   = '';
            this._interimTranscript = '';
            this.liveTranscript     = '';
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
         * @param {boolean} [compare] - when true, render the A/B chooser instead of auto-submitting
         */
        async _startServerRecording(lang, endpoint, compare) {
            this._compareMode = !!compare;
            if (!navigator.mediaDevices?.getUserMedia || typeof MediaRecorder === 'undefined') {
                this.errorMessage = this._sttErrorMessage('audio-capture');
                setTimeout(() => { this.errorMessage = null; }, 5000);
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

                const recorder = new MediaRecorder(stream);
                recorder.ondataavailable = (e) => {
                    if (e.data && e.data.size > 0) this._audioChunks.push(e.data);
                };
                recorder.onstop = () => this._compareMode
                    ? this._uploadCompareAudio(endpoint)
                    : this._uploadServerAudio(lang, endpoint);

                this._mediaRecorder = recorder;
                recorder.start();
                this.recording  = true;
                this.activeLang = lang;
            } catch (_) {
                this._releaseStream();
                this.errorMessage = this._sttErrorMessage('not-allowed');
                setTimeout(() => { this.errorMessage = null; }, 5000);
            }
        },

        /** Stops the recorder, which triggers onstop → upload. */
        _stopOpenAiRecording() {
            if (this._mediaRecorder && this._mediaRecorder.state !== 'inactive') {
                try { this._mediaRecorder.stop(); } catch (_) { /* ignored */ }
            }
            this.recording  = false;
            this.activeLang = null;
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
            this._releaseStream();
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
            const chunks = this._audioChunks || [];
            this._audioChunks = [];
            this._mediaRecorder = null;
            if (chunks.length === 0) return;

            const blob = new Blob(chunks, { type: 'audio/webm' });
            this.translating = true;
            this.processing  = true;   // show the progress bar across STT → translation
            try {
                const csrfToken  = document.querySelector('meta[name="_csrf"]')?.content || '';
                const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content || 'X-CSRF-TOKEN';

                const formData = new FormData();
                formData.append('audio', blob, 'speech.webm');

                const resp = await fetch(endpoint, {
                    method: 'POST',
                    headers: { [csrfHeader]: csrfToken },
                    body: formData,
                });
                if (!resp.ok) throw new Error('stt-failed');

                const data = await resp.json();
                const text = (data.corrected || data.raw || '').trim();
                if (text) {
                    // _submitTranscript opens the SSE stream which keeps `processing` true
                    // until `done`/`error`; only clear it here if we have nothing to submit.
                    this._submitTranscript(text, lang);
                } else {
                    this.processing = false;
                }
            } catch (_) {
                this.processing = false;
                this.errorMessage = this._sttErrorMessage('network');
                setTimeout(() => { this.errorMessage = null; }, 5000);
            } finally {
                this.translating = false;
            }
        },

        // ── Kinyarwanda A/B comparison ──────────────────────────────────────────

        /**
         * Uploads the recorded Kinyarwanda audio to the compare endpoint, then renders the two
         * cleaned candidates for the user to choose from (comparison mode).
         *
         * @param {string} endpoint - the compare transcription endpoint
         */
        async _uploadCompareAudio(endpoint) {
            this._releaseStream();
            const chunks = this._audioChunks || [];
            this._audioChunks = [];
            this._mediaRecorder = null;
            if (chunks.length === 0) return;

            const blob = new Blob(chunks, { type: 'audio/webm' });
            this.translating = true;
            this.processing  = true;
            try {
                const csrfToken  = document.querySelector('meta[name="_csrf"]')?.content || '';
                const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content || 'X-CSRF-TOKEN';

                const formData = new FormData();
                formData.append('audio', blob, 'speech.webm');

                const resp = await fetch(endpoint, {
                    method: 'POST',
                    headers: { [csrfHeader]: csrfToken },
                    body: formData,
                });
                if (!resp.ok) throw new Error('compare-failed');

                const data = await resp.json();
                if (data.candidates && data.candidates.length) {
                    this._renderComparison(data.candidates);
                }
            } catch (_) {
                this.errorMessage = this._sttErrorMessage('network');
                setTimeout(() => { this.errorMessage = null; }, 5000);
            } finally {
                this.translating = false;
                // Translation runs only after the user picks a candidate, so stop the bar now.
                this.processing = false;
            }
        },

        /**
         * Renders the A/B chooser: one card per candidate with its model label and cleaned text,
         * plus a "choose" button. Built in plain DOM (transient UI); labels come from
         * {@code window.tuganireMessages} so they stay internationalised.
         *
         * @param {Array<{modelId: string, label: string, text: string}>} candidates
         */
        _renderComparison(candidates) {
            const list = document.getElementById('conversation-list');
            if (!list) return;
            const msgs = window.tuganireMessages || {};

            const titleText = msgs.compareTitle || 'Quelle transcription est la meilleure ?';
            const chooseText = msgs.compareChoose || 'Choisir';

            const wrap = document.createElement('div');
            wrap.className = 'mb-4 rounded-lg border border-base-300 bg-base-100 p-3 animate-slide-in-up';
            wrap.setAttribute('role', 'group');
            wrap.setAttribute('aria-label', titleText);

            const title = document.createElement('p');
            title.className = 'text-xs font-medium text-base-content/60 mb-2';
            title.textContent = titleText;
            wrap.appendChild(title);

            candidates.forEach((cand, idx) => {
                const card = document.createElement('div');
                card.className = 'rounded-lg border border-base-300 p-3 mb-2';

                const lbl = document.createElement('p');
                lbl.className = 'text-xs font-medium text-accent/70 mb-1';
                lbl.textContent = cand.label;

                const txt = document.createElement('p');
                txt.className = 'text-sm text-base-content/80 mb-2 leading-relaxed';
                txt.textContent = cand.text;

                const btn = document.createElement('button');
                btn.type = 'button';
                btn.className = 'btn btn-primary btn-xs';
                btn.textContent = chooseText;
                btn.setAttribute('aria-label', `${chooseText} — ${cand.label}`);
                btn.addEventListener('click', () => {
                    const other = candidates[(idx + 1) % candidates.length];
                    this._chooseCandidate(wrap, cand, other);
                });

                card.appendChild(lbl);
                card.appendChild(txt);
                card.appendChild(btn);
                wrap.appendChild(card);
            });

            list.appendChild(wrap);
            this.scrollToBottom();
        },

        /**
         * Records the chosen model (best-effort) and submits the chosen text through the normal
         * translation flow.
         *
         * @param {HTMLElement} wrap - the chooser element to remove
         * @param {{modelId: string, text: string}} chosen - the picked candidate
         * @param {{modelId: string}} rejected - the other candidate
         */
        _chooseCandidate(wrap, chosen, rejected) {
            try {
                const csrfToken  = document.querySelector('meta[name="_csrf"]')?.content || '';
                const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content || 'X-CSRF-TOKEN';
                fetch('/api/v1/stt/compare-choice', {
                    method: 'POST',
                    headers: { [csrfHeader]: csrfToken, 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        chosenModel: chosen.modelId,
                        rejectedModel: rejected ? rejected.modelId : null,
                        sessionId: this.sessionId || null,
                    }),
                }).catch(() => { /* recording is best-effort */ });
            } catch (_) { /* best-effort */ }

            if (wrap && wrap.parentNode) {
                wrap.parentNode.removeChild(wrap);
            }
            this._submitTranscript(chosen.text, 'rw');
        },

        // ── transcript submission ───────────────────────────────────────────

        /**
         * Route a finalised transcript into the translation flow.
         *  - Two-button mode (the normal path): progressive SSE rendering via {@link _streamTranslate}.
         *  - Split-screen mode: the legacy server-rendered HTMX bubble, swapped into the active half.
         */
        _submitTranscript(transcript, lang) {
            const sourceLang = lang;
            const targetLang = lang === 'fr' ? 'rw' : 'fr';

            if (this.mode === 'splitScreen' && this._splitTarget) {
                // Split-screen: route to the correct half transcript list (server-rendered bubble).
                document.getElementById('split-text').value        = transcript;
                document.getElementById('split-session').value     = this.sessionId || '';
                document.getElementById('split-source-lang').value = sourceLang;
                document.getElementById('split-target-lang').value = targetLang;

                const form = document.getElementById('split-transcript-form');
                if (form) {
                    // Update HTMX target dynamically to the active panel
                    form.setAttribute('hx-target', `#${this._splitTarget}`);
                    htmx.process(form);
                    htmx.trigger(form, 'submit');
                }
            } else {
                // Two-button mode: progressive streaming via the SSE endpoint.
                this._streamTranslate(transcript, sourceLang, targetLang);
            }
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
        _streamTranslate(text, sourceLang, targetLang) {
            // Only one stream at a time — tear down any previous one.
            this._closeStream();

            const translationId = (crypto.randomUUID && crypto.randomUUID())
                || `t-${Date.now()}-${Math.random().toString(16).slice(2)}`;

            // Build the (initially empty) bubble and insert it into the conversation list.
            const refs = this._buildStreamingBubble(translationId, sourceLang, targetLang);
            if (!refs) return;

            this.bubbleCount++;
            this.processing  = true;
            this.translating = true;
            this.scrollToBottom();

            const params = new URLSearchParams({
                text,
                sourceLang,
                targetLang,
                sessionId: this.sessionId || '',
            });
            const es = new EventSource(`/api/v1/stream/translate?${params.toString()}`);
            this._eventSource = es;

            // 1. correction token → append to the SOURCE bubble
            es.addEventListener('correction', (e) => {
                const token = this._eventToken(e);
                if (token) {
                    refs.sourceText.textContent += token;
                    this.scrollToBottom();
                }
            });

            // 2. correction-done → set the final corrected source text
            es.addEventListener('correction-done', (e) => {
                const data = this._parseEvent(e);
                if (data && typeof data.text === 'string') {
                    refs.sourceText.textContent = data.text;
                    this.scrollToBottom();
                }
            });

            // 3. translation token → append to the TARGET bubble
            es.addEventListener('translation', (e) => {
                const token = this._eventToken(e);
                if (token) {
                    refs.targetText.textContent += token;
                    this.scrollToBottom();
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
                this.scrollToBottom();
            });

            // 5. done → close, hide progress bar, ensure controls are present
            es.addEventListener('done', () => {
                this._closeStream();
                this.processing  = false;
                this.translating = false;
                this.scrollToBottom();
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
            if (this._eventSource) {
                try { this._eventSource.close(); } catch (_) { /* ignored */ }
                this._eventSource = null;
            }
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
        _buildStreamingBubble(translationId, sourceLang, targetLang) {
            const list = document.getElementById('conversation-list');
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
            actionRow.className = 'flex items-center gap-2 pt-1 border-t border-base-300/50';

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
                targetLang,
                _controlsBuilt: false,
            };
        },

        /**
         * Attach a hidden <audio> element to the target bubble, auto-play it (pausing recognition
         * first, like the server-rendered bubble), and render the replay + feedback controls.
         */
        _attachAudio(refs, audioUrl) {
            const msgs = window.tuganireMessages || {};

            const audio = document.createElement('audio');
            audio.id = `audio-${refs.translationId}`;
            audio.src = audioUrl;
            audio.preload = 'none';
            audio.className = 'sr-only';
            audio.setAttribute('aria-label', msgs.replay || 'Rejouer');
            refs.targetBubble.insertBefore(audio, refs.actionRow);

            this._buildBubbleControls(refs, audio);

            // Pause recognition before playback so the mic doesn't capture the speaker output.
            this.$el.dispatchEvent(new CustomEvent('pause-recognition'));
            audio.play().catch(() => { /* autoplay may be blocked; replay button remains */ });
        },

        /** Render the replay button + feedback (👍/👎) controls into the bubble action row. */
        _buildBubbleControls(refs, audio) {
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
                + `<span class="text-xs"></span>`;
            replayBtn.lastElementChild.textContent = msgs.replay || 'Rejouer';
            replayBtn.addEventListener('click', () => {
                this.$el.dispatchEvent(new CustomEvent('pause-recognition'));
                audio.currentTime = 0;
                audio.play().catch(() => { /* ignored */ });
            });
            refs.actionRow.appendChild(replayBtn);

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

        /** Scroll the two-button conversation list to the bottom after a new bubble. */
        scrollToBottom() {
            const list = document.getElementById('conversation-list');
            if (list) {
                list.scrollTo({ top: list.scrollHeight, behavior: 'smooth' });
            }
        },

        /** Scroll the active split-screen transcript panel to the bottom. */
        scrollSplitToBottom() {
            if (this._splitTarget) {
                const list = document.getElementById(this._splitTarget);
                if (list) {
                    list.scrollTo({ top: list.scrollHeight, behavior: 'smooth' });
                }
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
