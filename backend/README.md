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
