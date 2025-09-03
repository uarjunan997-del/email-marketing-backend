# Project Starter Template

Reusable foundation extracted from Expense Monitor project for rapid new project bootstrapping.

## Modules

1. backend/ - Spring Boot base: Auth (JWT), subscription / plan gating via AOP, global error model, email templating hook, OpenAPI, rate limiting, caching.
2. frontend/ - React + Vite base: Auth context, API helper with token refresh/logout handling, preferences context, routing skeleton placeholder (not included here yet).
3. mobile/ - React Native (Expo) scaffold with package.json (copy minimal pieces as needed).
4. python-ai/ - Python Groq + PDF extraction pipeline sample for AI integrations.

## How To Use

Pick the modules you need. Each subfolder contains its own README with setup steps and environment variables.

## High-Level Features Included

- JWT auth utilities (generate/validate tokens) and security filter chain.
- AOP-based plan / feature gating annotations (`@RequiresPlan`, `@RequiresPaidPlan`) enforced by `PlanAccessAspect`.
- Consistent error response schema (`ErrorResponse`, `GlobalExceptionHandler`).
- Frontend auth & API service wrappers with automatic session expiry handling.
- Python statement extraction with Groq LLM & OCR fallback.

## Suggested Next Steps After Copy

- Replace package names (`com.expensetracker`) with your new domain.
- Change JWT secret & security properties.
- Review dependency versions for updates.
- Add CI pipeline (build + test + lint) for each module.
- Implement domain-specific models/controllers incrementally.

## License

Internal reuse template. Add license text if distributing externally.
