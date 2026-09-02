# ClassHub Class Membership

## Model

ClassHub separates **global user roles** from **class membership**:

| Concept | Purpose |
|---------|---------|
| `User` | Authentication identity with global role (`SUPER_ADMIN`, `CLASS_REP`, `STUDENT`) |
| `AcademicClass` | A cohort/class (supports multiple classes later) |
| `ClassMembership` | Links a user to a class with membership role and lifecycle status |

A **Class Representative** is a single `User` with global role `CLASS_REP` **and** an `ACTIVE` `ClassMembership` with `membershipRole=CLASS_REP`. They retain student-side capabilities for their own academic resources (coursework progress, notes, notifications, preferences, dashboard) while keeping Class Rep management permissions.

## Membership lifecycle

| Status | Meaning |
|--------|---------|
| `PENDING` | Join requested; user may authenticate but cannot access class academic resources |
| `ACTIVE` | Approved member with class resource access |
| `REJECTED` | Join request denied |
| `INACTIVE` | Former member; historical row retained |

Typical transitions:

- `PENDING` → `ACTIVE` (Class Rep approval)
- `PENDING` → `REJECTED` (Class Rep rejection)
- `ACTIVE` → `INACTIVE` (Class Rep deactivation of a student)
- `INACTIVE` → `ACTIVE` (Class Rep reactivation of a student; same membership row retained)

Rejected memberships are not reactivated through the reactivation endpoint.

## Join code security

- Join codes are generated server-side (`SecureRandom`, 6 characters, case-insensitive lookup)
- Join codes are an enrollment convenience, **not** authentication credentials
- Public registration and logged-in join both create `PENDING` membership; Class Rep approval is required for `ACTIVE` access
- Invalid join codes return a generic validation error without exposing internal class data

## Registration number

Students and Class Reps participating in a class should have a `registrationNumber` on `User` (trimmed, case-insensitive unique index). Super Admin accounts may omit it.

## Class scoping

Course units and announcements belong to an `AcademicClass`. Notifications, deadline reminders, coursework visibility, and announcement recipients resolve **ACTIVE** class members (`STUDENT` and `CLASS_REP` membership roles)—not global `User.role == STUDENT`.

## Privacy boundaries

- Class Reps manage membership directory data for their class only
- Class Reps cannot access another member's private notes, progress, preferences, or AI outputs
- Pending/rejected/inactive members do not receive class academic notifications

## Current semester timeline (MVP)

Each `AcademicClass` may configure **one current semester timeline** (not a full historical semester model):

| Field | Purpose |
|-------|---------|
| `semesterName` | Optional label (e.g. `Year 3 Semester 1`) |
| `semesterStartDate` | Inclusive start (`DATE`) |
| `semesterEndDate` | Inclusive end (`DATE`) |

- `PUT /api/v1/class-rep/class/semester` — CLASS_REP configures their own class (resolved from membership; no `classId` in body)
- `GET /api/v1/me/semester` — STUDENT and CLASS_REP read authoritative timeline + calculated progress

Timeline states: `NOT_CONFIGURED`, `UPCOMING`, `IN_PROGRESS`, `COMPLETED`.

Date calculations are inclusive on both start and end dates. Progress is clamped between 0 and 100.

Full historical semester archives, coursework-by-semester grouping, and academic-year structures are **future work**.

## Out of scope (intentional)

Academic years, faculties, departments, and related ERP structures beyond the current-semester timeline are intentionally deferred.
