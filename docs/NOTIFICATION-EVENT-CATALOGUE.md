# ClassHub notification event catalogue

ClassHub renders provider-neutral notification content into separate Email, WhatsApp, and in-app forms. URLs are composed from `CLASSHUB_WEB_BASE_URL`; templates do not contain deployment hosts.

## Delivery policy

- Account onboarding, class-membership lifecycle, password reset, and password-changed events are transactional/security-critical. Available globally configured channels bypass academic channel preferences.
- Academic events respect each student's Email and WhatsApp preferences. In-app academic notifications remain enabled.
- Email includes a branded HTML body and plain-text fallback. WhatsApp is concise and does not reuse Email HTML.
- Provider delivery runs from the durable delivery outbox after the originating transaction. Provider failures do not roll back business changes.

## Events

| Event | Recipient and trigger | Channels | Intentional content | Policy |
|---|---|---|---|---|
| `ACCOUNT_SETUP` | New Class Rep; setup issuance | Email, WhatsApp | Secure setup link; no temporary password or persisted raw token | Transactional |
| `ACCOUNT_SETUP_COMPLETED` | Class Rep; successful setup | In-app, Email, WhatsApp | Welcome plus actual programme, study year, semester, and academic year | Transactional |
| `CLASS_JOIN_REQUESTED` | Student; pending join request created | In-app, Email, WhatsApp | Explicitly says pending approval; includes actual class study context | Transactional |
| `CLASS_JOIN_APPROVED` | Student; Class Rep approval | In-app, Email, WhatsApp | Approval/welcome and actual class study context | Transactional |
| `CLASS_JOIN_REJECTED` | Student; Class Rep rejection | In-app, Email, WhatsApp | Not-approved wording and Class Rep help direction | Transactional |
| `CLASS_MEMBER_DEACTIVATED` | Student; membership deactivation | In-app, Email, WhatsApp | Access deactivation and help direction | Transactional |
| `CLASS_MEMBER_REACTIVATED` | Student; membership reactivation | In-app, Email, WhatsApp | Access restored | Transactional |
| `PASSWORD_RESET_OTP` | Account owner; accepted recovery challenge | Email, WhatsApp | Six-digit OTP, 10-minute expiry, do-not-share and ignore-if-unrequested warnings | Security-critical |
| `PASSWORD_CHANGED` | Account owner; successful reset | Email, WhatsApp | Change alert and immediate support direction; never the password | Security-critical |
| `COURSEWORK_PUBLISHED` | Active enrolled students; publication | In-app, Email, WhatsApp | Title, unit, type, deadline, bounded instructions preview, coursework CTA | Preferences respected |
| `COURSEWORK_DEADLINE_REMINDER` | Eligible incomplete student; reminder window | In-app, Email, WhatsApp | Title, unit, due time and remaining/overdue label | Preferences respected |
| `COURSEWORK_DEADLINE_CHANGED` | Active enrolled students; published deadline changes | In-app, Email, WhatsApp | Previous and new deadline plus coursework CTA | Preferences respected |
| `COURSEWORK_CANCELLED` | Active enrolled students; published coursework cancelled | In-app, Email, WhatsApp | Cancellation, unit, original deadline and coursework CTA | Preferences respected |
| `COURSEWORK_INSTRUCTIONS_UPDATED` | Active enrolled students; explicit significant-update notification | In-app, Email, WhatsApp | Bounded updated-instructions preview plus coursework CTA | Preferences respected |
| `ANNOUNCEMENT_PUBLISHED` | Active enrolled students; publication | In-app, Email, WhatsApp | Announcement title, bounded preview and announcement CTA | Preferences respected |

Provider adapters receive only the recipient contact, first name, prepared academic/security content, and internal action path needed for delivery. Passwords, password hashes, setup/reset token hashes, private notes, session data, and unrelated student data are excluded.
