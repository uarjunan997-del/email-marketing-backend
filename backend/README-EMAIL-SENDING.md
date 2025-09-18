# Email Sending Pipeline

This document explains how the backend handles composing, queuing, and sending emails for campaigns, including A/B tests, throttling, failover, and tracking.

## High-level flow
1) Campaign created/scheduled
- Controller: `campaign/CampaignController` → `POST /api/campaigns` to create, `POST /api/campaigns/{id}/schedule` to schedule, or `POST /api/campaigns/{id}/send-now` to start immediately.
- Service: `campaign/CampaignService.create`, `schedule`, `sendNow`.

2) Recipient resolution & seeding
- On `sendNow` or when scheduled time arrives (`activateDueScheduledCampaigns`):
  - `seedRecipientsIfEmpty` resolves recipients from Contacts by `segment` (or all contacts for the user), dedupes emails, and applies deliverability gates (suppression, bounces, complaints).
  - If A/B test variants exist, recipients are assigned variants using weighted splits.

3) Queueing messages
- `enqueueInitialBatch` creates `SendingQueueItem` records for a first slice (plan-aware batch size), rendering the campaign template with basic placeholders and appending an unsubscribe footer. Recipients move to status `QUEUED`.
- `refillQueueIfNeeded` tops up the queue while sending, keeping a steady number of pending items.

4) Processing the queue
- `processQueueBatch(sender)` pulls pending queue items and attempts delivery using the provided sender function.
  - Transient failures are retried with exponential backoff; permanent failures are marked FAILED.
  - Sent counters are aggregated per campaign; status transitions to `SENT` when all recipients have been delivered.

5) Delivery providers & failover
- Providers implement `email/EmailProvider` with `send(to, subject, html)`.
- `email/FailoverEmailService` iterates over configured providers until success or a permanent/config error occurs.
- Included providers:
  - `email/OracleEmailDeliveryService` (stubbed; replace with OCI SDK integration)
  - `email/SecondaryEmailProvider` (simple fallback that succeeds)

6) Tracking opens and clicks (public endpoints)
- Controller: `tracking/TrackingController` (base `/track`)
  - `GET /track/open?c={campaignId}&r={recipient}` returns a 1x1 GIF and records an open.
  - `GET /track/click?c={campaignId}&r={recipient}&u={url}` records a click and redirects to the target URL.
- Service hooks: `CampaignService.recordOpen`, `CampaignService.recordClick` update campaign metrics and recipient engagement scores.

7) Analytics and A/B testing
- `CampaignService.analytics` computes open rate, CTR, revenue (placeholder), and ROI.
- `evaluateAbWinnerIfEligible` selects a winning variant after a threshold (30% sent) using a composite metric and allocates remaining recipients to the winner.

## Key classes
- `campaign/CampaignService`: core orchestration (seeding, queueing, processing, analytics, tracking hooks).
- `campaign/SendingQueueRepository` and `campaign/CampaignRecipientRepository`: persistence for queue and recipients.
- `template/EmailTemplateService`: renders templates via `TemplateRenderingService` and sanitizes HTML.
- `email/FailoverEmailService`: wraps providers for resilient delivery.
- `email/OracleEmailDeliveryService`, `email/SecondaryEmailProvider`: provider implementations.

## Rate limits & quotas
- `determineInitialBatchSize` and plan type (FREE/PRO/PREMIUM) determine initial queue size.
- `exceedsDailyQuota` enforces per-plan daily send quotas, counting `SENT` items since day start.

## Error handling & retries
- Transient errors (network/timeouts) → retry with exponential backoff up to a cap.
- Permanent failures (4xx) → mark FAILED and do not retry.
- Config errors short-circuit provider failover.

