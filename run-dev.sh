#!/usr/bin/env bash
#
# run-dev.sh — Spin up the full Tuganire dev stack in one command.
#
#   1. Postgres 16 + Redis 7   (docker-compose, detached)
#   2. Python MMS-TTS server    (FastAPI on :8000) — optional
#   3. Tailwind CSS build        (one-shot, or --watch in background)
#   4. Spring Boot backend       (dev profile, :8080) — foreground
#
# Usage:
#   ./run-dev.sh                 # infra + CSS + backend (no TTS, no watch)
#   ./run-dev.sh --tts           # also start the Python MMS-TTS server
#   ./run-dev.sh --watch         # rebuild CSS on change (background)
#   ./run-dev.sh --tts --watch   # everything
#   ./run-dev.sh --no-app        # bring up dependencies only, skip the backend
#
# Stopping: Ctrl+C stops the backend and any background helpers this script
# started. Postgres/Redis keep running — stop them with `docker-compose down`.

set -euo pipefail

cd "$(dirname "$0")"

# ---- load local secrets ----------------------------------------------------
# Export everything in .env (gitignored) so the Spring backend picks up secrets
# like PROTO_TTS_TOKEN / OPENAI_API_KEY without baking them into any config file.
if [[ -f .env ]]; then
  set -a
  # shellcheck disable=SC1091
  . ./.env
  set +a
fi

# ---- flags -----------------------------------------------------------------
START_TTS=false
WATCH_CSS=false
START_APP=true
for arg in "$@"; do
  case "$arg" in
    --tts)    START_TTS=true ;;
    --watch)  WATCH_CSS=true ;;
    --no-app) START_APP=false ;;
    -h|--help)
      sed -n '2,22p' "$0" | sed 's/^# \{0,1\}//'
      exit 0 ;;
    *) echo "Unknown option: $arg (try --help)"; exit 1 ;;
  esac
done

# ---- track background PIDs so Ctrl+C cleans them up ------------------------
BG_PIDS=()
cleanup() {
  echo ""
  echo "→ Shutting down helpers started by this script..."
  for pid in "${BG_PIDS[@]:-}"; do
    if [[ -n "${pid:-}" ]] && kill -0 "$pid" 2>/dev/null; then
      kill "$pid" 2>/dev/null || true
    fi
  done
  echo "  Postgres/Redis are still running. Stop them with: docker-compose down"
}
trap cleanup EXIT INT TERM

# ---- 1. infrastructure -----------------------------------------------------
echo "→ Starting Postgres 16 + Redis 7 (docker-compose)..."
docker-compose up -d

echo "→ Waiting for Postgres to be healthy..."
for i in $(seq 1 30); do
  if docker exec tuganire-postgres pg_isready -U tuganire >/dev/null 2>&1; then
    echo "  Postgres ready."
    break
  fi
  sleep 1
  [[ "$i" == "30" ]] && { echo "  Postgres did not become ready in time."; exit 1; }
done

# ---- 2. Python MMS-TTS server (optional) -----------------------------------
if [[ "$START_TTS" == "true" ]]; then
  echo "→ Starting Python MMS-TTS server on :8000 ..."
  # torch 2.5.0 has no wheels for Python 3.14 — prefer 3.12/3.11 if present.
  TTS_PYTHON="${TTS_PYTHON:-}"
  if [[ -z "$TTS_PYTHON" ]]; then
    for cand in python3.12 python3.11 python3; do
      if command -v "$cand" >/dev/null 2>&1; then TTS_PYTHON="$cand"; break; fi
    done
  fi
  (
    cd tts-server
    # Guard on the uvicorn binary, not just the dir — a half-built venv (created
    # but deps never installed) must still trigger the install.
    if [[ ! -x .venv/bin/uvicorn ]]; then
      echo "  Setting up virtualenv with $TTS_PYTHON + installing requirements (first run, may take a few minutes)..."
      rm -rf .venv
      "$TTS_PYTHON" -m venv .venv
      ./.venv/bin/pip install -q --upgrade pip
      ./.venv/bin/pip install -q -r requirements.txt
    fi
    exec ./.venv/bin/uvicorn tts_server:app --host 0.0.0.0 --port 8000
  ) &
  BG_PIDS+=($!)
  echo "  TTS server launching (first run downloads model weights — see GET http://localhost:8000/health)."
fi

# ---- 3. Tailwind CSS -------------------------------------------------------
if [[ ! -d node_modules ]]; then
  echo "→ Installing npm dependencies (first run)..."
  npm install
fi

if [[ "$WATCH_CSS" == "true" ]]; then
  echo "→ Starting Tailwind CSS watcher (background)..."
  npm run watch &
  BG_PIDS+=($!)
else
  echo "→ Building CSS once..."
  npm run build
fi

# ---- 4. Spring Boot backend ------------------------------------------------
if [[ "$START_APP" == "true" ]]; then
  echo "→ Starting Spring Boot backend (dev profile) on :8080 ..."
  echo "  Web POC:  http://localhost:8080"
  echo "  Press Ctrl+C to stop."
  ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
else
  echo "→ Dependencies are up (--no-app). Run the backend yourself with:"
  echo "    ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev"
  # keep background helpers alive until interrupted
  if [[ ${#BG_PIDS[@]} -gt 0 ]]; then
    echo "  Background helpers running (PIDs: ${BG_PIDS[*]}). Ctrl+C to stop them."
    wait
  fi
fi
