# Tuganire — French ↔ Kinyarwanda Voice Translator

> **Tuganire** is a conversational translation app for French ↔ Kinyarwanda, designed by a native Rwandan speaker who understands the cultural and linguistic nuances that Google Translate ignores.

**"Tuganire"** means *"Let's talk"* in Kinyarwanda.

## Why Tuganire?

When a French-speaking traveller visits Rwanda, existing tools fall short:

- Google Translate Live Conversation does not support Kinyarwanda in real time
- Mainstream TTS synthesis for Kinyarwanda is absent or unusable
- GPT-4o and Claude produce structurally flawed translations across 5 documented grammatical error categories
- No tool respects Rwandan cultural codes (politeness registers, market negotiation, code-switching)

Tuganire combines a GPT-4o LLM for base translation, a native post-processing layer (5 correction rules + a golden dictionary), and purpose-built voice models (Whisper STT + Proto.cx / MMS-TTS for Kinyarwanda audio).

## Architecture

Three components work together:

| Component | Technology | Role |
|-----------|-----------|------|
| **Backend** | Spring Boot 4.0 + Java 21 + Spring AI 2.0 | REST/WebSocket API, translation pipeline, TTS/STT orchestration |
| **TTS server** | Python FastAPI + `facebook/mms-tts-kin` / `facebook/mms-tts-fra` | Kinyarwanda and French voice synthesis on a GPU |
| **Web POC** | Thymeleaf + Tailwind CSS v4 + HTMX + Alpine.js | Browser demo, community validation |
| **Android** (Phase 2) | Kotlin Jetpack Compose | Mobile app (not in this repo) |

The backend is the single source of truth: it serves the web POC and the future Android app from the same REST/WebSocket endpoints.

Full architecture details: [docs/ARCHI.md](docs/ARCHI.md)
Product requirements: [docs/PRD.md](docs/PRD.md)

### Backend package structure

```
com.tuganire/
├── conversation/    ConversationService, WebSocket handler, ConversationEvent (sealed interface)
├── translation/     TranslationService, FrenchNormalizer, GoldenDictionaryService
├── llm/             LlmProvider interface, OpenAI + Claude implementations, ProviderFactory
├── postprocessor/   KinyarwandaPostProcessor, 5 CorrectionRule implementations
├── tts/             TtsProvider interface, Proto.cx + MMS-TTS implementations
├── stt/             SttProvider interface, Whisper implementation
├── feedback/        FeedbackService, Feedback entity, FeedbackRepo
├── cache/           Redis-backed TranslationCache
├── web/             Thymeleaf WebController (web POC)
└── shared/          Security, CORS, rate-limiting, exception handling
```

## Prerequisites

- Java 21 (Eclipse Temurin recommended)
- Maven (wrapper `./mvnw` included)
- Docker and Docker Compose (for PostgreSQL + Redis)
- Node.js 20+ and npm (for CSS rebuild)
- Python 3.11+ with `uv` (for the TTS server, optional in dev)

## Local Setup

### 1. Start the databases

```bash
docker-compose up -d
```

This starts PostgreSQL 16 on `localhost:5432` and Redis 7 on `localhost:6379`.

### 2. Set environment variables

