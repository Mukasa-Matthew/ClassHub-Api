# WhatsApp provider integration preparation

## 1. Purpose

This document prepares ClassHub for a future real WhatsApp provider integration without selecting or enabling a provider. It records the current implementation, the provider-neutral boundary to preserve, and the security, delivery, testing, and rollout requirements for a later focused implementation.

This document does **not** enable WhatsApp, add a provider client, define a SopraSend wire contract, add secrets, add a webhook, or change production behavior. ClassHub remains the source of truth; WhatsApp is only a delivery channel.

## 2. Current ClassHub WhatsApp status

The notification foundation is provider-aware at the delivery edge and provider-agnostic in the domain:

- `NotificationChannel` contains `IN_APP`, `EMAIL`, and `WHATSAPP`.
- `NotificationOrchestrator` creates the in-app notification and one `notification_deliveries` row per channel.
- `NotificationEligibilityService` applies role/status, provider-enabled, preference, and contact-presence checks before external delivery.
- `notification_preferences.whatsapp_enabled` defaults to `false`; missing preference rows resolve to WhatsApp disabled.
- `NotificationDeliveryWorker` processes pending outbox rows in bounded batches through `NotificationDeliveryAdapter` implementations.
- `MetaWhatsAppNotificationDeliveryAdapter` is the current concrete WHATSAPP adapter. It can make Meta Graph API requests when enabled and configured, but `CLASSHUB_WHATSAPP_NOTIFICATIONS_ENABLED` defaults to `false`.
- The current database status model is `PENDING`, `PROCESSING`, `SENT`, `FAILED`, or `SKIPPED`. It does not yet model provider acceptance separately from handset delivery/read states.
- Delivery rows can store `provider_message_id`, safe error code/message, attempt count, and retry timestamps.
- There is no WhatsApp webhook endpoint or generic provider-webhook foundation.
- Coursework and announcement mutations are audited by their domain services. Notification delivery attempts are not written to the administrative audit log. Provider credentials, authorization headers, message bodies, and phone numbers must never be added to audit metadata.

The existing Meta adapter is not a SopraSend contract and must not be repurposed by adding SopraSend-specific conditionals.

## 3. Existing architecture

The current and intended flow is:

```text
ClassHub domain event
        ↓
NotificationOrchestrator
        ↓
notifications + notification_deliveries outbox rows
        ↓
WHATSAPP channel eligibility
        ↓
WHATSAPP delivery adapter
        ↓
WhatsApp provider port (future)
        ↓
Provider adapter (future)
        ↓
External WhatsApp provider
```

Actual components reused by this design:

- `NotificationOrchestrator`: accepts coursework, announcement, deadline-change, cancellation, instruction-update, and deadline-reminder events.
- `NotificationRecipientResolver`: resolves active `STUDENT` and `CLASS_REP` memberships for the relevant academic class.
- `NotificationEligibilityService`: protects preferences, active/student-like status, provider flags, contact presence, and completed-coursework reminder suppression.
- `NotificationTemplateService` and `NotificationMessageResolver`: produce channel-neutral safe message content.
- `NotificationDelivery` and `NotificationDeliveryRepository`: persistent outbox state and PostgreSQL `FOR UPDATE SKIP LOCKED` batch locking.
- `NotificationDeliveryScheduler` and `NotificationDeliveryWorker`: scheduled, isolated, bounded processing.
- `NotificationDeliveryAdapter`: existing channel adapter interface.
- `NotificationPreferenceService`: student-owned preference reads/updates.
- `notification_reminder_log`: reminder idempotency.

IN_APP remains mandatory and independent. An external provider failure must never delete, invalidate, or hide the in-app notification.

## 4. Provider abstraction

Do not rename `NotificationDeliveryAdapter` merely to accommodate one provider. It is the existing channel-level extension point and should remain stable.

During the later integration, introduce a dedicated provider port behind the WHATSAPP channel adapter, conceptually:

```java
interface WhatsAppProvider {
    WhatsAppSendResult send(WhatsAppSendCommand command);
}
```

