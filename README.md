# Email Marketing Platform Backend & Multi-Module Workspace

This repository has evolved from a generic starter into an email marketing platform with:

* Spring Boot 3 backend (Oracle + Flyway) for template management, subscription/plan gating, auth, assets.
* React (Vite) frontend with GrapesJS editor integration and planned MJML editing support.
* Python AI utilities for extraction / (future) LLM powered draft enhancement.
* Mobile scaffold (React Native / Expo) reusing auth + subscription API concepts.

## Module Overview
1. `backend/` – Core API: Auth (JWT), subscription gating (AOP), email templates (CRUD, versions, variables, assets, AI draft, variable analysis, MJML placeholder rendering), OpenAPI ready, error model, rate limit hooks.
2. `email-marketing-frontend/frontend/` – React app: login/register flow, auth context, GrapesJS newsletter editor toggle (migrating away from Unlayer), template consume UI (in progress).
3. `mobile/` – API stubs for subscriptions & auth (extend as needed).
4. `python-ai/` – Prototype Groq / LLM & document extraction helpers.

## Key Backend Features
* JWT stateless auth & security filter chain.
* Plan / feature gating via `@RequiresPlan` & `@RequiresPaidPlan` (AOP aspect enforcement).
* Email Template Domain:
	- CRUD with ownership + shared templates.
	- Version history endpoint.
	- Variable registration (`/variables`) & extraction/analysis (`/analyze`).
	- Asset upload list/upload endpoints.
	- Preview + simple test-send placeholder.
	- AI draft generation endpoint (`POST /api/templates/ai/draft`) – placeholder HTML generator returning discovered variables.
	- MJML source fields + placeholder renderer service (`MjmlRenderService`) ready to be replaced by real MJML pipeline (Node/CLI/microservice).
* Variable analysis service detects `{{variable}}` tokens, required variable presence (currently `unsubscribe`), and filters unsafe tokens.
* Consistent error model (`GlobalExceptionHandler`).
* Flyway migrations adapted for Oracle (ensure Oracle driver + flyway-database-oracle dependency present).

## Core Template Endpoints (Summary)
Base path: `/api/templates`

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/` | List own templates |
| GET | `/accessible` | Own + shared accessible |
| GET | `/shared` | Public/shared templates |
| POST | `/` | Create template |
| GET | `/{id}` | Fetch (must own or shared) |
| PUT | `/{id}` | Update (owner only) |
| POST | `/{id}/duplicate` | Duplicate template (into caller ownership) |
| POST | `/{id}/preview` | Merge sample data and return rendered HTML |
| POST | `/{id}/test-send` | Placeholder test send action |
| GET | `/{id}/versions` | Version history |
| GET | `/{id}/variables` | List registered variables |
| POST | `/{id}/variables` | Register a variable |
| DELETE | `/{id}/variables/{varId}` | Delete variable |
| GET | `/{id}/assets` | List assets |
| POST | `/{id}/assets` | Upload asset (multipart) |
| GET | `/{id}/analyze` | Extract variables & required checks from HTML |
| POST | `/ai/draft` | Generate AI starter HTML (placeholder impl) |

## Variable Analysis
`VariableAnalysisService` matches `{{var_name}}` (supports dots for nested context). Required variables (currently `unsubscribe`) are reported if missing. Extend sets in the service to enforce more. Future enhancements: unused registered variable warnings, reserved keyword scanning, length heuristics.

## MJML Placeholder
Entity includes MJML source + last rendered HTML fields. `MjmlRenderService` currently does regex replacements for `mj-section` / `mj-text`. Replace with a real renderer by:
1. Adding a Node microservice using official MJML.
2. Calling it via HTTP or local process (`Runtime.exec`) and caching outputs.
3. Validating output + sanitizing before persistence.

## AI Draft Generation
`POST /api/templates/ai/draft` accepts `{ prompt, tone, audience, callToAction }` and returns HTML + extracted variables. Swap placeholder with an LLM provider (OpenAI / Azure / local) behind a new `AIDraftService` that handles:
* Prompt assembly (brand voice / compliance).
* Safety filtering (PII, disallowed content).
* Automatic variable registration (optional future step).

## Local Development (Backend)
Prerequisites: Java 21+, Maven, Oracle DB (or test container swap), Node (if adding real MJML/AI pipeline), PowerShell (on Windows).

Environment (example):
```
spring.datasource.url=jdbc:oracle:thin:@localhost:1521/FREEPDB1
spring.datasource.username=APP
spring.datasource.password=APP
spring.datasource.driver-class-name=oracle.jdbc.OracleDriver
spring.flyway.baseline-on-migrate=true
security.jwt.secret=CHANGE_ME_LONG_RANDOM
```

Run:
```
mvn -f backend/pom.xml spring-boot:run
```

## Frontend Notes
GrapesJS integrated (newsletter preset) replacing Unlayer gradually. Add MJML mode toggle by mapping GrapesJS blocks to MJML tags or providing a dual-pane (MJML source ↔ rendered preview) once real renderer is wired.

## Recommended Next Enhancements
* Enforce required variable presence on save/update (return 400 if missing).
* Real MJML rendering service integration.
* AI draft LLM integration + brand style injection.
* Asset storage: replace local/in-memory with Oracle Object Storage or S3.
* Add caching for template preview renders.
* Add OpenAPI annotations & generate spec for frontend consumption.
* Expand test coverage (service + controller tests for new endpoints).

## License
Internal reuse. Add explicit license if distributing externally.
