# ClassHub API reference (MVP)

Lightweight developer documentation for the session-based ClassHub backend.
OpenAPI/Swagger is intentionally not exposed in this phase to avoid public API docs without auth controls.

## Authentication

- Mechanism: server-side HTTP session (cookie `CLASSHUB_SESSION`), not JWT.
- Login: `POST /api/v1/auth/login` with JSON `{ "email", "password" }`.
- Current user: `GET /api/v1/auth/me` (authenticated).
- Logout: `POST /api/v1/auth/logout` (authenticated + CSRF).
- Failed login returns a generic authentication failure (no account enumeration).
- Suspended / disabled accounts cannot authenticate (`isAccountNonLocked` / `isEnabled`).
- Login rate limiting is in-memory (per process). Multi-instance deployments need a shared limiter later.

## CSRF (browser / SPA clients)

- CSRF is enabled for state-changing requests.
- Spring issues an `XSRF-TOKEN` cookie (`HttpOnly=false`) so SPA JavaScript can read it.
- Send the token on mutating requests as header `X-XSRF-TOKEN`.
- Cookie `SameSite=Lax`; `Secure` follows `CLASSHUB_COOKIE_SECURE`.
- Login itself requires CSRF in this API (tests and clients must supply the token).

## CORS

- Credentialed CORS only.
- Allowed origins come from `CLASSHUB_CORS_ALLOWED_ORIGINS` (comma-separated exact origins).
- Never configure `*`. Wildcard origins are rejected by the server.
- Frontend origin example (local): `http://localhost:3000`.

## Roles

| Role | Typical access |
|------|----------------|
| `STUDENT` | Own progress, notifications, private notes, student dashboard |
| `CLASS_REP` | Course units (create/update), coursework lifecycle, announcements, class-rep dashboard |
| `SUPER_ADMIN` | User admin, audit logs, course unit status, admin dashboard, all class-rep capabilities where configured |

Default deny: any unmatched route requires authentication; role rules are explicit.

## Response shape

Success:

```json
{ "data": { } }
```

Paginated success may also include:

```json
{ "data": [ ], "pagination": { "page": 1, "size": 20, "totalElements": 0, "totalPages": 0 } }
```

