# Contacts API Reference

This document lists the REST endpoints exposed by the Contacts module, their purpose, and which of them are integrated in the current UI.

Base URL: {API_BASE_URL}
Auth: Bearer JWT required for all endpoints under `/contacts`.

Notes
- CSV export is streamed and requires Authorization header; the UI uses authenticated fetch to download.
- Non-CSV export returns a JSON `{ downloadUrl }`; the UI fetches this URL with Authorization to get the file.
- Filters are passed as JSON (string) in `filters` for GET or as an array in POST bodies.

## Contacts CRUD
- GET `/contacts`
  - Use: List contacts with optional `segment`, `search`, `filters`. May return an array or a page-like object (content/total elements).
- POST `/contacts`
  - Body: `{ email, firstName?, lastName?, segment? }`
  - Use: Create a contact.
- PUT `/contacts/{id}`
  - Body: same as POST (or partial)
  - Use: Update a contact (inline edit supported in UI).
- DELETE `/contacts/{id}?erase=false`
  - Use: Delete a contact. `erase=true` performs irreversible purge.
- POST `/contacts/{id}/request-delete`
  - Use: Log a GDPR delete request for the contact.
- DELETE `/contacts/{id}/purge`
  - Use: Purge a contact immediately (admin/GDPR tool).

## Activity
- GET `/contacts/{id}/activity`
  - Use: Fetch activity timeline for a contact.

## Bulk operations
- DELETE `/contacts/bulk`
  - Body: `{ ids: number[], erase?: boolean }`
  - Use: Bulk delete.
- PUT `/contacts/bulk`
  - Body: `{ ids: number[], updates: object }`
  - Use: Bulk update (e.g., unsubscribe/resubscribe).
- POST `/contacts/bulk/add-to-segment`
  - Body: `{ contactIds: number[], segment: string }`
  - Use: Add many contacts to a segment in one call.

## Lists / Segments
- GET `/contacts/lists`
  - Use: List saved lists/segments (dynamic/static).
- POST `/contacts/lists`
  - Body: `{ name, description?, isDynamic?, dynamicQuery? }`
  - Use: Create a list/segment.
- PUT `/contacts/lists/{listId}`
  - Body: same shape as POST (partial allowed)
  - Use: Update a list/segment.
- DELETE `/contacts/lists/{listId}`
  - Use: Delete a list/segment.
- GET `/contacts/lists/{listId}/members`
  - Use: Get members of a list.
- POST `/contacts/lists/{listId}/members`
  - Body: `{ emails: string[] }`
  - Use: Add members by email.
- DELETE `/contacts/lists/{listId}/members`
  - Body: `{ emails: string[] }`
  - Use: Remove members by email.
- POST `/contacts/lists/{listId}/materialize`
  - Use: Create a static snapshot of a dynamic list; returns `{ inserted }`.

## Segment preview
- POST `/contacts/segments/preview`
  - Body: `{ filters: Array<{ field, operator, value }> }`
  - Use: Returns `{ count, sample? }` for the given filter set.

## Custom fields
- GET `/contacts/custom-fields`
  - Use: List custom field definitions.
- POST `/contacts/custom-fields`
  - Body: `{ fieldKey, label, dataType, schemaJson? }`
  - Use: Create a custom field.
- PUT `/contacts/custom-fields/{id}`
  - Body: `{ label?, dataType?, schemaJson? }`
  - Use: Update a custom field.
- DELETE `/contacts/custom-fields/{id}`
  - Use: Delete a custom field.
- PUT `/contacts/{id}/custom-fields`
  - Body: `{ customJson: string }` (JSON string of per-contact fields)
  - Use: Set contact-level custom fields.

## Suppression
- GET `/contacts/suppression`
  - Use: List suppressed emails with reasons.
- POST `/contacts/suppression`
  - Body: `{ emails: string[], reason?: string }`
  - Use: Suppress emails.
- DELETE `/contacts/suppression?email=...`
  - Use: Unsuppress a single email.

