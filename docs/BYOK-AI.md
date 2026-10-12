# Future: Bring-Your-Own-Key (BYOK) AI providers

**Status:** Not implemented in Phase 12. This document records the intended architecture so the current `AiNoteProcessor` abstraction stays compatible.

## Goal

A student will eventually configure their own AI provider for lecture-note processing instead of a shared ClassHub-managed key.

## Candidate providers

- OpenAI
- Google Gemini
- Anthropic
- Ollama / local models

## Design requirements

1. **Student-specific provider selection** — each student chooses provider + model independently.
2. **Encrypted API key storage** — keys at rest must use application-level encryption (or KMS), never plain text columns.
3. **Never returned after storage** — API responses may show `configured: true` / last-four mask at most; full keys are write-only.
4. **Never logged** — keys must not appear in application logs, audit summaries, exceptions, or traces.
5. **Validation / test connection** — optional “test key” action without persisting note content to third parties beyond the probe.
6. **Enabled / disabled state** — students can disable a provider without deleting the encrypted secret.
7. **Provider adapters** — implement `AiNoteProcessor` (or a factory that selects a processor) per provider while keeping `LocalAiNoteProcessor` for demos/tests.

## Compatibility with current code

- Keep `com.classhub.ai.AiNoteProcessor` as the processing boundary.
- Do **not** add API key fields to the `users` table in this phase.
- Prefer a separate encrypted credentials table (future migration) keyed by `student_user_id` + provider.
- ClassHub-operated `CLASSHUB_AI_PROVIDER` remains an ops-level default for non-BYOK environments.

## Out of scope until a dedicated phase

- Key CRUD endpoints
- Encryption key management
- Real remote provider SDKs
- Billing / quota for third-party usage
