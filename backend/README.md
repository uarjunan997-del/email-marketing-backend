# Backend Starter

Spring Boot 3 base including:

- JWT authentication (stateless) with `JwtUtil` and `JwtFilter` wired in `SecurityConfig`.
- Role / Plan gating via AOP annotations (`@RequiresPlan`, `@RequiresPaidPlan`) and `PlanAccessAspect`.
- Global exception handling with structured JSON (`GlobalExceptionHandler`, `ErrorResponse`).
- Feature lock exception pattern (`FeatureLockedException`).
- CORS config + security headers.
- OpenAPI (springdoc) integration (ensure dependency present in `pom.xml`).
- Email templating stub (copy `templates/email` as needed).
- Rate limiting filter (Bucket4j) placeholder (ensure you pull the class if required).

## Minimal Package Structure Suggested

```
com/yourapp/
  config/ (SecurityConfig, JwtUtil, JwtFilter)
  security/ (PlanAccessAspect, annotations, PlanTier, AuthenticationFacade impl)
  exception/ (ErrorResponse, GlobalExceptionHandler, FeatureLockedException, etc.)
  model/ (User, Subscription, Plan, etc.)
  repository/ (UserRepository, SubscriptionRepository...)
  service/ (AuthService, EmailService...)
  controller/ (AuthController, Feature controllers)
```

## Key Environment Properties

```
# application.properties
security.jwt.secret=CHANGE_ME_TO_A_LONG_RANDOM_SECRET
app.security.cors.allowed-origins=http://localhost:3000
spring.datasource.url=jdbc:postgresql://localhost:5432/app
spring.datasource.username=app
spring.datasource.password=app
```

## Quick Start

1. Copy this folder into new repo.
2. Search & replace package `com.expensetracker` -> `com.yourdomain`.
3. Generate a new JWT secret (>= 32 chars).
4. Adjust dependencies in `pom.xml` (remove unused: Oracle, AWS, etc. if not needed).
5. Create initial migration & run `mvn spring-boot:run`.

## Plan Gating Example

Annotate a controller or method:
```
@RequiresPlan(level = PlanTier.PRO)
@GetMapping("/pro/feature")
public ResponseEntity<?> proOnly() {...}
```
Unauthorized plans receive `403 FEATURE_LOCKED` with message instructing upgrade.

## Error Response Format

```
{
  "code": "VALIDATION_ERROR",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/api/resource",
  "details": [ "field1: must not be blank" ]
}
```

## Customization Tips
- Replace email templates and configure SMTP.
- Add refresh tokens if long sessions needed.
- Add roles/authorities to `UserDetails` for fine-grained access.
- Introduce multi-tenancy or organization scoping if needed.

## Contacts: Dynamic Lists (Segments)

Dynamic contact lists are powered by a simple JSON filter stored as a string in `contact_lists.dynamic_query` and evaluated by the backend when you preview or materialize the list.

Supported operators inside `dynamic_query`:
- segmentEquals: exact match on the `contacts.segment` field.
- segmentStartsWith: prefix match (uses `LIKE 'prefix%'`).
- segmentContains: substring match, case-insensitive (uses `LOWER(segment) LIKE '%value%'`).
- unsubscribed: boolean filter on `contacts.unsubscribed` (true or false).

Notes
- Combine operators; they are ANDed together. Example: match segment contains “vip” AND unsubscribed false.
- `dynamic_query` is a JSON string. In HTTP JSON bodies, ensure it’s stringified (escape inner quotes) as shown below.

Endpoints
- Create list: POST `/contacts/lists`
- Update list: PUT `/contacts/lists/{listId}`
- Preview dynamic list: GET `/contacts/lists/{listId}/preview` → returns `{ count, sample[] }`
- Materialize dynamic list: POST `/contacts/lists/{listId}/materialize` → inserts rows into `contact_list_members`

Examples (request bodies)

1) Create a dynamic list with exact segment match

{
  "name": "VIP Members",
  "description": "All contacts in VIP segment",
  "isDynamic": true,
  "dynamicQuery": "{\"segmentEquals\":\"VIP\"}"
}

2) Create using startsWith

{
  "name": "VIP Prefix",
  "description": "Segments starting with VIP",
  "isDynamic": true,
  "dynamicQuery": "{\"segmentStartsWith\":\"VIP\"}"
}

3) Create using contains (case-insensitive)

{
  "name": "Prospects",
  "description": "Segments containing 'prospect'",
  "isDynamic": true,
  "dynamicQuery": "{\"segmentContains\":\"prospect\"}"
}

4) Create using unsubscribed filter

{
  "name": "Unsubscribed",
  "description": "All unsubscribed contacts",
  "isDynamic": true,
  "dynamicQuery": "{\"unsubscribed\":true}"
}

