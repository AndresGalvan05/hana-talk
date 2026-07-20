## 1. ai-exercise-svc project scaffold

- [x] 1.1 Create `ai-exercise-svc/` with FastAPI app skeleton (`pyproject.toml`
      or `requirements.txt`, `app/main.py`, health endpoint)
- [x] 1.2 Add `google-genai` SDK dependency and Mongo client (`pymongo` or
      `motor`) dependency
- [x] 1.3 Add Pydantic settings for `GEMINI_API_KEY` and `MONGO_URI` (no
      defaults containing real values; local dev supplies both via
      docker-compose `env_file`/environment)

## 2. Exercise schema and generation

- [x] 2.1 Define Pydantic models mirroring `ExerciseType`
      (`MCQ`/`FILL_IN_BLANK`): request model (`lessonId`, `content`,
      `jlptLevel`), response model (`type`, `prompt`, `options`,
      `correctAnswer`) with MCQ requiring non-empty `options` containing
      `correctAnswer`, fill-in-blank requiring no `options`
- [x] 2.2 Implement the Gemini call using structured output
      (`response_schema`) built from the Pydantic response model
- [x] 2.3 Validate the provider response against the Pydantic model; on
      `ValidationError`, return a 502 without writing to Mongo

## 3. MongoDB cache

- [x] 3.1 Implement a cache lookup by `lessonId` before calling Gemini
- [x] 3.2 On a validated generation, write the result to Mongo keyed by
      `lessonId`
- [x] 3.3 On a cache hit, return the cached result and skip the provider call

## 4. `POST /generate` endpoint

- [x] 4.1 Implement `POST /generate` wiring request validation → cache
      lookup → (cache hit: return) or (cache miss: call Gemini → validate →
      cache → return)
- [x] 4.2 Tests: cache hit skips provider call; cache miss calls provider and
      populates cache; malformed provider response returns 502 and does not
      populate cache; MCQ response with empty options is rejected

## 5. core-api integration

- [x] 5.1 Add an HTTP client bean/config in core-api for `ai-exercise-svc`
      (base URL via env var, e.g. `AI_EXERCISE_SVC_URL`, with a request
      timeout — this call is on the user-facing critical path, not
      best-effort)
- [x] 5.2 Update `ExerciseService.listByLesson`: when `findByLessonId`
      returns empty, call `ai-exercise-svc` with the lesson's `content` and
      the parent course's `jlptLevel`, persist the returned exercises as
      `Exercise` rows, then return them
- [x] 5.3 Tests: `ExerciseServiceTest` covers the call-out-on-empty path
      (mocked HTTP client), persists what comes back, and does not call out
      when `Exercise` rows already exist for the lesson
- [x] 5.4 Tests: a downstream failure from `ai-exercise-svc` (timeout,
      non-2xx) surfaces as a 5xx from `ExerciseController`, not a silently
      empty list

## 6. Infra wiring (local + CI)

- [x] 6.1 Add `mongo` and `ai-exercise-svc` services to
      `infra/docker-compose.yml`; `ai-exercise-svc` uses
      `env_file: ~/.config/dev-projects/llm-keys.env` (absolute path,
      outside the repo) — implemented via `${LLM_KEYS_ENV_PATH}` compose
      variable substitution from a gitignored `infra/.env`
      (`infra/.env.example` documents the shape), so the tracked
      `docker-compose.yml` never hardcodes a machine-specific path
- [x] 6.2 Add `ai-exercise-svc/Dockerfile` (multi-stage, matches the
      arm64/amd64 buildx pattern used by `core-api`/`frontend`)
- [x] 6.3 Add `.github/workflows/ai-exercise-svc.yml` (lint via `ruff`,
      `pytest`, build + push to GHCR — mirrors `core-api.yml`/`frontend.yml`
      structure)

## 7. Verification

- [x] 7.1 `docker compose up -d --build`: request exercises for a lesson
      with zero `Exercise` rows, confirm a real Gemini call occurs, response
      is persisted, and matches the existing no-answer-leakage contract
- [x] 7.2 Repeat the same lesson's request; confirm via `ai-exercise-svc`
      logs/Mongo inspection that no second Gemini call happens (served from
      cache) — note core-api itself won't re-call once `Exercise` rows
      exist, so this specifically verifies `ai-exercise-svc`'s own cache
      via a direct `POST /generate` call
- [x] 7.3 Submit a correct attempt on a generated exercise; confirm grading,
      `exercise.completed` event with `source=EXERCISE`, and progress update
      all work identically to seeded exercises
- [x] 7.4 Confirm existing seeded lessons (already having `Exercise` rows)
      are unaffected — no call to `ai-exercise-svc` for them
- [x] 7.5 Update `docs/ROADMAP.md` (M3 step 2 done), `docs/DEVLOG.md`
      (session entry), `README.md`/service list if needed