## Import
- POST `/contacts/bulk`
  - Body: Generic metadata/mapping `{ mapping?, dedupeStrategy?, filename? }`
  - Use: Create an import job record.
- POST `/contacts/import` (multipart/form-data)
  - Form fields: `file`, optional `mapping`, `dedupeStrategy`
  - Use: Upload a CSV to start processing.
- GET `/contacts/imports`
  - Use: List recent import jobs.
- GET `/contacts/imports/{jobId}`
  - Use: Get job details.
- GET `/contacts/imports/{jobId}/progress`
  - Use: Poll job progress.
- GET `/contacts/imports/{jobId}/errors`
  - Use: Fetch row-level errors for a job.

## Export
- GET `/contacts/export?format=csv`
  - Query: `fields`, `contactIds` (repeatable), `filters`
  - Produces: `text/csv` (streaming)
  - Use: CSV export (UI fetches with Authorization header and downloads Blob).
- GET `/contacts/export` (non-CSV)
  - Query: `format` (e.g., `xlsx`), `fields`, `contactIds`, `filters`
  - Produces: `application/json` → `{ downloadUrl }`
- POST `/contacts/export` (CSV)
  - Headers: `Accept: text/csv`, `Content-Type: application/json`
  - Body: `{ format: 'csv', fields, contactIds, filters }`
  - Produces: `text/csv` (streaming)
- POST `/contacts/export` (non-CSV)
  - Body: `{ format: 'xlsx'|'...', fields, contactIds, filters }`
  - Produces: `application/json` → `{ downloadUrl }`
- GET `/contacts/export/{exportId}/status`
  - Use: Optional job status lookup (if used by the chosen export flow).

---

# UI Integration (current)

Contacts screen (web frontend):
- List contacts: GET `/contacts`
- Inline edit: PUT `/contacts/{id}`
- Delete single: DELETE `/contacts/{id}`
- Bulk delete: DELETE `/contacts/bulk`
- Bulk update (unsubscribe/resubscribe): PUT `/contacts/bulk`
- Bulk add to segment: POST `/contacts/bulk/add-to-segment`
- Segment preview (Smart Segments): POST `/contacts/segments/preview`
- Advanced filters: encoded in `filters` on GET `/contacts` and export
- Custom fields (for columns/options): GET `/contacts/custom-fields`
- Lists/Segments (counts/selection): GET `/contacts/lists` (+ list member ops on list pages)
- Export (CSV): GET `/contacts/export?format=csv` with `fields`, `contactIds`, `filters` (UI downloads Blob with auth)
- Export (XLSX/others): POST `/contacts/export` → fetch `{ downloadUrl }` and then download with auth
- Contact activity drawer: GET `/contacts/{id}/activity`

Import workflow (import page):
- Create job: POST `/contacts/bulk`
- Upload CSV: POST `/contacts/import`
- Poll job: GET `/contacts/imports/{jobId}` or `/progress`, and `/errors` if needed

Suppression management (suppression page):
- List: GET `/contacts/suppression`
- Suppress: POST `/contacts/suppression`
- Unsuppress: DELETE `/contacts/suppression?email=...`

Custom fields (fields page):
- List/create/update/delete via `/contacts/custom-fields` endpoints

WebSocket (real-time updates):
- Contact create/update/delete notifications are pushed to the UI to refresh lists.

---

# Examples

CSV export (GET):
- `GET /contacts/export?format=csv&fields=email,first_name,last_name&contactIds=1&contactIds=2`
- Headers: `Authorization: Bearer <token>`

Segment preview (POST):
- `POST /contacts/segments/preview`
- Body: `{ "filters": [{ "field": "segment", "operator": "equals", "value": "VIP" }] }`

Bulk update (PUT):
- `PUT /contacts/bulk`
- Body: `{ "ids": [1,2,3], "updates": { "unsubscribed": true } }`

---

If you need additional endpoints documented or response schemas, extend this file with a short example per endpoint.