Create a `.env` file or export the variables listed in the [Environment Variables](#environment-variables) section below. Minimum required for dev:

```bash
export OPENAI_API_KEY=sk-...
export ANTHROPIC_API_KEY=sk-ant-...
```

All other variables have sensible defaults for local development (see `application-dev.yml`).

### 3. Build the CSS

```bash
npm install
npm run watch   # rebuild on save during development
# or
npm run build-prod  # one-shot minified build
```

The Maven `frontend-maven-plugin` rebuilds CSS automatically during `./mvnw package`, so a separate `npm run build-prod` is only needed when packaging without Maven.

### 4. Run the backend

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

The app starts on [http://localhost:8080](http://localhost:8080).

### 5. Run the TTS server (optional)

Kinyarwanda voice synthesis uses the Proto.cx native voice by default. The local MMS-TTS server is the fallback (used when Proto is unconfigured or fails) and also handles French. When `MMS_TTS_URL` points to a running server the backend uses it.

```bash
cd tts-server
brew install uv       # macOS; or: pip install uv
uv venv && source .venv/bin/activate
uv pip install -r requirements.txt
uvicorn tts_server:app --host 0.0.0.0 --port 8000
```

For GPU production deployment on Hetzner:

```bash
docker build -t tuganire/tts:latest .
docker run --gpus all -p 8000:8000 tuganire/tts:latest
```

## API

The backend exposes:

- REST API at `/api/v1/` — translate, audio/translate, feedback, sessions, languages, providers
- WebSocket at `/ws/conversation/{sessionId}` — real-time audio streaming pipeline
- Swagger UI at [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- Health check at `/actuator/health`

See [docs/ARCHI.md §6](docs/ARCHI.md) for the full endpoint list and WebSocket message protocol.

## Environment Variables

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `OPENAI_API_KEY` | Yes | — | OpenAI API key (GPT-4o translation, Whisper STT) |
| `ANTHROPIC_API_KEY` | No | — | Anthropic API key (Claude fallback LLM) |
| `DATABASE_URL` | Yes | `jdbc:postgresql://localhost:5432/tuganire` | PostgreSQL JDBC URL |
| `DATABASE_USER` | Yes | `tuganire` | PostgreSQL username |
| `DATABASE_PASSWORD` | Yes | `tuganire` | PostgreSQL password |
| `REDIS_URL` | Yes | `redis://localhost:6379` | Redis connection URL |
| `MMS_TTS_URL` | No | `http://localhost:8000` | Python MMS-TTS server URL (fallback voice + French) |
| `BASE_URL` | No | `http://localhost:8080` | Public base URL (used in emails/links) |
| `PROTO_TTS_URL` | No | `https://v3-api.proto.cx/api` | Proto.cx Voice API base URL (native Kinyarwanda TTS) |
| `PROTO_TTS_SUBCOMPANY_ID` | No | — | Proto.cx teamspace id; blank → MMS fallback |
| `PROTO_TTS_TOKEN` | No | — | Proto.cx API token; blank → MMS fallback |
| `PROTO_TTS_GENDER` | No | `female` | Proto.cx voice gender (`female`, `male`) |
| `KINY_TTS_PROVIDER` | No | `proto` | TTS provider for Kinyarwanda (`proto`, `mms`) |
| `TUGANIRE_LLM_DEFAULT_PROVIDER` | No | `openai` | Default LLM provider (`openai`, `anthropic`) |
| `TUGANIRE_RATE_LIMIT_RPM` | No | `60` | Rate limit: requests per minute per IP |
| `TUGANIRE_RATE_LIMIT_RPD` | No | `1000` | Rate limit: requests per day per IP |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | No | — | OpenTelemetry collector endpoint (optional) |

### GitHub Actions secrets required for CI/CD

| Secret | Used in | Description |
|--------|---------|-------------|
| `OPENAI_API_KEY_TEST` | `verify` job | OpenAI key for CI (ITs mock the network; key is wired for live smoke tests) |
| `ANTHROPIC_API_KEY_TEST` | `verify` job | Anthropic key for CI |
| `OPENAI_API_KEY` | `deploy` job | Production OpenAI key |
| `ANTHROPIC_API_KEY` | `deploy` job | Production Anthropic key |
| `DATABASE_URL` | `deploy` job | Production database JDBC URL |
| `DATABASE_USER` | `deploy` job | Production database username |
| `DATABASE_PASSWORD` | `deploy` job | Production database password |
| `REDIS_URL` | `deploy` job | Production Redis URL (e.g. Upstash `rediss://...`) |
| `MMS_TTS_URL` | `deploy` job | Production MMS-TTS server URL |
| `BASE_URL` | `deploy` job | Production base URL |
| `GCP_WORKLOAD_IDENTITY_PROVIDER` | `deploy` job | GCP Workload Identity Federation provider resource name |
| `GCP_SERVICE_ACCOUNT` | `deploy` job | GCP service account email for Cloud Run deployment |

## CI/CD

The pipeline in `.github/workflows/ci.yml` has three jobs:

1. **`build` (every push/PR)** — runs `./mvnw -B -ntp clean verify` on JDK 21 Temurin, including Spotless/Checkstyle, SpotBugs/FindSecBugs, all unit tests (Surefire), all integration tests (Failsafe / Testcontainers), and JaCoCo coverage.

2. **`publish` (main only)** — builds the backend Docker image via Jib and pushes it to GitHub Container Registry (`ghcr.io/<repo>/backend:<sha>` and `:latest`). No separate Dockerfile needed; Jib builds from source using `eclipse-temurin:21-jre-alpine` as base.

3. **`deploy` (main only)** — deploys to Cloud Run in `europe-west1` using Workload Identity Federation (no long-lived GCP service account keys in secrets).

Local image build (no Docker daemon required):

```bash
./mvnw compile jib:dockerBuild   # builds to local Docker daemon as tuganire:latest
```

Remote push (requires GHCR login or Jib credentials):

```bash
./mvnw compile jib:build -Djib.to.image=ghcr.io/<org>/tuganire/backend:dev
```

## Running Tests

```bash
# Unit tests only (fast)
./mvnw test

# Full pipeline: unit + IT + static analysis + coverage
./mvnw verify

# Single test class
./mvnw test -Dtest=TranslationServiceTest

# Single integration test
./mvnw verify -Dit.test=EndToEndIT
```

Integration tests use Testcontainers and spin up their own PostgreSQL and Redis containers — no local Docker configuration needed beyond having Docker running.

## Development Commands

```bash
# Run app (dev profile)
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# Apply code formatter (run before committing)
./mvnw spotless:apply

# Build production JAR
./mvnw clean package -DskipTests

# Build Docker image locally via Jib
./mvnw compile jib:dockerBuild

# Watch CSS changes
npm run watch

# Minified production CSS
npm run build-prod
```

## Database Migrations

Flyway manages schema evolution. Migrations live in `src/main/resources/db/migration/`. Current migrations:

| Migration | Description |
|-----------|-------------|
| V001 | `golden_dictionary` table (pre-validated translations) |
| V002 | `feedback` table (user thumbs-up/down + corrections) |
| V003–V009 | Auth, payment, blog, subscriptions (from base template) |

Add new feature migrations starting at the next available version number.

## Tech Stack

| Layer | Technology |
|-------|-----------|
| **Java** | Java 21 LTS (records, virtual threads, pattern matching, sealed interfaces) |
| **Framework** | Spring Boot 4.0.6, Spring Framework 7, Spring AI 2.0-M6 |
| **API** | Spring MVC + WebSocket, SpringDoc OpenAPI (Swagger UI) |
| **Persistence** | Spring Data JPA + Hibernate 6, Flyway, PostgreSQL 16 |
| **Cache** | Spring Data Redis (Lettuce), Redis 7 |
| **AI** | Spring AI 2.0 — OpenAI (GPT-4o + Whisper), Anthropic (Claude fallback) |
| **TTS** | Proto.cx native Kinyarwanda voice (default); `facebook/mms-tts-kin` / `facebook/mms-tts-fra` via FastAPI (fallback + French) |
| **Web POC** | Thymeleaf, Tailwind CSS v4, DaisyUI, HTMX, Alpine.js |
| **Security** | Spring Security, Bucket4j rate-limiting, CORS whitelist, CSRF |
| **Observability** | Spring Boot Actuator, Micrometer, OpenTelemetry (Spring Boot 4 native) |
| **Build** | Maven 3.9, Jib 3.4, frontend-maven-plugin |
| **Tests** | JUnit 5, Mockito, AssertJ, Testcontainers, WireMock, JaCoCo |
| **Code quality** | Spotless, Checkstyle, SpotBugs + FindSecBugs |

## Infrastructure

| Service | Provider | Notes |
|---------|---------|-------|
| Backend | Google Cloud Run (europe-west1) | Scale to zero |
| Database | Cloud SQL PostgreSQL 16 | Managed, auto-backups |
| Cache | Upstash Redis | Free tier |
| TTS GPU | Hetzner GEX44 (RTX 4000 SFF Ada) | On-demand |
| CI/CD | GitHub Actions | Free for public repo |
| Container registry | GitHub Container Registry (ghcr.io) | Integrated with GHA |
| DNS | Cloudflare | DDoS protection |
