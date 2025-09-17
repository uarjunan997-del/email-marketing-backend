# Email Marketing Backend API Reference

This document provides a comprehensive overview of the backend features, domains, and REST endpoints. All authenticated endpoints require a Bearer JWT unless marked as public.

Base URL: /api (unless otherwise noted)
Auth: Bearer <JWT>

## Auth
- Controllers: `auth/` package (AuthController, AdvancedAuthController)
- Features: login, refresh tokens, email verification, password reset; JWT via Spring Security.

Key endpoints (indicative; see controllers for exact payloads):
- POST /auth/login → { token, refreshToken }
- POST /auth/refresh → { token }
- POST /auth/register
- POST /auth/verify
- POST /auth/reset-password

## Subscription & Plans
- Controller: `controller/SubscriptionController` (simple starter)
- Service: `subscription/SubscriptionService`
- Entities: `subscription/User`, `subscription/Subscription`, `subscription/Plan`

Endpoints:
- POST /subscription/start → Start or renew a subscription for a user.

More docs: `README-SUBSCRIPTION.md`.

## Dashboard
- Controller: `dashboard/DashboardController`
- Endpoints (all under /api/dashboard):
  - GET /overview → high-level metrics
  - GET /campaigns/recent → recent campaigns
  - GET /analytics/trends?period=30d → timeseries points
  - GET /contacts/summary → contact metrics snapshot
  - GET /performance/top-campaigns → top performers
  - GET /deliverability/score → { reputationScore }
  - GET /usage/limits → plan usage/limits
  - POST /admin/evict (admin) → evict cache for current user

## AI-assisted Templates
- Controller: `ai/AiController`
- Purpose: Asset-aware AI email HTML generation with brand colors.

Endpoints (under /api/ai):
- POST /generate-template-with-assets (multipart/form-data)
  - Fields: templateId?, prompt, templateType, style?, brandColors? (JSON array ["#hex", ...])
  - Files: assets[0..n] with assetNames[0..n], assetTypes[0..n] (logo|background|content|brand|image)
  - Returns: { template { id, html, assetUrls, metadata }, suggestions[], processingTime }
- POST /asset-suggestions (JSON) → { suggestions[] }

Behavior:
- When templateId is passed, assets are deduped and stored as TemplateAssets; generated HTML is persisted to the template and tagged ai-draft.
- Brand palette influences CTA/background colors; placeholders like {{logo_url}}, {{hero_image}}, {{content_image_n}} are used in HTML, and mapped to real URLs in assetUrls.

## Templates
- Controller: `template/EmailTemplateController`
- Service: `template/EmailTemplateService`
- Storage: Oracle DB; assets optionally in OCI Object Storage via `storage/OciObjectStorageService`.

Endpoints (under /api/templates):
- GET / → list templates for current user
- GET /accessible → list templates accessible to user
- GET /shared → list publicly shared templates
- POST / → create template { name, html, categoryId?, description?, tags?, shared? }
- GET /{id} → get template
- PUT /{id} → update template
- DELETE /{id} → delete template (cascades: versions, variables, assets; deletes OCI objects)
- POST /{id}/duplicate → duplicate template → { newId }
- POST /{id}/preview → render with variables → { html }
- POST /{id}/test-send → stub test send → { result }
- GET /{id}/versions → list version history
- GET /{id}/variables → list variables
- POST /{id}/variables → create variable
- DELETE /{id}/variables/{varId} → delete variable
- GET /{id}/assets → list assets for template
- POST /{id}/assets (multipart) → upload generic asset
- POST /{id}/assets/images (multipart) → upload to images folder
- POST /{id}/assets/logos (multipart) → upload to logos folder
- POST /{id}/assets/brand (multipart) → upload to brand folder
- GET /{id}/assets/{assetId}/url?expiryMinutes=5 → temporary read URL if OCI is enabled

Notes:
- HTML is sanitized to allow style/class/id and capped to 200k chars in `EmailTemplateService.validateHtml`.
- When MJML source is provided, it is rendered to HTML before save.

## Campaigns
- Controller: `campaign/CampaignController`, `campaign/CampaignProgressSseController`
- Service: `campaign/CampaignService`

Endpoints (under /api/campaigns):
- GET / → list campaigns
- POST / → create campaign { name, segment, templateId, subject, preheader, scheduledAt? }
- GET /{id} → get campaign
- PUT /{id} → update campaign
- POST /{id}/schedule → schedule send window
- POST /{id}/send-now → immediate send
- POST /{id}/cancel → cancel
- GET /{id}/preview → preview payload for a sample recipient
- POST /{id}/ab-test → configure A/B variants
- GET /{id}/progress → pull progress snapshot { status, sent, opens, clicks }
- GET /{id}/progress/stream → Server-Sent Events stream of progress
- GET /{id}/analytics → metrics summary for campaign
- POST /{id}/analyze → trigger backend analysis (spam words, link checks, etc.)
- GET /summary/analytics → totals/trends across user’s campaigns

Tracking (public):
- Controller: `tracking/TrackingController`
- Base: /track
  - GET /open?c={campaignId}&r={recipientKey} → 1x1 tracking pixel
  - GET /click?c={campaignId}&r={recipientKey}&u={url} → click redirect and record

## Contacts
See `backend/README-CONTACTS-API.md` for full details.

## Deliverability
- Controllers: `deliverability/UnsubscribeController`, `deliverability/BounceComplaintController`
- Public endpoints:
  - POST /public/unsubscribe/{campaignId}?email=...
  - POST /public/deliverability/bounce
  - POST /public/deliverability/complaint

## Analytics & ROI
- Packages: `analytics/`, `roi/`
- ROI Controller (under /api):
  - See `roi/RoiController` exposing aggregate ROI metrics.
- Analytics data models: events, daily aggregates, industry benchmarks.

## E-commerce integration
- Controllers: `webhook/EcommerceWebhookController` (public)
- Services: `ecommerce/EcommerceOrderScheduler`, `ecommerce/EcommerceOrderTransformService`
- Purpose: import orders and map to contacts/campaigns for revenue attribution.

## Time Series API
- Controllers: `timeseries/TimeSeriesAnalyticsController`, `timeseries/CampaignTimeSeriesController`
- Base: /api/timeseries
  - GET /campaign/{id} → timeline of sends/opens/clicks
  - GET /overview?period=30d → aggregate trends

## Storage
- Service: `storage/OciObjectStorageService`
- Features: putBytes, deleteObject, generateReadUrl; when not configured, falls back to local:// keys.

## Security & Access Control
- JWT security, `security` package.
- Plan gating via `@RequiresPlan` and `@RequiresPaidPlan` with `PlanAccessAspect`.
- Global exception mapping (`exception/GlobalExceptionHandler`).

## Configuration
- application.properties/yml: DB, JWT, CORS, OCI.
- Build: Maven (see `backend/pom.xml`).

## Postman collections
- See `backend/postman/*.postman_collection.json` for example requests.

## Notes for Developers
- All controllers resolve user ID via `subscription/UserRepository` and `@AuthenticationPrincipal`.
- Template assets are cleaned up on template deletion, including remote OCI files.
- AI generator will persist generated HTML into the referenced template when `templateId` is provided.
- Sanitizers allow inline styles but may strip <style> tags in some render flows; prefer inline CSS in saved HTML.
