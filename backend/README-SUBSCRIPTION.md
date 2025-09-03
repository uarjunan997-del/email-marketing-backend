# Subscription & Oracle Integration Additions

This document describes added starter pieces for subscription management and Oracle services.

## JPA Entities
- `Plan` – pricing & feature metadata
- `Subscription` – user subscription lifecycle (status, period, dates)
- `User` – simplified user holding a `Subscription`

## Repositories
Standard Spring Data repositories for each entity (PlanRepository, SubscriptionRepository, UserRepository).

## Service
`SubscriptionService` – simple start/renew logic that activates a plan for a user (no payment gateway coupling here; extend with Razorpay/Stripe as needed).

## Plan Gating
Use annotations already provided (`@RequiresPlan` / `@RequiresPaidPlan`) in combination with `PlanAccessAspect`. `User` currently exposes `getSubscription()`; adapt your authentication principal resolution to map plan tier to the aspect's `CurrentUserProvider`.

## Oracle Object Storage (Stub)
`OciObjectStorageService` included as a lightweight placeholder. Replace with full implementation using `spring-cloud-oci-starter-storage` if you require actual uploads:

Add dependency:
```
<dependency>
  <groupId>com.oracle.cloud.spring</groupId>
  <artifactId>spring-cloud-oci-starter-storage</artifactId>
  <version>1.3.0</version>
</dependency>
```
Configure credentials via environment / instance principal. Then replace stub methods with real storage operations.

## Oracle DB
To switch to Oracle instead of PostgreSQL edit `application.properties`:
```
spring.datasource.url=jdbc:oracle:thin:@HOST:PORT/SERVICE
spring.datasource.username=APP
spring.datasource.password=APP
spring.datasource.driver-class-name=oracle.jdbc.OracleDriver
```
Include Oracle JDBC driver (already present in original project; add if needed):
```
<dependency>
  <groupId>com.oracle.database.jdbc</groupId>
  <artifactId>ojdbc8</artifactId>
  <version>23.2.0.0</version>
</dependency>
```
If using Flyway with Oracle also add:
```
<dependency>
  <groupId>org.flywaydb</groupId>
  <artifactId>flyway-database-oracle</artifactId>
</dependency>
```

## Frontend & Mobile Subscription API Stubs
- Web: `src/api/subscription.ts` with `createSubscriptionOrder` stub mapping to `/payment/order`.
- Mobile: `src/api/subscription.ts` similar logic using axios + SecureStore token.

You must implement the backend endpoint `/payment/order` (e.g., PaymentController) if you want order creation; adapt the DTOs from the original project for Razorpay or replace with your chosen provider.

## Next Steps
1. Implement authentication principal to expose current user ID for `SubscriptionService` calls.
2. Create REST endpoints for listing plans, starting upgrade, handling webhooks.
3. Flesh out Oracle Object Storage with real SDK operations if needed.
4. Add migrations creating tables `users`, `plans`, `subscriptions`.
5. Add indexing for performance (e.g., unique constraints, plan type lookup).
