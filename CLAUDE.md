# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

`tuganire` — a Spring Boot 3.5 + Java 21 SaaS starter (`com.tuganire`). Forked apps rename the base package; see README "Forking This Template".

## Commands

```bash
# Database (PostgreSQL 16 + pgvector)
docker-compose up -d

# Run app (dev profile = application-dev.yml overrides on top of application.yml)
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# Unit tests (Surefire)
./mvnw test
./mvnw test -Dtest=ClassNameTest
./mvnw test -Dtest=ClassNameTest#methodName

# Integration tests (Failsafe, *IT classes; Testcontainers Postgres)
./mvnw verify          # runs full pipeline: unit + IT + SpotBugs/FindSecBugs + JaCoCo

# Build / package
./mvnw clean package -DskipTests
./mvnw compile jib:dockerBuild     # Docker image via Jib

# Frontend (Tailwind v4 + DaisyUI via PostCSS)
npm run watch        # rebuild CSS on save
npm run build-prod   # minified, production

# Apply code formatter (Spotless / Eclipse formatter)
./mvnw spotless:apply
```

**Note:** Spotless and Checkstyle run at the `validate` phase, so **every** Maven build (including `compile`) fails on style violations. SpotBugs + FindSecBugs run at `verify`. The Maven `frontend-maven-plugin` rebuilds CSS during `generate-resources`, so `./mvnw package` produces a self-contained jar without a separate `npm run build`. CI (`.github/workflows/ci.yml`) runs `./mvnw -B -ntp clean verify` on every push/PR — match it locally before pushing.

## Architecture

Feature-driven packages under `com.tuganire`. Each feature owns its `controller / service / repository / model / dto / mapper` layers. Cross-cutting code lives in `shared/` and `common/`.

```
com.tuganire/
├── auth/          Registration, login, profile, password reset, email verification, OAuth2/OIDC social login (custom UserDetailsService)
├── payment/       Stripe subscriptions, webhooks, billing portal, plan-based access (FREE/PREMIUM)
├── blog/          Public blog + admin Markdown editor (EasyMDE) + AI excerpt/cover generation
├── admin/         /admin dashboard: user mgmt + LLM-usage analytics (LlmUsageTracker writes to llm_usage_events)
├── storage/       StorageService + swappable S3/Local impls + /files/** controller (see below)
├── common/        AI model cost properties (configurable in application.yml under tuganire.ai-models)
└── shared/        config, security, exception, util — touched by every feature
```

### Storage layer (key concept)

`StorageService` has two impls selected at startup by `@ConditionalOnProperty("spring.cloud.aws.s3.enabled")`:

- `S3StorageServiceImpl` — uses Spring Cloud AWS `S3Template` (do **not** wire `S3Client` manually; the starter auto-configures it)
- `LocalStorageServiceImpl` — filesystem fallback under `app.storage.local.directory` (matches `havingValue=false, matchIfMissing=true`)

`FileController` exposes `GET /files/**` and streams via `storage.downloadFile(key)` — the URL is identical regardless of backend. The route is `permitAll` in `SecurityConfig`. Path-traversal is guarded (`..` rejected). Trade-off: with S3 active, every image request still flows through the app server. Future optimization (public bucket / CloudFront / presigned URLs) is not yet implemented.

When generating files (e.g. `BlogAiService.generateCoverImage`), build the key with `storage.buildObjectKey(folder, id, ext)` and return `/files/{key}` to the client.

### Exception handling

`GlobalExceptionHandler` (`@ControllerAdvice`) has specific handlers for `BusinessException`, `ResourceNotFoundException`, `UnauthorizedException`, `BindException`, rate-limit, etc., **plus a catch-all `Exception` handler that returns `error/500`**. Consequence: throwing `ResponseStatusException` from a controller bypasses Spring's status mapping and is rendered as 500. Always throw the typed exception (e.g. `ResourceNotFoundException`) — the handler will produce the correct 404/4xx view.

