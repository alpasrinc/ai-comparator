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
- **`DebateOrchestrator`** — **rounds are sequential, participants within a round run in parallel.** Round N+1's prompt carries **only round N's answers**, not the full transcript (`DebatePromptBuilder.buildCritiqueRoundPrompt` reads just the last entry) — so round 3 never sees round 1. Only `buildSynthesisPrompt` gets the whole transcript. The in-memory `transcript` (`List<Map<AiProviderType,String>>`) is what the orchestrator carries; the models are stateless. Whether that narrow window is intentional is an open question; widening it would also make the debate prompt cacheable. If round 1 comes back entirely blank the debate is marked `FAILED`; otherwise it always ends with a synthesis pass.

### Context building (compare mode) — `ConversationService`
Messages form a tree via `parent_message_id` (self-FK). `buildActiveContextPrompt` walks from the conversation's `activeMessage` up to the root, so **only the selected branch** becomes context — sibling/alternative answers are excluded. Each prompt is prefixed with an identity preamble ("you are ChatGPT/Claude/Gemini") so a provider doesn't adopt another provider's self-description from the transcript.

### Prompt caching
Prompts are split into `PromptParts(cacheablePrefix, volatileSuffix)` and the split is what makes caching work — caching is a prefix match, so a single byte change anywhere in the prefix invalidates everything after it. In compare mode the prefix is the identity preamble plus the active branch transcript; the tail is the intensity directive plus the new user turn. **The intensity directive must stay in the tail** — it used to be prepended to the whole prompt, which invalidated the cache on every intensity change. The retry path (`buildBranchPrompt`) splits at the same point, so retrying a turn reads the prefix the first attempt wrote.

`AnthropicProvider` marks the prefix with an explicit `cache_control` breakpoint (TTL via `anthropic.cache.ttl`, default `5m`); OpenAI and Gemini cache automatically once the prefix is stable. Debate mode gets no caching: `buildCritiqueRoundPrompt` sends only the previous round, so there is no growing prefix — it passes `PromptParts.volatileOnly(...)`.

`TokenUsage` carries `cacheReadTokens` / `cacheWriteTokens` (persisted by `V5`). **`inputTokens` is no longer the whole prompt** — it is the uncached remainder, and total = `input + cacheRead + cacheWrite`. Anthropic reports it that way natively; OpenAI and Gemini include cached tokens in their input count, so the providers subtract it to keep one meaning across all three.

The guard against silent regressions is `ConversationServiceIntegrationTests` asserting that one turn's `cacheablePrefix` is a byte-exact prefix of the next turn's. Caching fails silently — requests keep succeeding, the bill just goes up — so that assertion matters more than it looks.

Note the Anthropic model matters: `claude-haiku-4-5` has a 4096-token minimum cacheable prefix (the highest tier), so caching only engages after roughly 8-10 turns. Below the threshold the breakpoint is silently inert and no write premium is charged.

### Streaming (SSE)
Both stream endpoints emit `text/event-stream` frames (`event: <name>\ndata: <json>\n\n`). `SseSupport.send` wraps every emit in a per-emitter `synchronized` lock so parallel providers don't interleave frames. Frontend (`services/api.js`) reads the byte stream with `TextDecoder`, splits on `\n\n`, and **keeps the last fragment in a buffer** (it may be a half-frame) before `JSON.parse`. Event vocabularies differ by mode — compare: `start/token/done/error`; debate: `start/round-start/token/participant-done/participant-error/round-done/synthesis-done/done` (`round: 0` on a token means the synthesis stream).

### `ResponseIntensity`
An enum (LOW/MEDIUM/HIGH) that does two things at once: `scaleTokens()` multiplies the provider's max-output-tokens, and `applyTo()` prepends a brevity/detail instruction to the **volatile half** of the prompt (`PromptParts.volatileSuffix`), never to the whole thing — see Prompt caching. Passed through the whole call chain; the providers apply it, the prompt builders do not.

### Persistence
Schema is owned by **Flyway** (`db/migration/V1..V5`), not Hibernate — `spring.jpa.hibernate.ddl-auto=none`. Enums are stored as `VARCHAR` text (`.name()`), not ordinals. `open-in-view=false`, so entities must be fully loaded inside `@Transactional` service methods before returning DTOs. Token usage columns were added in `V4`, prompt-cache token columns in `V5`.

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
