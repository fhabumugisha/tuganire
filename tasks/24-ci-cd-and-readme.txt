TASK 24 — CI/CD Pipeline, Docker Image and README

PROJECT CONTEXT

Tuganire backend, package com.tuganire. Conventions (CLAUDE.md): CI runs ./mvnw -B -ntp clean verify on every push/PR; Docker image built via Jib (./mvnw compile jib:dockerBuild). Spotless/Checkstyle at validate, SpotBugs/FindSecBugs at verify. Run ./mvnw spotless:apply before finishing.

ARCHITECTURE

See ARCHI.md section 9 (GitHub Actions pipeline: JDK 21 Temurin, mvn clean verify, build/push image to GHCR, deploy to Cloud Run europe-west1) and section 14 (deliverables: public repo, README, OpenAPI/Swagger, demo). Infra targets: Cloud Run backend, Cloud SQL Postgres, Upstash Redis, Hetzner GPU for the Python MMS-TTS server. The Python server (Task 02) has its own Dockerfile already.

TECH STACK AND BEST PRACTICES

GitHub Actions (actions/checkout@v4, actions/setup-java@v4 java 21 temurin, cache maven). Use Jib for the backend image (already configured in pom by Task 01) OR spring-boot:build-image — prefer Jib per CLAUDE.md. Push to GitHub Container Registry (ghcr.io). Deploy step to Cloud Run gated on main. Pass OPENAI_API_KEY_TEST as a secret for the verify step (ITs mock the network, but keep the env wiring). Document all environment variables (PRD/ARCHI list: DATABASE_*, REDIS_URL, OPENAI_API_KEY, ANTHROPIC_API_KEY, MMS_TTS_URL, ELEVENLABS_API_KEY, rate-limit, etc.).

CONTEXT

This makes Tuganire shippable and portfolio-ready (PRD Sprint 5 / acceptance criteria: public GitHub repo with complete README + demo; ARCHI deliverables). It packages the backend, wires CI, and writes the README that ties the three components (backend, Python TTS, Android) together. Depends on a working backend (Task 18) and the web POC (Task 20).

DEPENDENCIES

Requires Task 18 (REST API) and Task 20 (web POC). Benefits from Task 22 (tests) being green but does not edit test files.

PARALLEL GROUP

Group H (Polish & ship), Wave 8. Runs in parallel with Task 23 — distinct files (.github/, Dockerfile, README; Task 23 edits templates/static).

WHAT TO IMPLEMENT

File: .github/workflows/ci.yml (or backend.yml)
Purpose: On push/PR to main: checkout, setup JDK 21 Temurin with Maven cache, run ./mvnw -B -ntp clean verify. On main only: build the image via Jib to ghcr.io/<repo>/backend:<sha>, push to GHCR, deploy to Cloud Run (europe-west1) using google-github-actions/deploy-cloudrun@v2. Wire OPENAI_API_KEY_TEST and other secrets.

File: Dockerfile (backend) — optional if Jib is the chosen path
Purpose: Only add a Dockerfile if not relying solely on Jib; otherwise document the Jib command. Do not duplicate image-build mechanisms.

File: README.md (root)
Purpose: Project overview (the Tuganire pitch from the PRD), architecture summary (link ARCHI.md), the three components (backend / tts-server / android), prerequisites, local run (docker-compose up -d; ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev; npm run watch; uvicorn for the TTS server), the full environment-variable table, the REST/WebSocket API summary (link Swagger UI), and the "Forking This Template" note removal/replacement since this is now Tuganire (not the SaaS template).

File: docs/ (optional) — link the existing PRD.md and ARCHI.md
Purpose: Ensure README references docs/PRD.md and docs/ARCHI.md.

ACCEPTANCE CRITERIA

1. .github/workflows/ci.yml exists and its verify job runs ./mvnw -B -ntp clean verify on push/PR.
2. The workflow builds and pushes a backend image to GHCR only on main, and has a Cloud Run deploy step gated on main.
3. ./mvnw compile jib:dockerBuild builds a backend image locally (or the documented build command works).
4. README.md documents the three components, local-run steps, the complete environment-variable table, and links PRD.md / ARCHI.md and the Swagger UI path.
5. README no longer describes the project as the generic SaaS template; it describes Tuganire.
6. The workflow YAML is valid (parses) and uses JDK 21 Temurin with Maven caching.

NOTES

ITs mock external APIs (Task 22), so CI does not require real OpenAI credentials — but keep the secret wiring so the same workflow works if live smoke tests are added. Prefer Jib (per CLAUDE.md) over spring-boot:build-image; do not configure both. The Python MMS-TTS server already has its own Dockerfile (Task 02); reference it in the README but a separate workflow for it is optional/Phase 2.
