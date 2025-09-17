# MJML Canonical Template Strategy

This document describes how MJML is integrated as the canonical source format for email templates.

## 1. Canonical Source Ordering
1. `mjmlSource` (preferred)
2. `easyEmailDesignJson` (structured design JSON from the Easy Email editor)
3. `html` (legacy / fallback)

`EmailTemplateService.save` renders `mjmlSource` (when present) to HTML via `MjmlRenderService` and stores a snapshot in `lastRenderedHtml`. If `html` was blank it is set to the rendered output to guarantee downstream sending always has HTML.

## 2. Persistence & Versioning
- Current version history (`TemplateVersion`) stores only HTML. Future enhancement: extend to also store MJML snapshot for diffing and rollback at the canonical layer.
- When MJML changes, a new rendered HTML will differ and thus create a new version entry.

## 3. Editing Modes
- Visual editing: Easy Email editor works over HTML (and its own design JSON). It does not yet natively edit MJML.
- Raw MJML editing (new modal) allows power users to modify the canonical source directly. After save, MJML is rendered and HTML/design can be refreshed.

## 4. AI Generation Flow
1. Backend (Python service) now returns JSON including `mjml` and optionally `html`.
2. Java backend persists `mjmlSource` and renders HTML server-side if not supplied.
3. Frontend receives both; current UI still uses HTML. A modal exposes raw MJML for inspection/modification.

## 5. Sending Pipeline
- Campaign send uses `html` field (ensured non-empty). If `mjmlSource` exists and is updated, re-render occurs on save so `html` stays in sync.
- Long-running scheduling should not assume HTML is static; always use latest template snapshot at queue time.

## 6. Legacy Templates
- Templates without `mjmlSource` continue to function unchanged.
- Optional backfill (future): Wrap existing HTML in a minimal MJML scaffold to unify editing.

## 7. Variable Detection
- Future improvement: analyze `mjmlSource` first (canonical) then union with HTML. Present logic still scans HTML.

## 8. Failure Handling
- `MjmlRenderService` catches exceptions and embeds a comment header with the error while returning original MJML for later re-render attempts.

## 9. Deletion & Cascades
- No change: removing a template deletes associated assets, versions, variables. MJML fields are on the base template row and are removed automatically.

## 10. Future Enhancements
- Add `/api/templates/{id}/mjml` endpoint (GET/PUT) with validation & dry-run render.
- Store MJML in `TemplateVersion`.
- Background re-render job to normalize legacy HTML to MJML.
- Lint/validate MJML server-side before save (e.g., ensure required placeholders present for plan tier).

## 11. Summary
MJML is now treated as the authoritative design representation. HTML is a derived artifact used for editing fallback (until a native MJML editor is integrated) and for sending. This separation increases portability, consistency, and future-proofs advanced layout features without sacrificing current editing workflows.