Possible implementations may include:

- `SopraSendWhatsAppProvider`
- `OfficialMetaWhatsAppProvider`
- `DisabledWhatsAppProvider`

Names are illustrative and should be aligned with the implementation conventions at integration time. The ClassHub domain, orchestrator, recipient resolver, and preference services must depend on neither SopraSend nor its payload types.

### Provider responsibilities

A provider adapter should:

- receive a normalized canonical destination phone number;
- receive a safe, already-selected message body and action link;
- transform canonical values into the provider-specific request format;
- send the request;
- return the provider message/reference ID when available;
- distinguish accepted/queued submission from final sent/delivered/read outcomes;
- return structured, sanitized failure information, including transient/permanent classification where reliable;
- convert provider and network exceptions into safe results without leaking credentials or raw sensitive payloads.

A provider adapter must not:

- decide which students receive notifications;
- query coursework, announcements, users, preferences, or class membership;
- generate ClassHub domain events;
- decide reminder eligibility;
- implement ClassHub authorization or business rules;
- expose provider request/response models beyond the provider boundary.

## 5. SopraSend candidate provider

SopraSend is a candidate provider, not a committed dependency. The expected conceptual configuration is:

```dotenv
SOPRASEND_ENABLED=false
SOPRASEND_BASE_URL=https://wa.sopraent.com
SOPRASEND_API_KEY=<secret>
SOPRASEND_DEVICE_ID=<backend-side-device-id>
```

These variables are documentation-only proposals. They must not be added to runtime configuration until the integration phase defines validated configuration properties and activation rules.

The following are **TO VERIFY AGAINST SOPRASEND API DOCUMENTATION**:

- exact send endpoint and HTTP method;
- exact authentication mechanism and headers;
- exact request and response JSON;
- whether message acceptance is synchronous or queued;
- provider message/reference ID location and guarantees;
- device status and health endpoints;
- error codes and transient/permanent classification;
- rate limits and retry headers;
- webhook URL registration process;
- webhook signature algorithm, secret, and headers;
- webhook event names and payload fields;
- duplicate-event identifier and ordering guarantees;
- destination formatting requirements;
- timeout and idempotency-key support;
- SMS fallback behavior and whether it can be disabled.

No SopraSend endpoint, payload, response, signature, or rate-limit contract should be inferred before official documentation is reviewed.

## 6. Configuration and fail-safe behavior

The existing global notification and channel configuration remains authoritative. A future provider selection should fit beneath `classhub.notifications.whatsapp` rather than add provider knowledge to domain services.

Expected behavior:

- `SOPRASEND_ENABLED=false`: ClassHub starts and operates normally; SopraSend is never called.
- WhatsApp preference disabled: no WhatsApp provider call occurs.
- Provider enabled with incomplete credentials/device configuration: fail safely before any malformed request.
- Invalid base URL or unsupported scheme: reject configuration or disable that provider safely.
- Provider unavailable at runtime: isolate the failed delivery and preserve IN_APP.

The existing Meta adapter returns `PROVIDER_NOT_CONFIGURED` when enabled with incomplete configuration. For SopraSend, startup validation is preferable when the provider is explicitly enabled because misconfiguration is deterministic and operator-actionable. If project conventions favor runtime fallback, the provider must still remain disabled and emit a sanitized operational error; it must never attempt partial requests. This decision is **TO VERIFY DURING INTEGRATION** against the final provider-selection configuration design.

## 7. Secrets and security policy

Provider API keys are backend-only secrets. They must never be:

- returned through REST endpoints;
- exposed to React or any mobile client;
- stored in cookies, local storage, or session storage;
- logged, including through exception objects or HTTP-client debug logging;
- committed to Git or placed in `.env.example` with a real value;
- stored in database seed data;
- included in API error responses;
- included in administrative audit metadata.

Production secrets must come from environment variables or an approved secret manager. `SOPRASEND_DEVICE_ID` is configuration rather than an authentication secret, but it remains backend-side and should not be exposed without a concrete operational need.

