# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

AI Agent-powered adaptive contextual vocabulary learning system for exam preparation. Uses FSRS (Free Spaced Repetition Scheduler) with a custom Dynamic Forgetting Factor (DF) driven by multi-dimensional behavioral data (reaction time, accuracy history, hint usage, spelling performance) — replacing traditional user self-reporting ("know/don't know").

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Frontend | Vue3 + Vite + Element Plus ^2.8.0 + ECharts + vue-echarts + echarts-wordcloud@2.x + Axios + Pinia + mitt + dayjs + lodash-es + SCSS |
| Backend | Spring Boot 3.x + Spring Data JPA + MyBatis + Spring Security + JWT |
| Database | MySQL 8.0 (primary) + Redis (cache, distributed locks, queues) |
| AI | DeepSeek API (JSON mode via RestTemplate) — exam coach "TiMo" |
| API Docs | Knife4j (Swagger) during development |
| MCP | Playwright for browser automation (see `.mcp.json`) |

## Project Status

Stages 1–10 complete (scaffolding, auth, wordbank, FSRS+DF, all 3 learning modes, exam plan, AI Agent, stats/calendar/profile, integration polish). Stage 11 (Admin backend system) backend + frontend pages fully implemented; see `ADMIN_PLAN.md` for design. See `开发进度.md` for stage-by-stage progress.

## Build & Run

**Prerequisites:** Java 21, Maven, MySQL 8.0 (database `timo_words`), Redis

**Backend:**
```bash
cd backend
./mvnw spring-boot:run          # port 8080
# or: ./mvnw package && java -jar target/words-0.0.1-SNAPSHOT.jar
```
DB credentials via env vars `DB_USERNAME` / `DB_PASSWORD` (default: root/root). Schema auto-created by JPA (`ddl-auto: update`). Seed data: `backend/src/main/resources/import-words.sql`.

**Frontend:**
```bash
cd frontend
npm install
npm run dev     # port 3000, proxies /api → localhost:8080
npm run build   # output to dist/
```

**Full stack:** Start backend first (MySQL + Redis must be running), then frontend dev server. Login at `http://localhost:3000`.

## Core Architecture

```
Agent Coach (TiMo) — top-level orchestrator (planning, recommendations, diagnosis, scheduling)
        │
   ┌────┼──────────┐
   │    │          │
   ▼    ▼          ▼
Quick   Contextual  Unified    Exam Plan
Memory  Deep Learn  Review     (dialog-driven)
   │    │          │          │
   └────┴──────────┴──────────┘
                │
        Shared FSRS+DF Engine
```