## Template rendering
### Dynamic merge & variable bindings
- During queue creation (`enqueueInitialBatch` and refills) `CampaignService` now performs full dynamic rendering using `TemplateRenderingService.renderDetailed`.
- Data map includes:
  - `campaign_id`, `recipient_email`, `variant_code` (if A/B), `unsubscribeUrl` (tokenized per recipient)
  - Contact fields (`first_name`, `last_name`, plus the full `contact` object and raw `custom_fields` JSON)
  - Any system/default variables (e.g., `unsubscribe_url`, `first_name`) falling back to defaults or a generic "Subscriber".
- Template variable bindings (CONTACT_COLUMN / CUSTOM_FIELD / SYSTEM / STATIC) are resolved server-side; missing required variables (future flag) are logged but do not block send.
- `{{unsubscribe}}` placeholder is replaced inside `TemplateRenderingService` after variable substitution; explicit `unsubscribe_url` variable also available.
- Transformation pipeline supports inline modifiers: `{{first_name|default:Friend|upper|trim}}` and date formatting `{{event_date|date:yyyy-MM-dd}}`.
- Conditional & list blocks:
  - `<!-- IF some.variable --> ... <!-- ENDIF -->`
  - `<!-- FOR item IN items --> ... {{item}} ... <!-- ENDFOR -->`

### MJML
- If MJML source is stored, it is rendered to HTML on save via `MjmlRenderService` and persisted (`lastRenderedHtml` and `html`).

## Unsubscribe Flow
1. Each queued email includes an unsubscribe link generated in `CampaignService` using `UnsubscribeTokenService` and the property `public.base-url` (default `https://example.com`).
2. Link format: `${public.base-url}/public/unsubscribe?token=...`
3. Frontend public route `/public/unsubscribe` loads `UnsubscribePage` (no auth) which:
  - Calls `GET /public/unsubscribe/verify?token=...` to validate and display the target email.
  - On confirmation calls `POST /public/unsubscribe/confirm` with `{ token, reason? }`.
4. Backend controller `PublicUnsubscribeController`:
  - Validates signature; if valid marks the contact `unsubscribed=true` (if found) and adds a suppression entry (reason `UNSUBSCRIBE_LINK` by default).
  - Persists an `UnsubscribeEvent` row capturing `user_id`, `campaign_id`, `email`, `reason`, `created_at` (migration V43) for analytics / compliance audit.
5. Future enhancements: rate-limit confirmations, preference center categories, expose unsubscribe analytics endpoint.

## Scheduling
- `schedule` writes a `CampaignSchedule` row; `activateDueScheduledCampaigns` finds due campaigns and kicks off sending (status → SENDING, seed + enqueue).

## Extending delivery
- Add a new provider by implementing `EmailProvider` and declaring it as a Spring bean; it will be picked up by `FailoverEmailService` in iteration order.
- Replace `OracleEmailDeliveryService` stub with real OCI Email Delivery SDK calls (request signing, sender domains, suppression rules).

## Operational notes
- Queue processor: call `CampaignService.processQueueBatch(failoverService::send)` from a scheduled task or worker.
- Monitoring: use `/api/campaigns/{id}/progress` or SSE `/api/campaigns/{id}/progress/stream` for live progress.
- Deliverability: suppression/bounce/complaint repositories are consulted before seeding recipients.

## Configuration
- OCI Email Delivery (stubs):
  - `oracle.email.delivery.endpoint`
  - `oracle.email.delivery.compartment.ocid`
  - `oracle.email.delivery.sender`
  - `oracle.email.delivery.api.key` (placeholder; replace with SDK auth)

## Example queue processor (pseudo)
```
@Component
@RequiredArgsConstructor
public class QueueWorker {
  private final CampaignService campaigns;
  private final FailoverEmailService failover;

  @Scheduled(fixedDelay = 5000)
  public void tick(){
    int sent = campaigns.processQueueBatch((item, track) -> {
      var res = failover.send(item.getRecipient(), item.getSubject(), item.getBody());
      // You can branch on res.type() if you need custom handling
    });
  }
}
```
