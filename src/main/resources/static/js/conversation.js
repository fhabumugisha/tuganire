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
        activeLang: null,        // 'fr' | 'rw' | null
        speechSupported: false,
        sessionId: null,
        bubbleCount: 0,
        errorMessage: null,

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
                    this._submitTranscript(text, lang);
                }
            } catch (_) {
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
         * Populate the appropriate hidden HTMX form and trigger submission.
         * In two-button mode: uses #transcript-form, targeting #conversation-list.
         * In split-screen mode: uses #split-transcript-form, targeting the side list.
         */
        _submitTranscript(transcript, lang) {
            const sourceLang = lang;
            const targetLang = lang === 'fr' ? 'rw' : 'fr';

            if (this.mode === 'splitScreen' && this._splitTarget) {
                // Split-screen: route to the correct half transcript list
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
                // Two-button mode: standard flow
                document.getElementById('transcript-text').value        = transcript;
                document.getElementById('transcript-session').value     = this.sessionId || '';
                document.getElementById('transcript-source-lang').value = sourceLang;
                document.getElementById('transcript-target-lang').value = targetLang;

                this.bubbleCount++;
                const form = document.getElementById('transcript-form');
                if (form) {
                    htmx.trigger(form, 'submit');
                }
            }
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