5) Combined filters

{
  "name": "VIP Active",
  "description": "VIP but not unsubscribed",
  "isDynamic": true,
  "dynamicQuery": "{\"segmentContains\":\"vip\",\"unsubscribed\":false}"
}

Preview and materialize
- Preview: `GET /contacts/lists/{listId}/preview` → returns match count and a short sample of contacts.
- Materialize: `POST /contacts/lists/{listId}/materialize` → rebuilds `contact_list_members` from current filters. Use this to freeze a snapshot for campaigns.

## AI Template Meta Storage (Migration V37)

The AI template generator now persists its configuration (prompt, template type, style, brand colors, optional model info) in a JSON CLOB column `AI_META_JSON` on `EMAIL_TEMPLATES`.

Flyway migration: `V37__add_ai_meta_json.sql` adds the column idempotently for Oracle (checks `USER_TAB_COLS`). If Flyway is disabled in your environment, run manually:

```sql
ALTER TABLE email_templates ADD (ai_meta_json CLOB);
```

Endpoint contract:
- GET `/api/templates/{id}/ai-meta` → `{ "json": "{...}" }` (or `null` if unset)
- PUT `/api/templates/{id}/ai-meta` body `{ "json": "{...}" }`

Legacy Support: Older templates that encoded meta inside a tag `aimeta:<base64>` are auto-migrated on first load: the frontend decodes and calls the new PUT endpoint, then stops using the tag.

No indexing is required; access pattern is per template id.

## Template Variables Defaults & Integrity (Migration V38)

When a new template is created the backend now auto-creates a core set of merge variables so the frontend no longer injects them client-side:

```
first_name, last_name, company_name, unsubscribe_url
```

These rows are stored in `template_variables` with `system = 1` (boolean flag introduced in V38) so they can be distinguished from user-defined variables. User-created variables have `system = 0`.

### Uniqueness Constraint
A unique index `ux_template_variables_template_var` enforces one variable name per template (`(template_id, var_name)`). This prevents duplication even under concurrent creation.

### Migration (V38)
Flyway script `V38__template_variable_unique_and_system.sql`:
1. Adds the `SYSTEM` column (NUMBER(1) default 0 NOT NULL)
2. Marks existing default variable names as `system=1`
3. Creates unique index on `(template_id, var_name)`

If running manually (Oracle example):
```sql
ALTER TABLE template_variables ADD (system NUMBER(1) DEFAULT 0 NOT NULL);
UPDATE template_variables SET system = 1 WHERE var_name IN ('first_name','last_name','company_name','unsubscribe_url');
CREATE UNIQUE INDEX ux_template_variables_template_var ON template_variables(template_id, var_name);
```

### API Shape
`GET /api/templates/{id}/variables` now returns each variable with:
```json
{
  "id": 123,
  "name": "first_name",
  "defaultValue": null,
  "required": false,
  "system": true
}
```

### Backfilling Older Templates
Older templates (pre-V38) will only gain default variables on new creation going forward. To backfill existing templates, insert missing rows for each template id using the list above, setting `system=1` and ensuring the unique constraint is preserved.

### Frontend Impact
The editor no longer hardcodes defaults; it renders exactly what the API returns. Any logic relying on defaults must now query the API first.

## Easy Email Structured Design Storage (Migration V39)

The experimental Easy Email editor (feature flag: `VITE_USE_EASY_EMAIL=true` on the frontend) persists its structured block model in a new nullable CLOB column: `EASY_EMAIL_DESIGN_JSON` on `EMAIL_TEMPLATES`.

Flyway migration: `V39__add_easy_email_design_json.sql` (idempotent for Oracle using `USER_TAB_COLUMNS`). If Flyway is disabled, run manually:

```sql
ALTER TABLE email_templates ADD (easy_email_design_json CLOB);
```

Behavior:
- Legacy GrapesJS or raw HTML templates leave the column NULL.
- Save operations from the Easy Email path send both rendered `html` and `easyEmailDesignJson` (structured JSON) in the same existing PUT `/api/templates/{id}` request payload.
- Duplicate endpoint copies this JSON to keep derived templates editable in the structured editor.

Rationale:
- Maintains round‑trip fidelity for block-level editing (future personalization, block A/B, semantic diffing).
- Allows incremental migration: plain HTML remains the source of truth for sending; design JSON augments authoring only.

Future Enhancements (not yet implemented):
- Lightweight `GET /api/templates/{id}/design` endpoint to fetch only the JSON for faster editor hydration.
- Validation & size guard (e.g., reject > 500 KB design JSON to prevent unbounded growth).
- Server‑side diffing to decide whether to version on structural changes vs cosmetic content edits.

If you later remove the experimental editor, you can safely drop the column without affecting send pipelines.