**Three Learning Modes:**
1. **Quick Memory** — fast word contact: binary "know/don't know" → lightning verification (2-choice, 3s). Grade: 4.0 (correct) / 2.0 (wrong verify) / 1.0 (don't know).
2. **Contextual Deep Learning** — 5-step progressive per group of ~10 words: word card → meaning choice (4选1) → match fill (drag/dropdown) → passage fill (Agent-generated) → word completion (progressive letter hints). Composite grade from weighted s2-s5 scores.
3. **Unified Review** — 3-step detection: lightning judge (3s) → contextual meaning (5s) → orthographic judge (5s). Auto-triggers extra spelling when `grade_review < 2.5` or word is stubborn.

## Algorithm: FSRS + Dynamic Forgetting Factor

- FSRS tracks: Difficulty (D), Stability (S), Retrievability (R)
- `R = exp(ln(0.9) * Δt / S)` — when R drops below threshold, word enters review queue
- After standard FSRS update: `S_final = S' × DF`. DF itself is clamped to [0.5, 1.5] (product of bounded λ factors). `S_final` is floored at 0.1 days to prevent divide-by-zero; **upper bound is effectively unbounded (36500 days)** so successful repetitions extend the interval indefinitely per FSRS design.
- DF composition varies by mode:

| Mode | DF Formula | λ_rt | λ_acc | λ_skip |
|------|-----------|------|-------|--------|
| Quick Memory | `λ_rt × λ_acc × 1.0` | reaction time based | history accuracy | disabled |
| Context Deep | `1.0 × λ_acc × λ_skip_custom` | disabled (group-level) | per-grade history | hint_total based |
| Unified Review | `λ_rt × λ_acc × 1.0` | step-1 reaction time | history accuracy | disabled |

- Each λ factor clamped to [0.7, 1.3] before DF composition
- θ₁=0.3 (λ_rt), θ₂=0.5 (λ_acc), θ₃=0.2, γ=0.1 (λ_skip)
- Cold start: λ_rt uses μ=8s, σ=3s; personal stats after ≥30 correct answers
- λ_acc = 1.0 and λ_skip = 1.0 for new words (no history)

## Key Cross-Cutting Patterns

**TiMo State Machine** — global avatar state driven by mitt events:
- States: `idle | thinking | alert | success | offline`
- Events: `api:call-start` → thinking, `api:call-success` → idle, `api:meltdown` (3 failures in 30s) → offline, `study:correct` → success (1.5s), `study:fatigue` (20min) → alert
- Implemented via `tiMoStore` (Pinia) + `TiMoAvatar.vue` (SVG switching + CSS halo animations)

**Data Isolation** — conversation quiz logs (`conversation_quiz_log`) are separate from FSRS main records (`quiz_records`) to preserve forgetting curve accuracy. Agent marks words as `conversation_mastered` for 3 days to filter from recommendations without modifying S/D values.

**Stubborn Words (顽固词)** — auto-marked by error reinforcement rules across all modes; cleared when same mode achieves grade ≥ 3.5 for 3 consecutive reviews.

## Backend Package Structure

```
com.timo.words
├── common          # Global exception handling, unified response body (Result), constants
├── config          # Security, Redis, CORS configs
├── modules/
│   ├── auth        # Register, login, JWT
│   ├── user        # Profile, preferences, exam plan storage
│   ├── word        # Word CRUD, meanings, examples, exam points, word analysis
│   ├── study       # quiz_records, study_plans, user_word_bind updates
│   ├── examplan    # Dialog state machine, plan generation/adjustment
│   ├── review      # Review queue (next_review_time <= now), stubborn word callbacks
│   ├── agent       # Chat sessions/messages, dynamic question generation, recommendations, weekly reports
│   ├── statistics  # Dashboard data (overview, retention, forgetting curve, heatmap, reaction time, weak words)
│   ├── calendar    # Check-in records, monthly view
│   └── admin       # Super-admin backoffice: dashboard, user management, word import, AI provider config + call logs, system config, operation logs, DataInitializer (seeds super-admin + default AI provider from env vars)
├── algorithm/
│   ├── fsrs        # Scheduler class: review(word, grade, studyMode) → updated S, D, R, next_review_time
│   ├── df          # DynamicForgettingFactor: lambda_rt, lambda_acc, lambda_skip_custom
│   └── scoring     # GradeMapper: behavior data → 1.0~4.0 float grade per mode
└── infrastructure/
    ├── ai          # DeepSeek client wrapper, circuit breaker (3 failures → fallback to local rules)
    └── event       # Event publishing (mirrors frontend mitt)
```

## API Endpoints (key routes)

**Auth:** `POST /api/auth/register`, `POST /api/auth/login`

**Study:** `POST /api/study/submit-quick-memory`, `POST /api/study/submit-context-deep`, `POST /api/study/submit-review`

**Review:** `GET /api/review/queue`, `POST /api/review/result`

**Agent:** `POST /api/agent/chat/send`, `GET /api/agent/recommend`, `POST /api/agent/generate-questions`, `GET /api/agent/weekly-report`

**Exam Plan:** `POST /api/exam-plan/start-dialog`, `POST /api/exam-plan/continue-dialog`, `POST /api/exam-plan/generate`

**Statistics:** `GET /api/statistics/overview`, `GET /api/statistics/forgetting-curve`, `GET /api/statistics/heatmap`, `GET /api/statistics/weak-words`

**Admin** (requires `role=ADMIN` or `SUPER_ADMIN` in JWT; some routes are `SUPER_ADMIN`-only):
- Auth: `POST /api/admin/auth/verify-secret`, `POST /api/admin/auth/impersonate/{userId}`, `POST /api/admin/auth/exit-impersonate`
- Dashboard: `GET /api/admin/dashboard/overview`, `GET /api/admin/dashboard/trend`
- Users: `GET /api/admin/users` (paged, search, role/status filter), `PUT /api/admin/users/{id}/role`, `PUT /api/admin/users/{id}/status`, `DELETE /api/admin/users/{id}`
- Words: `GET|POST|PUT|DELETE /api/admin/words[/{id}]`, `POST /api/admin/words/import`, `DELETE /api/admin/words/by-exam-type`
- AI: `GET|POST|PUT|DELETE /api/admin/ai/providers[/{id}]`, `PUT /api/admin/ai/providers/{id}/activate`, `GET /api/admin/ai/logs`, `GET /api/admin/ai/stats`
- System: `GET|PUT /api/admin/system/config[/{key}]`
- Logs: `GET /api/admin/logs` (operation audit trail)

Full endpoint list: 10 modules, ~50 endpoints. See `文档/后端规划.txt` §四 and `ADMIN_PLAN.md` §四.2.

## Database Key Tables

- `users` — adds `role` (`USER`/`ADMIN`/`SUPER_ADMIN`), `status` (`ACTIVE`/`BANNED`), `last_login_at`
- `user_word_bind` — per-user per-word FSRS state (D, S, R, next_review_time, is_stubborn, consecutive_errors)
- `quiz_records` — learning event log (study_mode, grade 1.0~4.0, composite_grade, step_results JSON, hint_total, reaction_time_ms, source)
- `conversation_quiz_log` — isolated conversation practice records (does NOT feed FSRS)
- `chat_sessions` / `chat_messages` — Agent dialog with card_data and suggested_actions JSON
- `stubborn_log` — mark/unmark history for stubborn words
- `ai_provider_config` — pluggable AI provider configs (DeepSeek/OpenAI/Qwen/Zhipu), only one `is_active` at a time
- `ai_call_log` — every AI request: provider, model, tokens, latency, status (`SUCCESS`/`FAILED`/`CIRCUIT_OPEN`)
- `system_config` — runtime-tunable params (admin_secret, FSRS defaults, DF θ, fatigue threshold, circuit breaker config) — changes take effect without restart
- `admin_operation_log` — audit trail (login, role change, ban/unban, AI/system config edit, batch import, impersonation)
- `word_import_batch` — CSV/Excel import progress and result tracking

## Frontend Key Patterns

- **API layer:** `src/api/` mirrors backend modules 1:1 (auth.js, words.js, study.js, etc.). All requests go through `request.js` Axios instance with JWT interceptor + `/api` prefix.
- **Stores:** Pinia stores in `src/stores/` — `user.js` (auth state + profile), `study.js` (session state), `examPlan.js` (dialog flow), `agent.js` (chat state).
- **Event bus:** mitt in `events/` — drives TiMo state, API meltdown detection, study feedback
- **Routing:** `beforeEach` guard checks `needAuth` + JWT; `keep-alive` caches Wordbank/Stats/Profile/Calendar
- **Layout:** MainLayout with top Navbar + `<router-view>` + fixed TiMoFAB (bottom-right, 64px)
- **TiMo Dialog:** 480px fixed overlay, 4 message types (text, card, action, quick_reply)
- **Desktop-first:** base width 1440px, breakpoint at 1024px
- **Speech:** Browser SpeechSynthesis API (not backend)
- **Fatigue detection:** snackbar after 20min cumulative study
- **Admin frontend:** separate `AdminLayout.vue` (sidebar + topbar) at `/admin/*` routes — Dashboard / Users / Words / AI / Stats / Settings / Logs. Hidden entry on Login: rapid-click TiMo logo 5× within 3s reveals a secret-key input box. Router guard checks JWT role; `SUPER_ADMIN`-only routes additionally check role server-side.

## Working with This Repository

- Documentation is in Chinese (Simplified) — algorithm specs in `文档/基于 AI Agent 与 FSRS 的自适应语境化背单词系统.txt`, frontend plan in `文档/前端规划.txt`, backend plan in `文档/后端规划.txt`, admin design in `ADMIN_PLAN.md`
- Tests: backend `./mvnw test -Dspring.profiles.active=test` (277 tests across algorithm, services, controllers, admin), frontend `npm test` (96 tests covering stores, router, API contract). Both pass green as of 2026-05-23.
- Agent TiMo visual assets: `agent形象/` (5 SVG states: idle, thinking, alert, success, offline)
- Word data sourced from kajweb/dict (18,142 words across 8 exam types), enriched with Collins stars + BNC/COCA frequency from ECDICT
