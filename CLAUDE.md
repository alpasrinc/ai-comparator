# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

Full-stack app that fans one user message out to OpenAI, Anthropic, and Gemini in parallel and compares the answers. Two modes:
- **Compare** (`/api/chat/**`, `/api/conversations/**`): three providers answer the same message; the user picks one answer as the active branch, and the next message only carries that branch's history as context.
- **Debate** (`/api/debates/**`): selected providers argue a topic over 1–5 rounds (each round they critique the previous round's answers), then a chosen synthesizer writes a neutral combined answer.

Stack: React 19 + Vite (frontend, nginx-served in Docker) → Spring Boot 4 / Java 21 (backend) → MySQL 8.4. Backend talks to the AI services with the official Java SDKs (`openai-java`, `anthropic-java`, `google-genai`); the frontend never holds API keys.

## Commands

Backend (from `backend/`):
```bash
./mvnw spring-boot:run        # run backend on :8080
./mvnw test                   # full test suite (needs a running MySQL)
./mvnw test -Dtest=DebateOrchestratorTests            # single test class
./mvnw test -Dtest=DebateOrchestratorTests#methodName # single test method
```

Frontend (from `frontend/`):
```bash
npm run dev     # dev server on :5173
npm run test    # vitest (unit tests for utils/*)
npm run lint    # eslint
npm run build   # production build
```

Whole stack: `docker compose up --build` (needs the env vars below exported).

### Windows / shell gotchas
- On Windows use `./mvnw` (the POSIX wrapper via the Bash tool), **not** `mvnw.cmd`. Never combine a trailing `&` with `run_in_background`.
- Required env vars for any backend run or test: `AI_COMPARATOR_DB_PASSWORD`, `OPENAI_API_KEY`, `ANTHROPIC_API_KEY`, `GEMINI_API_KEY`. Model IDs, per-provider token limits, timeouts, and rate-limit tuning are all optional overrides — see `backend/src/main/resources/application.properties` for the full list and defaults.

## Architecture

### Provider abstraction
`ai/AiProvider` is the seam. Each of `OpenAiProvider` / `AnthropicProvider` / `GeminiProvider` implements `sendMessage` (blocking), `streamMessage` (token callback), and optionally `streamSynthesisMessage` (larger token budget for debate synthesis). Services depend on `List<AiProvider>` and never reference a concrete SDK. To add a provider: add an `AiProviderType` enum value, implement the interface, and it's auto-wired into compare/debate.

### The two orchestration services
- **`AiComparisonService`** — fans a message to the selected providers concurrently on a virtual-thread executor (`aiExecutor`), joins the results, and persists. Per-provider timeout via `completeOnTimeout` (blocking) / `orTimeout` (streaming); a slow or failing provider degrades to an error response and never blocks the others.
- **`DebateOrchestrator`** — **rounds are sequential, participants within a round run in parallel.** Round N+1's prompt is built from the full transcript of rounds 1..N (see `DebatePromptBuilder`). The in-memory `transcript` (`List<Map<AiProviderType,String>>`) is the debate's memory — the models are stateless, the orchestrator carries the history. If round 1 comes back entirely blank the debate is marked `FAILED`; otherwise it always ends with a synthesis pass.

### Context building (compare mode) — `ConversationService`
Messages form a tree via `parent_message_id` (self-FK). `buildActiveContextPrompt` walks from the conversation's `activeMessage` up to the root, so **only the selected branch** becomes context — sibling/alternative answers are excluded. Each prompt is prefixed with an identity preamble ("you are ChatGPT/Claude/Gemini") so a provider doesn't adopt another provider's self-description from the transcript.

### Streaming (SSE)
Both stream endpoints emit `text/event-stream` frames (`event: <name>\ndata: <json>\n\n`). `SseSupport.send` wraps every emit in a per-emitter `synchronized` lock so parallel providers don't interleave frames. Frontend (`services/api.js`) reads the byte stream with `TextDecoder`, splits on `\n\n`, and **keeps the last fragment in a buffer** (it may be a half-frame) before `JSON.parse`. Event vocabularies differ by mode — compare: `start/token/done/error`; debate: `start/round-start/token/participant-done/participant-error/round-done/synthesis-done/done` (`round: 0` on a token means the synthesis stream).

### `ResponseIntensity`
An enum (LOW/MEDIUM/HIGH) that does two things at once: `scaleTokens()` multiplies the provider's max-output-tokens, and `applyTo()` prepends a brevity/detail instruction to the prompt. Passed through the whole call chain.

### Persistence
Schema is owned by **Flyway** (`db/migration/V1..V4`), not Hibernate — `spring.jpa.hibernate.ddl-auto=none`. Enums are stored as `VARCHAR` text (`.name()`), not ordinals. `open-in-view=false`, so entities must be fully loaded inside `@Transactional` service methods before returning DTOs. Token usage columns were added in `V4`.

### Rate limiting
`AiRateLimitFilter` is an in-memory per-IP token bucket guarding **only `/api/chat/**`** (the paid calls); returns 429 when exhausted. No external dependency.

## Operational gotcha: two MySQL instances
There is a Docker MySQL (what the app uses under `docker compose`, container port `mysql:3306`, **not** published to the host) and, on this dev machine, a separate local Windows MySQL84 service on `localhost:3306` with older data. A plain `mysql` client from PowerShell hits the local one — so "old records" usually means you queried the wrong database. To see the app's real data, exec into the container:
```
docker exec ai-comparator-mysql-1 mysql --default-character-set=utf8mb4 -u ai_comparator_app ai_comparator -e "SELECT ..."
```
For Turkish characters, the issue is client charset, not the data: pass `--default-character-set=utf8mb4` (or `SET NAMES utf8mb4;` interactively), and `chcp 65001` if the Windows console still mangles output.

## CI
`.github/workflows/ci.yml` runs backend tests against a MySQL 8.4 service container (with placeholder AI keys) and, separately, frontend lint + build. Keep both green.