Webhook processing must use provider-specific signature/secret verification and must not use normal browser-session authentication as a substitute.

## 8. Phone-number normalization

### Current implementation

- `users.phone_number` is `VARCHAR(32)` and nullable.
- `UserService.normalizePhoneNumber` currently trims surrounding whitespace only; it does not validate or canonicalize country codes.
- The current Meta adapter removes all non-digits and accepts a result between 8 and 15 digits. That transformation is adapter-local and is not a canonical database guarantee.
- Eligibility currently checks only that the phone field is nonblank.

### Target strategy

ClassHub should store a provider-neutral E.164-style international representation, including the leading plus sign:

```text
+2567XXXXXXXX
```

Provider adapters may transform that canonical value at their boundary. For example, if a verified provider contract requires no plus sign:

```text
ClassHub canonical: +2567XXXXXXXX
Provider format:    2567XXXXXXXX
```

The database must not become SopraSend-specific.

For a controlled Uganda-first integration, explicitly define and test handling for:

- `07XXXXXXXX`
- `7XXXXXXXX`
- `2567XXXXXXXX`
- `+2567XXXXXXXX`

The integration phase must decide whether these inputs are accepted and normalized or rejected with corrective guidance. Do not silently implement broad international parsing without a vetted library and product requirements. Validation should occur before provider dispatch; the provider adapter should perform only the final verified provider transformation.

## 9. Notification preferences and consent

WhatsApp is explicit opt-in:

- database default: `whatsapp_enabled = false`;
- missing preference record: resolved as WhatsApp disabled;
- preference API: `GET` and `PUT /api/v1/me/notification-preferences` for student-like users;
- existing users must not be automatically opted in;
- a nonblank phone number does not imply consent;
- provider enablement does not override an individual preference.

The future frontend must clearly show the current state and allow a student to enable or disable WhatsApp notifications. Before enabling, the UI should explain the academic-message purpose and any phone verification requirement. Disabling must prevent creation of an eligible WhatsApp delivery/provider call for future events.

## 10. Message-content policy

WhatsApp should carry concise academic alerts and an instruction or link to open ClassHub. Appropriate content includes:

- coursework reminders;
- coursework deadline changes;
- announcement alerts;
- semester reminders;
- other short class-related notifications.

Do not send:

- passwords, reset secrets, session information, or CSRF tokens;
- provider/API keys;
- private lecture notes or AI-generated note content;
- private coursework progress;
- internal identifiers or audit data;
- sensitive administrative data;
- entire ClassHub records when a short alert is sufficient.

Example coursework reminder:

```text
ClassHub Reminder

Web Programming coursework is due tomorrow at 11:59 PM.

Open ClassHub for details.
```

Example announcement:

```text
ClassHub

A new class announcement has been posted.

Open ClassHub to view it.
```

Use the existing `NotificationTemplateService`/`NotificationMessage` model where suitable. Do not add a separate template engine solely for SopraSend.

## 11. Delivery lifecycle

The provider lifecycle must distinguish:

- `QUEUED` / `ACCEPTED`: provider accepted responsibility for processing the request;
- `SENT`: provider reports the message sent onward;
- `DELIVERED`: provider reports delivery to the destination/device;
- `READ`: provider reports a read receipt;
- `FAILED`: terminal failure or exhausted retry policy.

Current ClassHub statuses do not represent all of these. `DeliveryResult.sent(...)` causes the worker to mark the row `SENT` immediately after a provider returns an ID. Therefore current `SENT` means provider acceptance for external adapters, not confirmed handset delivery. This must not be described as `DELIVERED`.

Future flow:

```text
outbox PENDING
  → provider request
  → provider ACCEPTED/QUEUED
  → provider message ID associated with delivery
  → verified webhook/status update
  → SENT / DELIVERED / READ or FAILED
```

The integration phase must decide whether to extend `DeliveryStatus`, add a separate provider-status field/history, or introduce a delivery-event table. No migration is made by this preparation task.

## 12. Retry, failure, and fallback behavior

Current worker behavior:

