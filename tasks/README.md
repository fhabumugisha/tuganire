# Tuganire MVP — Task Breakdown

Generated from `docs/PRD.md` (v1.1) and `docs/ARCHI.md` (v1.1) by the `prd-to-tasks` skill.

Tuganire is a French ↔ Kinyarwanda conversational voice-translation app: a Spring Boot 4 backend (LLM translation + native correction layer + STT/TTS), a Python MMS-TTS server, a Thymeleaf/HTMX web POC, and a Kotlin/Compose Android app.

**Phased delivery: Web MVP first (22 tasks, 8 waves), then Android (2 tasks) once the web version is validated.** Peak concurrency: 6 agents (Wave 4). Recommended team size: 6.

WEB-FIRST: Tasks 03 (android-skeleton) and 21 (android-conversation-screen) are deferred to Phase 2. Do NOT run them until the web POC is built, deployed and validated. Everything else (backend + Python TTS + web POC) is Phase 1.

Each `NN-*.txt` is a self-contained prompt for an implementer agent. The two UI tasks (20, 23) additionally require the `ui-ux-pro-max` skill (design), the `frontend-design` skill (HTML), and review against `rules/thymeleaf-rules.mdc`.

## Overview

| # | Task | Priority | Est. min | Depends on | Wave / Group |
|---|------|----------|----------|------------|--------------|
| 01 | backend-build-config | P0 | 45-60 | — | W1 / A |
| 02 | python-mms-tts-server | P0 | 40-60 | — | W1 / A |
| 03 | android-skeleton | P1 | 30-45 | — | PHASE 2 (Android) |
| 04 | dtos-and-events | P0 | 25-40 | 01 | W2 / B |
| 05 | flyway-and-jpa-entities | P0 | 30-50 | 01 | W2 / B |
| 06 | i18n-messages | P0 | 20-30 | 01 | W2 / B |
| 07 | provider-and-rule-interfaces | P0 | 30-45 | 04 | W3 / C |
| 08 | redis-translation-cache | P1 | 30-45 | 04 | W3 / C |
| 09 | feedback-service | P1 | 25-40 | 05 | W3 / C |
| 10 | llm-providers-and-prompt | P0 | 50-60 | 07 | W4 / D |
| 11 | french-normalizer-and-golden-dictionary | P0 | 40-55 | 05, 07 | W4 / D |
| 12 | kinyarwanda-postprocessor-rules | P0 | 50-60 | 07 | W4 / D |
| 13 | tts-providers-and-service | P0 | 45-60 | 07 | W4 / D |
| 14 | stt-provider-and-service | P0 | 40-55 | 07 | W4 / D |
| 15 | security-cors-ratelimit | P1 | 40-55 | 01, 08 | W4 / D |
| 16 | translation-service-orchestrator | P0 | 40-55 | 08, 10, 11, 12 | W5 / E |
| 17 | conversation-service-audio-pipeline | P0 | 50-60 | 13, 14, 16 | W6 / F |
| 18 | rest-controllers-and-exception-handling | P0 | 45-60 | 09, 16 | W6 / F |
| 19 | websocket-streaming-handler | P1 | 45-60 | 17 | W7 / G |
| 20 | web-poc-deux-boutons | P0 | 55-60 | 06, 18 | W7 / G |
| 21 | android-conversation-screen | P1 | 55-60 | 03, 18 | PHASE 2 (Android) |
| 22 | tests-unit-and-integration | P0 | 55-60 | 12, 16, 18 | W7 / G |
| 23 | web-poc-splitscreen-and-settings | P1 | 45-60 | 20 | W8 / H |
| 24 | ci-cd-and-readme | P1 | 40-55 | 18, 20 | W8 / H |

## Dependency graph

PHASE 1 — WEB MVP (build, validate, deploy first):

```
Wave 1:  [01-build]      [02-python-tts]
            |  \  \
            |   \  `------------------------.
            |    `------------.              \
Wave 2:  [04-dtos]        [05-flyway]   [06-i18n]
            |  \             |  \            |
            |   \            |   \           |
Wave 3:  [07-ifaces]    [08-cache]    [09-feedback]
            | \  \  \  \      |              |
            |  \  \  \  `-----+--.           |
Wave 4:  [10-llm] [11-norm/golden] [12-postproc] [13-tts] [14-stt] [15-security]
              \        |               |            \       (08->15)
               `----.  |  .------------'             \
Wave 5:           [16-translation-service]  (08,10,11,12)
                         |        \
Wave 6:        [17-conversation-pipeline]   [18-rest-controllers]   (16; 09)
                    |    \                        |     \
Wave 7:  [19-websocket]   [20-web-deux-boutons]        [22-tests]
                              |
Wave 8:        [23-web-splitscreen]        [24-ci-cd-readme]
```

PHASE 2 — ANDROID (only after the web version is validated):

```
[03-android-skeleton]  ->  [21-android-conversation-screen]   (21 also needs 18 from Phase 1)
```

### Phase 1 — Web MVP

- **Wave 1 (2 agents):** 01, 02 — backend build + Python TTS server (two independent stacks).
- **Wave 2 (3 agents):** 04, 05, 06 — contracts, schema, i18n. All depend on 01 only.
- **Wave 3 (3 agents):** 07, 08, 09 — seams (interfaces), cache, feedback service.
- **Wave 4 (6 agents — peak):** 10, 11, 12, 13, 14, 15 — pipeline components, each in its own feature package.
- **Wave 5 (1 agent):** 16 — the orchestrator that converges Wave 4.
- **Wave 6 (2 agents):** 17, 18 — application services and the public API.
- **Wave 7 (3 agents):** 19, 20, 22 — delivery surfaces (WebSocket, web POC, tests).
- **Wave 8 (2 agents):** 23, 24 — polish (split-screen/settings) and ship (CI/CD + README).

At the end of Phase 1: a deployed, shareable web POC (PRD Sprint 3 demo) for community validation.

### Phase 2 — Android (after web validation)

- **A1 (1 agent):** 03 — Android project skeleton.
- **A2 (1 agent):** 21 — Android conversation screen (reuses the validated REST API from Task 18).

No two tasks in the same wave modify the same file (see FILE OWNERSHIP MAP in `TEAM-PROMPT.txt`).

## How to execute

- **Agent Teams / APEX:** run `/apex -a -m` (or your team coordinator) pointed at `tasks/TEAM-PROMPT.txt`. The coordinator spawns one implementer per task per wave, waits for each wave to complete, then advances.
- **Manual:** implement waves in order; within a wave, work tasks in parallel. Run `./mvnw -B -ntp clean verify` after each backend wave to match CI.
- **UI tasks (20, 23):** use `ui-ux-pro-max` for design, `frontend-design` for the HTML, and review against `rules/thymeleaf-rules.mdc`.

## Key risks surfaced during planning

- **OpenAI `whisper-1` does NOT support Kinyarwanda** — Task 14 makes this explicit and keeps STT provider-pluggable (fallbacks: `mbazaNLP/Whisper-Small-Kinyarwanda` or Meta MMS ASR). PRD already flags this 🟡 for Sprint 2.
- **Spring AI 2.0 is a milestone (M6 latest, not GA)** — pin the newest milestone; keep Spring AI types confined to provider impls (ADR-008) so the M→GA migration is contained.
- **Virtual threads** — never use `synchronized` (pins carrier threads); use `ReentrantLock`.
- **Jackson 3** — imports are `tools.jackson.*`, not `com.fasterxml.jackson.*`.
