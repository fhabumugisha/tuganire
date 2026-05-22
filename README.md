# Tuganire — Spring Boot SaaS Starter Template

A production-ready Spring Boot 3.5 + Java 21 SaaS starter with authentication, Stripe payments, and a modern frontend stack.

## Stack

- **Backend:** Java 21, Spring Boot 3.5, Spring Security, Spring Data JPA
- **Payments:** Stripe (subscriptions, webhooks, billing portal)
- **Database:** PostgreSQL 16 + pgvector, Flyway migrations
- **Frontend:** Thymeleaf, Tailwind CSS v4, DaisyUI, HTMX, Alpine.js
- **Storage:** Spring Cloud AWS S3 (with local filesystem fallback)
- **AI:** Spring AI (OpenAI: `gpt-4o-mini` chat, `gpt-image-1` images)
- **Email:** Spring Boot Mail
- **Rate limiting:** Bucket4j
- **Build:** Maven + frontend-maven-plugin
- **Deploy:** Jib (Docker image build)

## Quick Start

```bash
# 1. Start PostgreSQL
docker-compose up -d

# 2. Install and build CSS
npm install
npm run build-prod

# 3. Set environment variables (copy and edit)
cp .env.example .env

# 4. Run the app
export $(cat .env | xargs)
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

The app will be available at http://localhost:8080

## Environment Variables

Copy `.env.example` to `.env` and fill in the values:

| Variable | Description |
|---|---|
| `DATABASE_HOST` | PostgreSQL host:port/database (e.g. `localhost:5432/tuganire`) |
| `DATABASE_USERNAME` | PostgreSQL username |
| `DATABASE_PASSWORD` | PostgreSQL password |
| `MAIL_USERNAME` | SMTP username |
| `MAIL_PASSWORD` | SMTP password |
| `MAIL_FROM` | From address for emails |
| `STRIPE_API_KEY` | Stripe secret key (test: `sk_test_...`) |
| `STRIPE_WEBHOOK_SECRET` | Stripe webhook signing secret |
| `STRIPE_PRICE_ID_MONTHLY` | Stripe price ID for monthly plan |
| `STRIPE_PRICE_ID_YEARLY` | Stripe price ID for yearly plan |
| `BASE_URL` | Public base URL (e.g. `https://yourapp.com`) |
| `OPENAI_API_KEY` | OpenAI API key (used for blog AI excerpts/covers) |
| `AWS_S3_ENABLED` | `true` to use S3, `false`/unset → local filesystem fallback |
| `AWS_S3_BUCKET_NAME` | S3 bucket name (required when S3 is enabled) |
| `AWS_S3_PREFIX` | Optional key prefix (e.g. `prod/`) — keys land at bucket root if empty |
| `AWS_ACCESS_KEY_ID` | AWS access key (S3 only) |
| `AWS_SECRET_ACCESS_KEY` | AWS secret key (S3 only) |
| `AWS_REGION` | AWS region (e.g. `us-east-1`, `eu-west-3`) |
| `LOCAL_STORAGE_DIR` | Local fallback directory (default: `${java.io.tmpdir}/tuganire-storage`) |
| `GOOGLE_CLIENT_ID` | Google OAuth client ID — leave unset to disable Google Sign-In |
| `GOOGLE_CLIENT_SECRET` | Google OAuth client secret (required when `GOOGLE_CLIENT_ID` is set) |

## What's Included

### Authentication
- User registration with email/password
- Login with form-based authentication
- Google Sign-In (OAuth 2.0) — opt-in via `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` (button hidden when unset)
- Password reset via email (tokenized, time-limited)
- Email verification at registration
- Profile management (name, password, delete account)
- Session management

### Payments (Stripe)
- Pricing page (`/payment/pricing`)
- Checkout session creation
- Webhook handler for subscription lifecycle
- Monthly and yearly subscription plans
- Billing portal integration
- Plan-based access control (`FREE` / `PREMIUM`)