- batch size defaults to 50;
- maximum attempts defaults to 3;
- retry backoff defaults to 1, 5, and 30 minutes;
- pending rows are locked with `FOR UPDATE SKIP LOCKED`;
- adapter exceptions are caught per delivery;
- after bounded attempts the row becomes `FAILED`;
- disabled/ineligible/no-contact deliveries are `SKIPPED`.

A future provider result should classify failures so only potentially transient failures are retried.

Potentially retryable:

- timeout or connection interruption;
- temporary provider/device outage;
- HTTP 5xx;
- rate limiting when retry guidance permits it.

Normally permanent and not blindly retryable:

- invalid destination;
- invalid/expired credentials;
- invalid or unknown device ID;
- unsupported destination or recipient not on WhatsApp;
- rejected request caused by invalid content/configuration.

Retries must remain bounded. One failed WhatsApp delivery must not stop other rows in the batch or crash the scheduler. Provider-side SMS fallback is not part of ClassHub architecture for this phase. IN_APP remains the reliable source-of-truth fallback regardless of WhatsApp outcome.

Expected isolated outcomes:

- device offline: safe transient/permanent result according to verified provider semantics; no worker crash;
- provider unavailable or timeout: bounded retry;
- invalid API key/device: sanitized terminal configuration/authentication failure;
- destination not on WhatsApp: terminal destination failure;
- rate limited: honor verified retry guidance and bounded throughput;
- unexpected response: sanitized failure, no raw body or credentials in logs.

## 13. Webhook architecture

A possible future endpoint is:

```text
POST /api/v1/webhooks/whatsapp/soprasend
```

It does not exist and must not be implemented until the provider contract is verified.

Requirements:

- verify the provider signature before parsing or applying business state;
- reject missing/invalid signatures;
- do not require a normal ClassHub user session;
- authenticate using the verified provider signature/secret mechanism;
- validate event type, message ID, status, timestamps, and bounded payload size;
- never trust arbitrary provider status payloads;
- map the provider message ID to exactly the intended ClassHub delivery;
- handle events idempotently;
- make duplicate and out-of-order events safe;
- prevent status regression (for example, `READ` back to `SENT`);
- return safe errors without echoing secrets or raw payloads.

Conceptual mappings:

- provider `sent` → ClassHub sent/provider status;
- provider `delivered` → delivered status;
- provider `read` → read status;
- provider `failed` → failed status plus sanitized reason.

Exact SopraSend event names, signature headers, payload fields, and ordering guarantees are **TO VERIFY DURING INTEGRATION**.

## 14. Observability

Safe operational telemetry may include:

- provider name;
- ClassHub delivery attempt ID;
- notification/event type;
- channel;
- attempt number;
- HTTP status category rather than raw response body;
- success/failure and sanitized error code;
- provider message ID where operationally appropriate;
- duration and retry scheduling outcome.

Never log:

- API keys or authorization headers;
- session cookies or CSRF tokens;
- full provider request/response bodies by default;
- private message content unnecessarily;
- unmasked phone numbers.

Mask phone numbers in operational logs, for example:

```text
+2567****1234
```

The current worker logs the delivery ID and exception message for unexpected runtime exceptions. A future HTTP adapter must ensure propagated exception messages cannot contain authorization headers, raw payloads, or provider secrets.

## 15. Rate limiting and abuse protection

The integration must remain an event-driven academic notification mechanism, not a bulk-spam feature.

Controls should include:

- existing bounded worker batch size and retry count;
- provider rate-limit awareness and verified retry guidance;
- existing notification occurrence uniqueness and reminder idempotency;
- per-user preference checks;
- duplicate provider-request suppression/idempotency where supported;
- reasonable reminder frequency;
- operational alerting for repeated failures or throttling.

Connecting a provider must not grant Class Reps an unrestricted WhatsApp broadcast endpoint. Recipient resolution and permitted academic events remain ClassHub business decisions.

## 16. Future testing strategy

### Configuration