### AI integration

Spring AI 1.x with OpenAI starter (`gpt-4o-mini` chat, `gpt-image-1` images). pgvector starter is on the classpath for downstream vector-search features (no schema yet). Every LLM call must go through `LlmUsageTracker.track(user, provider, model, feature, promptTokens, completionTokens)` so admin dashboards stay accurate. Token costs are read from `tuganire.ai-models.models.*` in `application.yml` and applied by `AIModelCostCalculator`.

`BlogAiService.extractImageBytes` handles **both** `b64Json` and `url` responses from OpenAI — image-model responses vary, do not assume one form.

### Frontend

- Thymeleaf + `thymeleaf-layout-dialect` (base layout in `templates/layout/`)
- Tailwind v4 CSS-first config (no `tailwind.config.js`) — design tokens live inline in `src/main/resources/static/css/input.css` via `@theme` / `@plugin "daisyui/theme"`
- HTMX for partial updates, Alpine.js for client interactivity
- i18n: `messages.properties` (FR default, see `spring.web.locale: fr`) + `messages_en.properties`. **All** user-facing UI/form/table text must come from message properties — no hardcoded strings.

### Database

Flyway migrations in `src/main/resources/db/migration/` (currently through V009 — email verification + OAuth provider columns). JPA `ddl-auto: none` — schema changes **must** add a new migration. The dev profile uses the same Postgres as prod (`localhost:5432/tuganire` from `docker-compose.yml`).

### Configuration profiles

- `application.yml` — defaults, env-var-driven (`${VAR:default}` for every external dependency)
- `application-dev.yml` / `application-prod.yml` — profile overrides
- All AWS / OpenAI / Stripe / SMTP credentials default to `changeme` / `*_placeholder` so the app can boot without them; failures surface only when the corresponding feature is exercised.

## Environment variables

See README for the full list. Critical ones: `DATABASE_*`, `STRIPE_API_KEY`, `STRIPE_WEBHOOK_SECRET`, `STRIPE_PRICE_ID_MONTHLY/YEARLY`, `OPENAI_API_KEY`, `AWS_S3_ENABLED` (+ `AWS_S3_BUCKET_NAME`, `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `AWS_REGION` when enabled), `MAIL_*`, `BASE_URL`.

## Conventions specific to this codebase

- Lombok is used everywhere (`@RequiredArgsConstructor` for DI, `@Slf4j` for logging, `@Builder` on entities). Don't write boilerplate constructors.
- Services are exposed as **interfaces** with a sibling `*Impl` class — keep that pattern when adding features.
- Controllers return **DTOs, never entities**; convert with a feature `*Mapper`. Prefer Java `record`s for DTOs.
- Replace duplicated string literals with constants (see each feature's `constant/` package).
- Admin routes require `hasRole("ADMIN")`; the `admin` column on `users` was added in V005 and is checked by `UserService`.
- Auth uses session cookies (`JSESSIONID`), `sessionFixation().newSession()`, max one session per user. CSRF is enabled except on `/webhooks/**`.
- Code style is enforced by `eclipse-formatter.xml` + `checkstyle.xml`. After edits, run `./mvnw spotless:apply` before compiling.
- More detailed backend/frontend conventions live in `rules/spring-boot-rules.mdc` and `rules/thymeleaf-rules.mdc` (Cursor rule files) — consult them when adding code.

## Tests

`src/test/java` has unit + integration tests (`AuthControllerIT`, `StripeWebhookIT`, `LocalStorageServiceImplTest`, `AbstractIntegrationTest` base). **Gotcha:** test classes still live under the pre-fork package `com.saaskit` while main code is `com.tuganire` — when adding tests, follow the `com.tuganire` package or migrate the existing ones. ITs use Testcontainers Postgres; profiles `application-test.yml` (unit) and `application-it.yml` (IT).