### Infrastructure
- Flyway database migrations (V001–V004)
- Rate limiting per IP with Bucket4j
- Global exception handler
- LLM input sanitizer utility
- Markdown renderer utility
- Slug generator utility

### Object Storage
- `StorageService` interface with two swappable implementations selected by `spring.cloud.aws.s3.enabled`:
  - `S3StorageServiceImpl` — uses Spring Cloud AWS `S3Template` (auto-configured; no manual `S3Client` wiring)
  - `LocalStorageServiceImpl` — filesystem fallback under `app.storage.local.directory` (default `${java.io.tmpdir}/tuganire-storage`)
- `FileController` serves stored objects over HTTP at `/files/**` (path-traversal guarded, MIME auto-detected) — same URL works regardless of backend
- AI-generated blog covers are persisted via `StorageService` and exposed as `/files/blog/covers/{uuid}.png`

**Enable S3:**

```bash
export AWS_S3_ENABLED=true
export AWS_S3_BUCKET_NAME=my-bucket
export AWS_ACCESS_KEY_ID=...
export AWS_SECRET_ACCESS_KEY=...
export AWS_REGION=eu-west-3
```

Leave `AWS_S3_ENABLED` unset (or `false`) in dev — files land under `${java.io.tmpdir}/tuganire-storage`.

**Usage in your code:**

```java
@RequiredArgsConstructor
public class MyService {
    private final StorageService storage;

    public String save(byte[] bytes) {
        String key = storage.buildObjectKey("uploads", UUID.randomUUID().toString(), "png");
        try (InputStream in = new ByteArrayInputStream(bytes)) {
            storage.uploadFile(in, key, "image/png", bytes.length);
        }
        return "/files/" + key; // served by FileController
    }
}
```

### Frontend
- Base Thymeleaf layout with DaisyUI
- Authentication pages (login, register, forgot/reset password)
- Profile page with tabs (profile, subscription, security)
- Dashboard (generic placeholder)
- Error pages (400, 404, 500)
- HTMX + Alpine.js ready

## Forking This Template

To create a new app from this template:

```bash
# 1. Clone
git clone https://github.com/yourorg/tuganire my-app
cd my-app

# 2. Rename package (macOS)
find src -type f -name "*.java" -exec sed -i '' 's/com\.tuganire/com.myapp/g' {} +
find src -type f \( -name "*.html" -o -name "*.yml" -o -name "*.properties" \) -exec sed -i '' 's/tuganire/myapp/g' {} +

# Move the source directory
mv src/main/java/com/tuganire src/main/java/com/myapp

# 3. Update pom.xml
sed -i '' 's/com.tuganire/com.myapp/g' pom.xml
sed -i '' 's/tuganire/my-app/g' pom.xml

# 4. Rename main class if needed
# Edit src/main/java/com/myapp/TuganireApplication.java

# 5. Initialize new git repo
rm -rf .git
git init && git add -A && git commit -m "feat: initial commit from tuganire template"
```

## Database Migrations

| Migration | Description |
|---|---|
| V001 | Create `users` table with Stripe customer ID |
| V002 | Create `usage_stats` table for freemium tracking |
| V003 | Create `subscriptions` table for Stripe subscriptions |
| V004 | Create `password_reset_tokens` table |

Add your feature migrations starting at `V005__...`.

## Development Commands

```bash
# Run with dev profile
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# Build for production
./mvnw clean package -DskipTests

# Build Docker image
./mvnw compile jib:dockerBuild

# Build CSS in watch mode
npm run watch

# Run tests
./mvnw test
```

## Architecture

Feature-driven package structure under `com.tuganire`:

```
com.tuganire/
├── auth/          - Registration, login, profile, password reset
├── payment/       - Stripe subscriptions + webhooks
├── blog/          - Public blog + admin editor (with AI excerpt/cover)
├── storage/       - StorageService + S3/Local impls + /files/** controller
├── shared/        - Config, security, exceptions, services, utils
└── common/        - AI model cost properties (for downstream apps)
```

Add new features by creating `com.tuganire.yourfeature/` with the standard layers: `controller`, `service`, `model`, `repository`, `dto`.