- disabled provider starts normally and produces no provider call;
- enabled provider with missing API key fails safely;
- enabled provider with missing device ID fails safely;
- custom base URL is validated and used only by the provider adapter.

### Sending

- successful queued/accepted send;
- provider message ID is associated with the delivery;
- invalid number is terminal and sanitized;
- device offline response is classified correctly;
- authentication failure is terminal and sanitized;
- rate limit follows verified bounded retry behavior;
- timeout is retried within bounds;
- provider 5xx is retried within bounds.

### Preferences

- WhatsApp disabled for the student results in no provider call;
- WhatsApp enabled plus eligible contact results in one eligible provider call;
- missing preference row remains disabled;
- IN_APP remains present for both success and failure paths.

### Security

- API key is never logged or returned;
- authorization headers and raw private payloads are never logged;
- invalid webhook signature is rejected before state mutation;
- duplicate valid webhook is idempotent;
- webhook cannot update an unrelated delivery.

### Delivery lifecycle

- queued/accepted is not treated as delivered;
- delivered webhook advances status;
- failed webhook records sanitized terminal failure;
- read webhook advances status without regression;
- duplicate/out-of-order webhooks are safe.

### Resilience

- one failed WhatsApp message does not stop the remaining outbox batch;
- maximum attempts terminate retrying;
- concurrent workers do not duplicate locked work;
- IN_APP remains available when WhatsApp fails;
- provider response without a message ID fails safely.

Use mock HTTP integration tests for all provider contracts before any real-number smoke test. No test should use a real API key or contact a real provider.

## 17. Future deployment checklist

- [ ] Dedicated ClassHub WhatsApp number
- [ ] WhatsApp/WhatsApp Business account connected
- [ ] SopraSend device connected
- [ ] API key created
- [ ] Device ID recorded
- [ ] Production secrets configured outside Git
- [ ] WhatsApp preferences verified
- [ ] Test student explicitly opted in
- [ ] Test number normalized
- [ ] Controlled test message successful
- [ ] Failure behavior tested
- [ ] IN_APP fallback verified
- [ ] Logs contain no secrets
- [ ] Rate limits understood
- [ ] Webhook signature mechanism verified before enabling webhooks
- [ ] Provider proxy/TLS/timeout settings reviewed
- [ ] Rollback disables the provider without disabling ClassHub

## 18. Known unknowns and items to verify

Before implementation, obtain and review official SopraSend documentation for every item listed in section 5. Also decide:

- the provider-selection configuration model beneath the existing WHATSAPP channel;
- canonical phone validation library and Uganda-first rules;
- accepted/queued versus final delivery persistence model;
- webhook status precedence and idempotency storage;
- whether SopraSend supports idempotency keys;
- delivery retention and provider-message-ID retention policy;
- operational health-check exposure and authorization;
- whether provider health can distinguish credentials, device existence, and device connectivity without sending a message.

A future provider health capability may verify reachability, credential acceptance, configured device existence, and device connection. It must expose only a safe aggregate status and must never return the API key or provider secrets.

## 19. Future integration sequence

Do not perform these phases as part of this preparation task.

1. **Phase A — SopraSend configuration properties:** add validated, backend-only provider configuration with disabled defaults.
2. **Phase B — SopraSend outbound adapter:** implement the provider port using the verified endpoint/authentication/payload contract.
3. **Phase C — Phone normalization/validation:** establish canonical E.164-style storage/input rules and provider transformation.
4. **Phase D — Connect to the existing WHATSAPP outbox channel:** select the provider behind the current delivery architecture without leaking provider types into the domain.
5. **Phase E — Mock HTTP integration tests:** cover success, errors, timeouts, retries, redaction, and preference gating.
6. **Phase F — Controlled real-number smoke test:** use a dedicated opted-in test student and number in a controlled environment.
7. **Phase G — Webhook delivery-status support:** implement verified signatures, idempotency, provider-ID mapping, and lifecycle updates.
8. **Phase H — Production monitoring/rate-limit hardening:** add safe metrics, alerting, throughput controls, retention, and operational runbooks.