Error:

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "email must not be blank",
    "timestamp": "2026-08-31T09:00:00Z",
    "path": "/api/v1/auth/login",
    "fieldErrors": [
      { "field": "email", "message": "must not be blank" }
    ]
  }
}
```

`fieldErrors` appears for bean-validation failures. Clients never receive stack traces, SQL, or filesystem paths.

Correlation: every response includes `X-Request-Id` (accepted if safe, otherwise generated).

## Public endpoints

- `GET /health` — process up
- `GET /ready` — DB connectivity
- `POST /api/v1/auth/login`

## Endpoint groups

### Auth
- `POST /api/v1/auth/login`
- `POST /api/v1/auth/logout`
- `GET /api/v1/auth/me`

### Admin users (`SUPER_ADMIN`)
- `GET/POST /api/v1/admin/users`
- `GET /api/v1/admin/users/{id}`
- `PATCH /api/v1/admin/users/{id}/role`
- `PATCH /api/v1/admin/users/{id}/status`

### Audit (`SUPER_ADMIN`)
- `GET /api/v1/admin/audit-logs?page&size&action&actorUserId&entityType`

### Dashboards
- `GET /api/v1/dashboard/student` — STUDENT
- `GET /api/v1/dashboard/class-rep` — CLASS_REP
- `GET /api/v1/dashboard/admin` — SUPER_ADMIN

### Course units
- `GET /api/v1/course-units` — authenticated (students see active only)
- `POST /api/v1/course-units` — SUPER_ADMIN, CLASS_REP
- `PATCH /api/v1/course-units/{id}` — SUPER_ADMIN, CLASS_REP
- `PATCH /api/v1/course-units/{id}/status` — SUPER_ADMIN
- `POST /api/v1/course-units/{id}/cover-image` — multipart field `file` (SUPER_ADMIN, CLASS_REP)
- `GET /api/v1/course-units/{id}/cover-image` — authenticated download (students: active units only)
- `DELETE /api/v1/course-units/{id}/cover-image` — SUPER_ADMIN, CLASS_REP

**Identifiers**
- `code` — optional official academic course code (e.g. `BSIT3104`); visible to all roles that can read the unit.
- `internalCode` — server-generated ClassHub identifier (`CU-000001`); returned only in **SUPER_ADMIN** responses (`SuperAdminCourseUnitResponse`). Never accepted from clients.

**Cover images**
- One optional cover per course unit; JPEG/PNG/WebP only; max size `CLASSHUB_MAX_COURSE_UNIT_COVER_SIZE` (default 5MB); max dimensions 6000×6000.
- Responses expose `hasCoverImage` and `coverImageUrl` (API path, not filesystem path). SVG uploads are rejected.

### Coursework
- CRUD/lifecycle under `/api/v1/coursework` (staff mutations; students read published)
- `PATCH /api/v1/coursework/{id}` — SUPER_ADMIN, CLASS_REP (DRAFT and PUBLISHED)
- Progress: `GET/PUT /api/v1/coursework/{id}/progress` — STUDENT only

**Lifecycle editability**
- **DRAFT** — all coursework fields editable (including course unit reassignment).
- **PUBLISHED** — academic corrections allowed (title, description, instructions, type, issued/due dates, weight, source fields). Course unit is immutable. Status remains PUBLISHED; edits do not republish or emit `COURSEWORK_PUBLISHED` again.
- **CANCELLED** / **ARCHIVED** — not editable via PATCH.

**Published update notifications**
- Deadline change (`dueAt`) — automatic `COURSEWORK_DEADLINE_CHANGED` for students.
- Instructions change — `COURSEWORK_INSTRUCTIONS_UPDATED` only when `notifyStudentsOfUpdate=true` (default false). Whitespace-only instruction changes do not notify.
- Title, description, weight, source, and other field edits — saved silently (no update notification).

Optional request field: `notifyStudentsOfUpdate` (boolean, default false).

### Attachments (upload)
- `POST /api/v1/coursework/{id}/attachments` — multipart field `file` (DRAFT or PUBLISHED; staff)
- `GET /api/v1/coursework/{id}/attachments`
- `GET /api/v1/coursework/{id}/attachments/{attachmentId}` — download
- `DELETE /api/v1/coursework/{id}/attachments/{attachmentId}` — DRAFT or PUBLISHED; staff
- Allowlisted types/extensions; max size from `CLASSHUB_MAX_ATTACHMENT_SIZE`
- Storage is local filesystem MVP (single instance). Future: S3 / R2 / Spaces / MinIO.
- Antivirus / content scanning is future production work.

### Announcements & notifications
- Announcements under `/api/v1/announcements`
- Notifications under `/api/v1/notifications` (recipient-owned only)

### Private lecture notes (`STUDENT`)
- `/api/v1/notes` — owner-only; AI processing uses `AiNoteProcessor` abstraction (local stub today)

### Class membership & semester timeline
- `GET /api/v1/me/class-membership` — authenticated member status
- `POST /api/v1/classes/join` — STUDENT join request (PENDING)
- `GET /api/v1/me/semester` — STUDENT, CLASS_REP (ACTIVE membership); authoritative current-semester timeline and progress
- Class Rep membership management under `/api/v1/class-rep/**`
- `PUT /api/v1/class-rep/class/semester` — CLASS_REP configures current semester dates for own class

Request body example:

```json
{
  "semesterName": "Year 3 Semester 1",
  "startDate": "2026-08-25",
  "endDate": "2026-12-12"
}
```

Response includes calculated `totalDays`, `elapsedDays`, `remainingDays`, `progressPercentage`, and `state` (`NOT_CONFIGURED` | `UPCOMING` | `IN_PROGRESS` | `COMPLETED`). This is the **current** semester timeline only; historical semester archives are future work.

## Configuration (env)

See `.env.example` for database, CORS, cookie secure flag, session timeout, storage path, upload size, bootstrap admin, and AI provider placeholder.

Production profile: `SPRING_PROFILES_ACTIVE=prod` (secure cookies by default, reduced logging, no SQL echo).

## Security headers

API responses include `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, Referrer-Policy, CSP (`default-src 'none'; frame-ancestors 'none'`), and Permissions-Policy restrictions. HSTS is enabled when secure cookies are on (HTTPS deployments).
