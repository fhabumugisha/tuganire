/**
 * settings.js — Alpine.js components for the Tuganire settings and onboarding screens.
 *
 * Responsibilities:
 *  settings():
 *    - Load TTS providers from GET /api/v1/providers.
 *    - Load translation models from GET /api/v1/settings/translation-model.
 *    - Load the GPT temperature from GET /api/v1/settings/temperature.
 *    - Auto-save every control (TTS provider, FR STT engine, temperature slider,
 *      translation model) on change — there is no batched submit button.
 *    - Slider saves are debounced and tagged with a monotonic request id so an
 *      out-of-order response cannot overwrite a fresher slider value (C11).
 *    - Feedback is exposed via a single `feedback` object carrying { field, type,
 *      message, ts } so each event re-renders aria-live regions and is announced
 *      again by assistive tech (C12).
 *    - Failed saves show a per-field error message AND revert the local Alpine
 *      state to the server's value (C13) so the UI doesn't lie.
 *
 *  onboarding():
 *    - Show the 3-step overlay only on first visit (localStorage flag).
 *    - Support skip (any step) and step-through navigation.
 *    - Mark as completed in localStorage so it never reappears.
 */

// ── i18n strings injected from the template via window.tuganireI18n ─────────
// (see settings.html — a small <script th:inline="javascript"> block sets this
//  object so we don't hardcode any user-facing copy here.)
function _t(key, fallback) {
    const dict = (typeof window !== 'undefined' && window.tuganireI18n) || {};
    return (dict[key] !== undefined && dict[key] !== null) ? dict[key] : (fallback || '');
}

