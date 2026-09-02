# ClassHub Notification Orchestration Architecture

This document describes the provider-agnostic notification foundation in the ClassHub API backend. Business logic publishes academic events; external delivery (Email, WhatsApp) is deferred via an outbox table and processed by a scheduled worker.

## Event types

| Type | When emitted |
|------|----------------|
| `COURSEWORK_PUBLISHED` | Published coursework transitions DRAFT → PUBLISHED |
| `ANNOUNCEMENT_PUBLISHED` | Published announcement transitions DRAFT → PUBLISHED |
| `COURSEWORK_DEADLINE_REMINDER` | Scheduler finds deadline in reminder window (7/3/1/0 days, or overdue once) |
| `COURSEWORK_DEADLINE_CHANGED` | Published coursework `dueAt` changes |
| `COURSEWORK_CANCELLED` | Published coursework transitions to CANCELLED |
| `COURSEWORK_INSTRUCTIONS_UPDATED` | Published coursework instructions change with `notifyStudentsOfUpdate=true` |

## Channels

`NotificationChannel` enum:

- **IN_APP** — always enabled; stored in `notifications` table (existing inbox APIs).
- **EMAIL** — outbox delivery through Brevo's transactional email API.
- **WHATSAPP** — outbox delivery through the provider-neutral adapter boundary; SopraSend is the configured provider and remains disabled by default.

## Delivery statuses

`DeliveryStatus`: `PENDING`, `PROCESSING`, `SENT`, `FAILED`, `SKIPPED`.

External channels are marked `SKIPPED` at creation when the provider is disabled or the user is ineligible. Failed sends remain retryable until `max-attempts`.

## Publish flow

```
Domain action (publish coursework/announcement)
    → NotificationOrchestrator
    → Resolve active students
    → NotificationTemplateService (channel-neutral content)
    → Per student: notification row + notification_deliveries rows (transaction)
    → commit
    → NotificationDeliveryWorker (scheduled) processes PENDING external/IN_APP deliveries
```

Publishing succeeds even if Email/WhatsApp providers are unavailable.

## Reminder flow

`DeadlineReminderJob` runs hourly (configurable). `DeadlineReminderScheduler`:

1. Loads published coursework with deadlines.
2. Uses configured timezone (`Africa/Kampala` default) for calendar-day windows.
3. Skips students with `COMPLETED` progress.
4. Records idempotency in `notification_reminder_log` (unique student + coursework + reminder type).
5. Emits `COURSEWORK_DEADLINE_REMINDER` via orchestrator.

Default windows: 7, 3, 1, 0 days before deadline; one `OVERDUE_ONCE` after deadline passes.

## Outbox / delivery worker

Table: `notification_deliveries` (Flyway V10).

Worker (`NotificationDeliveryWorker`):

- Batch size: 50 (default)
- Max attempts: 3
- Backoff: 1m, 5m, 30m
- Uses `FOR UPDATE SKIP LOCKED` on PostgreSQL for concurrent safety
- Adapter interface: `NotificationDeliveryAdapter`

Provider adapters store Brevo's `messageId` or SopraSend's accepted `message_id`. Credentials and provider response bodies are never logged.

## Idempotency

- Notifications: unique `(recipient_user_id, type, reference_id, occurrence_key)`
- Reminders: unique `(student_user_id, coursework_id, reminder_type)` in `notification_reminder_log`
- Deadline changes use occurrence keys encoding old/new deadline instants

## Preferences

Table: `notification_preferences` — `email_enabled` (default true), `whatsapp_enabled` (default false, explicit opt-in for WhatsApp).

API (STUDENT only):

- `GET /api/v1/me/notification-preferences`
- `PUT /api/v1/me/notification-preferences`

IN_APP is mandatory and not configurable.

**Email verification** is not implemented yet; eligibility uses presence of email only. Future verification can extend `NotificationEligibilityService`.

## Configuration / environment variables

| Variable | Default | Purpose |
|----------|---------|---------|
| `CLASSHUB_NOTIFICATIONS_ENABLED` | `true` | Master switch |
| `CLASSHUB_NOTIFICATION_TIMEZONE` | `Africa/Kampala` | Deadline formatting and reminder day boundaries |
| `CLASSHUB_WEB_BASE_URL` | `http://localhost:5173` | Compose action URLs in adapters |
| `CLASSHUB_EMAIL_NOTIFICATIONS_ENABLED` | `false` | Global Email channel |
| `SOPRASEND_ENABLED` | `false` | SopraSend WhatsApp provider switch |
| `CLASSHUB_DEADLINE_REMINDERS_ENABLED` | `true` | Reminder scheduler |
| `BREVO_API_KEY` | empty | Brevo transactional-email API key |
| `BREVO_SENDER_NAME` | `ClassHub` | Sender display name |
| `BREVO_SENDER_EMAIL` | empty | Sender address verified in Brevo |
| `BREVO_REPLY_TO_EMAIL` | empty | Optional reply-to address |
| `BREVO_SANDBOX` | `false` | Validate and drop email rather than delivering it |
| `SOPRASEND_BASE_URL` | `https://wa.sopraent.com` | SopraSend API base URL |
| `SOPRASEND_API_KEY` | empty | Backend-only SopraSend bearer key |
| `SOPRASEND_DEVICE_ID` | empty | Connected SopraSend device ID |

Spring properties under `classhub.notifications.*` in `application.yml`.

## Timezone

All friendly deadline strings and reminder “deadline day” logic use `classhub.notifications.timezone`, not the JVM default zone.

## Security / privacy

- In-app notifications remain user-scoped (existing endpoints).
- Delivery records are internal only (no public API).
- Reminder eligibility uses private student progress; not exposed to Class Rep.
- Preference endpoints require STUDENT role; CSRF applies to mutations.

## Brevo setup

1. Authenticate a sending domain or verify a sender address in Brevo.
2. Create a Brevo API key with transactional-email access.
3. Fill the `BREVO_*` entries in `.env`. `BREVO_SANDBOX=true` can validate requests without delivery.
4. Set `CLASSHUB_EMAIL_NOTIFICATIONS_ENABLED=true` and restart the API.

ClassHub renders escaped HTML itself, so a Brevo dashboard template ID is not required. Brevo API acceptance is recorded with its returned `messageId`.

## SopraSend WhatsApp setup

1. Connect a dedicated WhatsApp Business number as a SopraSend device.
2. Create a backend-only SopraSend API key and record the device ID.
3. Fill the `SOPRASEND_*` entries in `.env` without committing the key.
4. Explicitly opt in one controlled test student before setting `SOPRASEND_ENABLED=true`.
5. Students need a supported international-format phone number and must opt in through notification preferences.

## Remaining work

- Email verification gate for external Email
- WhatsApp phone verification
- Provider delivery-status webhooks (`SENT` currently means provider accepted, not confirmed inbox/handset delivery)
- Delivery log retention policy
- Per-event preference matrix (optional)
- Admin operational API for delivery status (optional)

## Data retention

Delivery rows are designed for future retention jobs; no automatic deletion in MVP.
