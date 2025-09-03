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