// ── Settings component ─────────────────────────────────────────────────────────
function settings() {
    return {
        // ── state ────────────────────────────────────────────────────────────
        providers: [],
        selectedProvider: '',
        providerError: false,

        /**
         * French STT engine: 'webspeech' (on-device Web Speech API) or 'openai'
         * (server-side Whisper + LLM correction). Per-device, French-only; read
         * by conversation.js from the same localStorage key.
         */
        frenchStt: 'webspeech',

        /**
         * Kinyarwanda A/B comparison mode (per-device). When on, each Kinyarwanda utterance is
         * cleaned by two models and the user picks the better transcription. Read by conversation.js
         * from the same localStorage key.
         */
        compareRw: false,

        /**
         * GPT-4o translation temperature. Server-global (PUT /api/v1/settings/temperature),
         * loaded from the server in init() so the slider reflects the live value.
         */
        temperature: 0.3,

        /**
         * FR→RW translation model. Server-global (PUT /api/v1/settings/translation-model).
         * `translationModels` holds the selectable options ({ id, label, provider });
         * `selectedModel` is the active model id. `translationModelError` lets the
         * template render an inline error in place of the dropdown (C10).
         */
        translationModels: [],
        selectedModel: '',
        translationModelError: false,

        /**
         * Per-control feedback. Shape: { field, type: 'success' | 'error' | 'saving',
         * message, ts }. The `ts` is bumped on every event so :key bindings re-render
         * the aria-live region and screen readers re-announce (C12).
         * Success and error are mutually exclusive (writing one clears any timer
         * for the other on the same field).
         */
        feedback: { field: null, type: null, message: '', ts: 0 },

        // Internal: pending in-flight save per field (used to debounce / suppress
        // stale responses for the temperature slider). Not Alpine-reactive.
        _tempDebounceTimer: null,
        _tempRequestId: 0,
        _tempLatestRequestId: 0,
        _feedbackTimers: {},

        /** Active UI locale derived from the page's Accept-Language or URL param. */
        currentLocale: document.documentElement.lang || 'fr',

        // ── lifecycle ─────────────────────────────────────────────────────────

        init() {
            const savedStt = localStorage.getItem('tuganire.frenchStt');
            if (savedStt === 'webspeech' || savedStt === 'openai') {
                this.frenchStt = savedStt;
            }
            this.compareRw = localStorage.getItem('tuganire.compareRw') === '1';
            this.loadProviders();
            this.loadTemperature();
            this.loadTranslationModel();
        },

        // ── feedback helpers ────────────────────────────────────────────────

        /**
         * Records a feedback event for a field. Mutually exclusive: setting a new
         * event for a field clears any previously scheduled auto-dismiss timer for
         * the same field. Always bumps `ts` so :key re-renders and aria-live
         * re-announces.
         */
        _emitFeedback(field, type, message, autoDismissMs) {
            // Clear any previous auto-dismiss for this field
            if (this._feedbackTimers[field]) {
                clearTimeout(this._feedbackTimers[field]);
                this._feedbackTimers[field] = null;
            }
            this.feedback = {
                field,
                type,
                message: message || '',
                ts: Date.now() + Math.random(), // ensure unique even for back-to-back events
            };
            if (autoDismissMs && autoDismissMs > 0) {
                this._feedbackTimers[field] = setTimeout(() => {
                    // Only clear if this same field is still the active one
                    if (this.feedback.field === field) {
                        this.feedback = { field: null, type: null, message: '', ts: Date.now() };
                    }
                    this._feedbackTimers[field] = null;
                }, autoDismissMs);
            }
        },

        _success(field) {
            this._emitFeedback(field, 'success', _t('settings.saved.field', 'Saved'), 2000);
        },

        _saving(field) {
            this._emitFeedback(field, 'saving', _t('settings.saving', 'Saving…'), 0);
        },

        _error(field, key, fallback) {
            this._emitFeedback(field, 'error', _t(key, fallback), 5000);
        },

        /** True when feedback for a given field+type is the current event. */
        isFeedback(field, type) {
            return this.feedback.field === field && this.feedback.type === type;
        },

        // ── French STT engine ───────────────────────────────────────────────

        /**
         * Persists the French STT engine choice to localStorage (per-device).
         * No server round-trip: conversation.js reads the same key on load.
         */
        saveFrenchStt() {
            try {
                localStorage.setItem('tuganire.frenchStt', this.frenchStt);
                this._success('frenchStt');
            } catch (_) {
                this._error('frenchStt', 'settings.saveError.frenchStt',
                    'Failed to save the French transcription engine.');
            }
        },

        /**
         * Persists the Kinyarwanda A/B comparison-mode toggle to localStorage (per-device).
         * No server round-trip: conversation.js reads the same key on load.
         */
        saveCompareRw() {
            try {
                localStorage.setItem('tuganire.compareRw', this.compareRw ? '1' : '0');
                this._success('compareRw');
            } catch (_) {
                this._error('compareRw', 'settings.saveError.compareRw',
                    'Failed to save the comparison mode.');
            }
        },

        // ── translation temperature ─────────────────────────────────────────

        /** Loads the current GPT-4o translation temperature from the server. */
        async loadTemperature() {
            try {
                const resp = await fetch('/api/v1/settings/temperature', {
                    headers: { Accept: 'application/json' },
                });
                if (!resp.ok) return;
                const data = await resp.json();
                if (typeof data.temperature === 'number') {
                    this.temperature = data.temperature;
                }
            } catch (_) {
                // Non-fatal: keep the default slider value.
            }
        },

        /**
         * C11 — debounce + stale-response suppression for the slider.
         * Call from @input on the range (binds on every drag step). The PUT only
         * fires ~400 ms after the LAST event. Each in-flight request carries a
         * monotonic id; responses whose id is not the latest are discarded so a
         * laggy older response can't overwrite a fresher local value.
         */
        saveTemperature() {
            if (this._tempDebounceTimer) {
                clearTimeout(this._tempDebounceTimer);
            }
            // Show the inline "saving…" pill immediately so the user sees
            // their drag is being captured.
            this._saving('temperature');
            this._tempDebounceTimer = setTimeout(() => {
                this._tempDebounceTimer = null;
                this._performSaveTemperature();
            }, 400);
        },

        async _performSaveTemperature() {
            const requestId = ++this._tempRequestId;
            this._tempLatestRequestId = requestId;
            try {
                const csrfToken  = document.querySelector('meta[name="_csrf"]')?.content || '';
                const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content || 'X-CSRF-TOKEN';

                const resp = await fetch('/api/v1/settings/temperature', {
                    method: 'PUT',
                    headers: {
                        'Content-Type': 'application/json',
                        [csrfHeader]: csrfToken,
                    },
                    body: JSON.stringify({ temperature: this.temperature }),
                });

                // Stale-response guard: discard if a newer request has been kicked off.
                if (requestId !== this._tempLatestRequestId) return;

                if (resp.ok) {
                    const data = await resp.json();
                    if (requestId !== this._tempLatestRequestId) return;
                    if (typeof data.temperature === 'number') {
                        this.temperature = data.temperature;
                    }
                    this._success('temperature');
                } else {
                    this._error('temperature', 'settings.saveError.temperature',
                        'Failed to save the temperature.');
                    // Revert to server value (C13)
                    this.loadTemperature();
                }
            } catch (_) {
                if (requestId !== this._tempLatestRequestId) return;
                this._error('temperature', 'settings.saveError.temperature',
                    'Failed to save the temperature.');
                this.loadTemperature();
            }
        },

        // ── translation model ───────────────────────────────────────────────

        /** Loads the active FR→RW model and the selectable options from the server. */
        async loadTranslationModel() {
            try {
                const resp = await fetch('/api/v1/settings/translation-model', {
                    headers: { Accept: 'application/json' },
                });
                if (!resp.ok) {
                    this.translationModelError = true;
                    return;
                }
                const data = await resp.json();
                if (Array.isArray(data.available)) {
                    this.translationModels = data.available;
                }
                if (typeof data.model === 'string') {
                    this.selectedModel = data.model;
                }
                // Reset the error state on a successful load (in case we're retrying).
                this.translationModelError = false;
            } catch (_) {
                this.translationModelError = true;
            }
        },

        /**
         * PUT /api/v1/settings/translation-model with the chosen model id (server-global).
         * On failure: revert to the server's value (C13).
         */
        async saveTranslationModel() {
            if (!this.selectedModel) return;
            this._saving('model');
            try {
                const csrfToken  = document.querySelector('meta[name="_csrf"]')?.content || '';
                const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content || 'X-CSRF-TOKEN';

                const resp = await fetch('/api/v1/settings/translation-model', {
                    method: 'PUT',
                    headers: {
                        'Content-Type': 'application/json',
                        [csrfHeader]: csrfToken,
                    },
                    body: JSON.stringify({ model: this.selectedModel }),
                });

                if (resp.ok) {
                    const data = await resp.json();
                    if (typeof data.model === 'string') {
                        this.selectedModel = data.model;
                    }
                    this._success('model');
                } else {
                    this._error('model', 'settings.saveError.model',
                        'Failed to save the translation model.');
                    this.loadTranslationModel();
                }
            } catch (_) {
                this._error('model', 'settings.saveError.model',
                    'Failed to save the translation model.');
                this.loadTranslationModel();
            }
        },

        // ── provider loading + TTS save ───────────────────────────────────────

        /**
         * Fetches GET /api/v1/providers and populates the <select>.
         *
         * The endpoint (SessionController) returns an object:
         *   { "ttsProviders": ["elevenlabs","mms","openai"], ...,
         *     "activeTtsProvider": "elevenlabs" }
         */
        async loadProviders() {
            try {
                const resp = await fetch('/api/v1/providers', {
                    headers: { Accept: 'application/json' },
                });
                if (!resp.ok) {
                    this.providerError = true;
                    return;
                }
                const data = await resp.json();
                const names = Array.isArray(data.ttsProviders) ? data.ttsProviders : [];
                if (names.length === 0) {
                    this.providerError = true;
                    return;
                }
                this.providers = names.map((id) => ({
                    id,
                    name: id.charAt(0).toUpperCase() + id.slice(1),
                }));
                this.selectedProvider = data.activeTtsProvider && names.includes(data.activeTtsProvider)
                    ? data.activeTtsProvider
                    : names[0];
            } catch (_) {
                this.providerError = true;
            }
        },

        /**
         * B6 — auto-save the TTS provider on @change (no batched submit button).
         * PUT /api/v1/providers/tts with the selected provider ID and CSRF header.
         */
        async saveTtsProvider() {
            if (!this.selectedProvider) return;
            this._saving('tts');
            try {
                const csrfToken  = document.querySelector('meta[name="_csrf"]')?.content || '';
                const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content || 'X-CSRF-TOKEN';

                const resp = await fetch('/api/v1/providers/tts', {
                    method: 'PUT',
                    headers: {
                        'Content-Type': 'application/json',
                        [csrfHeader]: csrfToken,
                    },
                    // Endpoint (SessionController.TtsProviderSwitchRequest) expects { providerName }.
                    body: JSON.stringify({ providerName: this.selectedProvider }),
                });

                if (resp.ok) {
                    this._success('tts');
                } else {
                    this._error('tts', 'settings.saveError.tts',
                        'Failed to save the text-to-speech provider.');
                    this.loadProviders();
                }
            } catch (_) {
                this._error('tts', 'settings.saveError.tts',
                    'Failed to save the text-to-speech provider.');
                this.loadProviders();
            }
        },
    };
}

// ── Onboarding component ───────────────────────────────────────────────────────
function onboarding() {
    return {
        // ── state ─────────────────────────────────────────────────────────────
        visible: false,
        step: 1,
        totalSteps: 3,

        // ── lifecycle ─────────────────────────────────────────────────────────

        init() {
            // Show only if the user has never completed or skipped onboarding
            const done = localStorage.getItem('tuganire.onboarding.done');
            this.visible = !done;
        },

        // ── navigation ────────────────────────────────────────────────────────

        next() {
            if (this.step < this.totalSteps) {
                this.step++;
            } else {
                this._complete();
            }
        },

        skip() {
            this._complete();
        },

        _complete() {
            localStorage.setItem('tuganire.onboarding.done', '1');
            this.visible = false;
        },
    };
}
