# API Contract

All authentication API errors use `{"error":"...","message":"..."}`. Credentials, password hashes, signing secrets, and database credentials are never returned.

## Health APIs

### GET /api/health

Public application health; does not require MySQL.

```json
{"status":"UP","application":"Campus Complaint and Resolution Management System"}
```

### GET /api/health/database

Public live JDBC check. Returns HTTP 200 `{"status":"UP"}` or HTTP 503 `{"status":"UNAVAILABLE"}`.

## Authentication APIs

### POST /api/auth/register

Public Student registration. Any `role` supplied by a caller is ignored; the server always assigns `STUDENT`. An active department must be configured by an Admin before registration is available.

Request:

```json
{
  "name": "Example Student",
  "rollNumber": "000A1234",
  "departmentId": 1,
  "email": "student@example.edu",
  "password": "CampusExample123"
}
```

Response (HTTP 201):

```json
{
  "accessToken": "<signed-access-token>",
  "tokenType": "Bearer",
  "user": {
    "id": 1,
    "name": "Example Student",
    "email": "student@example.edu",
    "role": "STUDENT",
    "departmentId": 1,
    "rollNumber": "000A1234",
    "className": null,
    "section": null,
    "active": true
  }
}
```

Registration requires Full Name (`name`), Roll Number (`rollNumber`), Department (`departmentId`), Email Address (`email`), and Password (`password`). `className` and `section` remain optional for compatibility with existing student records. `rollNumber` is a string and unique across populated values, preserving leading zeroes. The database stores a BCrypt password hash, never the submitted password.

### POST /api/auth/login

Request:

```json
{"email":"student@example.edu","password":"CampusExample123"}
```

Response: same access-token/profile shape as registration (HTTP 200). Invalid credentials return HTTP 401 with a generic message.

### GET /api/auth/me

Requires `Authorization: Bearer <access-token>`. Returns the authenticated profile only; never returns a password hash.

### GET /api/auth/departments

Public list of active department IDs and names for the registration selector, as an array of `{ "id": 1, "name": "Example Department" }` records. An empty list means no active departments exist. This endpoint does not create or fabricate department records.

### GET /api/admin/users

Requires an Admin bearer token. Lists safe user profiles without password data; `active` indicates whether the account can sign in.

### POST /api/admin/users

Requires an Admin bearer token. Provisions only `FACULTY` or `STAFF` accounts. Public registration cannot assign either role or `ADMIN`. Admin accounts are created only through the one-time controlled bootstrap procedure documented in [SECURITY.md](./SECURITY.md).

Request:

```json
{
  "name": "Example Faculty",
  "email": "faculty@example.edu",
  "password": "InitialExample123",
  "role": "FACULTY",
  "departmentId": 1
}
```

Response: HTTP 201 with a safe user profile only (no token and no password hash). The provisioned account should receive its initial password through a secure channel and change it once password-update functionality is available.

### PUT /api/admin/users/{userId}

Requires an Admin bearer token. Updates the selected non-Admin account's name, email, and department. Student accounts require `rollNumber`; `className` and `section` are optional. The role and password cannot be changed through this request.

```json
{
  "name": "Example Student",
  "email": "student@example.edu",
  "departmentId": 1,
  "rollNumber": "00012345",
  "className": "BSc Computing",
  "section": "A"
}
```

Returns the safe profile; duplicate email or roll number returns HTTP 409. Admin profiles cannot be edited through this endpoint.

### PATCH /api/admin/users/{userId}/deactivate

Requires an Admin bearer token. Soft-deactivates a non-Admin account and returns HTTP 204. Deactivation prevents further sign-in but preserves complaint history and foreign-key relationships. Admin accounts and the requesting account cannot be deactivated through this endpoint.

### PATCH /api/admin/users/{userId}/activate

Requires an Admin bearer token. Re-enables a deactivated non-Admin account and returns its safe profile. Admin accounts cannot be activated through this endpoint.

### GET /api/admin/audit

Requires an Admin bearer token. Returns a paginated, newest-first audit trail from MySQL. Optional query parameters are `page` (default 0), `size` (default 20, maximum 100), `search`, `action`, `role`, `actorUserId`, `from` and `to` (ISO dates, inclusive). Each record includes the server-resolved actor ID/name/role, action, entity and complaint references, safe description, timestamp, and before/after state when applicable. Passwords, password hashes, credentials, tokens, and secrets are never recorded.

Successful supported events include complaint submission and image upload, comments and resolution notes (including internal work notes), assignment changes, status transitions and resolution-proof uploads; Admin creation, edits and activation/deactivation of Staff/Faculty/student accounts; and department creation, edits, and activation/deactivation. Repeated activation/deactivation calls that do not change state do not create duplicate entries. Events are written transactionally with their successful operation.

### GET /api/admin/departments

Requires an Admin bearer token. Returns the existing department records and their active state.

### GET /api/admin/assignment-options/staff?departmentId={id}

Requires an Admin bearer token. Returns only active `STAFF` accounts belonging to the selected active department. Each item contains only the staff ID and name. An empty array means no eligible staff are associated with that department. A 401 indicates that the access token is missing, expired, or invalid; the frontend clears it and requires a new sign-in.

### PUT /api/admin/complaints/{complaintId}/assignment

Requires an Admin bearer token. Assigns or reassigns one active Staff account to an existing complaint and updates the complaint's department. The staff account must have the `STAFF` role, be active, and belong to that active department.

```json
{"departmentId":1,"staffId":12}
```

Returns the updated complaint record, including its current assignment. Reassignment timestamps the previous active `complaint_assignments` row and creates a new row recording the Staff member and assigning Admin. Complaint status and status history are not changed. Invalid departments or staff return HTTP 400; a missing complaint returns HTTP 404.

### POST /api/admin/departments

Requires an Admin bearer token. Creates a department record using the existing department table; no department name is automatically invented.

Request:

```json
{"name":"Example Department","description":"Optional description"}
```

Returns HTTP 201; duplicate department names return HTTP 409.

### PUT /api/admin/departments/{departmentId}

Requires an Admin bearer token. Updates a department name and optional description; duplicate names return HTTP 409.

```json
{"name":"Example Department","description":"Updated description"}
```

### PATCH /api/admin/departments/{departmentId}/deactivate

Requires an Admin bearer token. Soft-deactivates a department and returns HTTP 204. Existing users and complaints retain their department relationship, while the department is removed from public registration choices.

### PATCH /api/admin/departments/{departmentId}/activate

Requires an Admin bearer token. Reactivates the existing department record and makes it available for registration again.

## Notification APIs

All notification APIs require a valid bearer token. The recipient is always taken from the authenticated principal; clients cannot specify a user ID.

### GET /api/notifications?page=0&size=10

Returns the current user's notifications newest first. `size` is limited to 1–50. The response contains `content`, `page`, `size`, `totalElements`, and `totalPages`. Each notification contains `id`, `title`, `message`, `type`, nullable `complaintId`/`complaintCode`, `createdAt`, nullable `readAt`, and `read`.

### GET /api/notifications/unread-count

Returns `{"count":0}` with the unread count for the current user.

### PATCH /api/notifications/{notificationId}/read

Marks the specified notification read only if it belongs to the authenticated user. Repeated requests are idempotent. A notification owned by another user is reported as HTTP 404.

### PATCH /api/notifications/read-all

Marks all unread notifications for the authenticated user read and returns the updated count and common `readAt` timestamp. It does not affect another user's notifications.

Complaint-event notifications are persisted in the same transaction as the triggering complaint operation: submission notifies the reporter and active Admins; assignment changes notify the newly assigned/removed Staff and complaint reporter; status changes notify the complaint reporter. Faculty receives notifications for their own submitted complaints, not all Student complaints visible in the read-only campus view. No WebSocket or push channel is used.

## Status Codes

- 400: request validation failure or unsupported provisioning role
- 401: missing, invalid, expired token or invalid credentials
- 403: authenticated user lacks the required role
- 409: duplicate email, roll number, or department name
- 503: live database health check unavailable

## Complaint APIs

Complaint APIs require a Bearer access token. Requester identity is always taken from the token; client-supplied reporter IDs, complaint IDs, and status values are not accepted in submissions.

### GET /api/complaints/categories

Student- or Faculty-authenticated list of active categories:

```json
[{"id":1,"name":"Electricity"},{"id":2,"name":"Wi-Fi and Internet"}]
```

### POST /api/complaints

Student- or Faculty-authenticated multipart/form-data. The server identifies the reporter from the authenticated principal; the request cannot supply a reporter ID. Include:

- `complaint`: JSON part with `title`, `description`, `categoryId`, `location`, `priority`.
- `image`: optional JPG, PNG, or WEBP file, maximum 5 MiB.

Example JSON part:

```json
{
  "title": "Classroom light is not working",
  "description": "The light above the front row does not turn on.",
  "categoryId": 1,
  "location": "Main Building, Room 204",
  "priority": "MEDIUM"
}
```

Returns HTTP 201 with the generated human-readable `complaintId`, initial `SUBMITTED` status, submission timestamp, database ID, and image availability/link metadata. The client cannot assign status or ownership.

### GET /api/complaints/my

List of the authenticated Student's or Faculty member's own complaints, newest first. Each record includes the complaint ID/code, title, category, submission timestamp, priority, actual current status, and department name when stored. The caller's identity is taken from the authenticated principal, not a request parameter; users cannot read another user's private history.

### GET /api/complaints/{id}

Returns complaint details only to its submitting Student/Faculty, its currently assigned Staff member, or an Admin. An unowned or unassigned complaint returns HTTP 403 to another authenticated account. In addition to the complaint fields, the response includes active assigned staff names and chronological status-history entries (previous/new status, timestamp, actor name, and optional note). Admin responses include `currentAssignment` with the current department ID and staff ID/name; the staff fields are null when not assigned. Faculty reporter details include this assignment information and its timestamp, but not internal staff IDs. Admin and Staff detail responses include legacy `workNotes` in chronological order; a Faculty viewer may read those notes within the existing Faculty complaint scope. Student detail responses omit internal work notes. Ordinary comments and separately permissioned resolution notes are retrieved from the endpoints below. If proof has been recorded, authorized details include `resolutionProof` metadata (protected image URL, Staff name, upload time, and resolution note). Missing complaints return HTTP 404.

### GET /api/complaints/{id}/image

Returns the authenticated and authorized complaint image with its detected/validated image content type. The image endpoint uses the complaint ID and applies the same access checks; it is not an unrestricted file endpoint.

### GET /api/complaints/assigned

Staff-only list of complaints with a current assignment to the authenticated Staff user. Identity comes from the validated JWT principal; request parameters cannot change whose queue is returned.

### PATCH /api/complaints/{id}/status

Staff status updates require an active assignment to the authenticated Staff account. The actor is derived from the JWT. Staff can move `SUBMITTED` or `UNDER_REVIEW` to `ASSIGNED`, then `ASSIGNED` to `IN_PROGRESS`; `IN_PROGRESS` to `RESOLVED` must use the resolution endpoint below. Admins may move `RESOLVED` to `CLOSED`. Other transitions, including attempts to reopen terminal statuses, return HTTP 400. Each accepted transition updates the complaint and inserts a row in `complaint_status_history` in one transaction; assignment does not rewrite the complaint's prior status. Optional JSON `note` is limited to 1000 characters and is retained in history.

### POST /api/complaints/{id}/resolution

Assigned Staff only; multipart/form-data with `resolution` as a JSON part `{"note":"Repair completed and tested."}` and `image` as a JPG/JPEG, PNG, or WEBP file up to 5 MiB. Server-side content, filename extension, size, active assignment, and current `IN_PROGRESS` status are validated. Image file bytes are stored with a generated name in `UPLOAD_DIR`; one metadata row is added to `complaint_resolution_proofs`, and the complaint is moved to `RESOLVED` with the authenticated Staff actor and resolution note in `complaint_status_history` in the same database transaction. If persistence fails, the newly written file is removed. Resolving without valid proof is rejected; the existing complaint image is not replaced.

### GET /api/complaints/{id}/resolution-proof

Returns the proof image only to an authorized complaint viewer: its submitting Student/Faculty, currently assigned Staff, or Admin. The response is served from the configured upload directory by an authenticated controller, not a public filesystem route. Missing proof/file returns HTTP 404.

### POST /api/complaints/{id}/work-notes

Staff-only work-note creation for a complaint currently assigned to the authenticated Staff account. Request JSON is `{"note":"Inspected the switch in Block I."}`. Notes must contain non-whitespace text and be at most 1000 characters. Returns HTTP 201 with the updated complaint response, including chronologically ordered work notes. Adding a note does not change complaint status or description. Notes are persisted independently in `complaint_work_notes`.

### GET /api/complaints/{id}/comments

Returns ordinary comments chronologically for a complaint the authenticated caller may view under the existing complaint access rules. Entries include ID, author name, author role, content, type, and creation timestamp. Legacy Staff `WORK_NOTE` records and `RESOLUTION_NOTE` records are not returned as comments.

### POST /api/complaints/{id}/comments

Adds an ordinary comment with request JSON `{"content":"The light is still flickering.","requestId":"8a0fdb2c-a827-4c37-b35c-3395874d86d1"}`. `requestId` is an optional UUID used by the frontend to make a retried submission idempotent. Repeating the same request ID, author, complaint, and normalized content returns the existing comment with HTTP 201; reusing that ID with different content returns HTTP 409. Content must be non-whitespace and at most 1000 characters. Students may comment only on their own complaints; Staff must have an active assignment; Admin may comment on complaints they manage. Faculty remains read-only. The server derives author and role from the authenticated principal. Returns HTTP 201 with the saved entry. Comment creation does not change complaint status.

After comment persistence, the same transaction creates recipient-scoped notifications: Student comments notify active Admins, currently assigned Staff, and active Faculty monitoring a Student complaint; Staff comments notify the complaint reporter, active Admins, and monitoring Faculty for Student complaints; Admin comments notify the reporter, currently assigned Staff, and monitoring Faculty for Student complaints. The author is excluded and the comment body is never included in notification messages. The existing notification bell polls updated notifications and unread counts while authenticated.

### GET /api/complaints/{id}/resolution-notes

Returns resolution notes for an accessible complaint to assigned Staff or Admin only. Each entry includes its authenticated author name/role and creation timestamp. Students and Faculty cannot read this internal note stream.

### POST /api/complaints/{id}/resolution-notes

Adds a separate resolution note using request JSON `{"content":"Repair parts are on order."}`. Content must be non-whitespace and at most 1000 characters. Assigned Staff or Admin may add notes; Students and Faculty are denied. It does not resolve the complaint or alter status/history. The existing multipart `/resolution` workflow remains unchanged and still requires proof before Staff may mark a complaint resolved.

### GET /api/complaints

Admin-only list of all complaints, newest first. The response includes the submitting student's name and roll number, department, current assigned Staff names (an empty array when unassigned), category, priority, submission date, current status, and `currentAssignment` (department ID plus staff ID/name, null for an unassigned staff member). Student details are populated only in this Admin list response; they are not populated in Student lists or non-Admin complaint details.

## Faculty dashboard APIs

All `/api/faculty/**` routes require a Faculty bearer token. Complaint-list, summary, filter, analytics, and export queries include all complaints whose reporter has the `STUDENT` role, regardless of status or department. Faculty-owned complaints appear only in that Faculty member's `GET /api/complaints/my` history and authorized details; they are excluded from the campus-wide Student list and its metrics. Faculty can submit complaints through `POST /api/complaints`, and can read Student complaint details and protected images, but cannot assign, change status, or delete complaints. Student and Staff accounts receive HTTP 403 on Faculty dashboard APIs; missing/invalid tokens receive HTTP 401.

### GET /api/faculty/summary

Returns campus-wide Student complaint totals, pending (`SUBMITTED` and `UNDER_REVIEW`), assigned, in-progress, resolved (`RESOLVED` and `CLOSED`), rejected, and resolution-rate counts.

### GET /api/faculty/complaints

Returns a paginated list of Student-submitted campus complaints, including description, submitter name, department, active assigned Staff, category, location, priority, status, and submission time. Supported optional query parameters: `page` (zero-based), `size` (1-100), `search` (up to 120 characters; complaint ID/title/category/location/department), `status`, `priority`, `categoryId`, `departmentId`, inclusive ISO dates `from`/`to`, and `sort` (`submittedAt`, `updatedAt`, `priority`, or `status`). The response contains `content`, `page`, `size`, `totalElements`, and `totalPages`. Invalid date ranges, sizes, IDs, enum values, and sort fields return HTTP 400.

### GET /api/faculty/filters

Returns category and department ID/name options present in Student-submitted campus complaints.

### GET /api/faculty/analytics

Returns counts by status, priority, category, and department; submission counts for the most recent 12 months; and average hours from submission to the first `RESOLVED` history entry where resolution data exists. Reopened is not a supported status in the current schema and is not counted.

### GET /api/faculty/reports.csv

Downloads Student-submitted campus complaint rows using the same `search`, `status`, `priority`, category/department ID, and date filters. It includes complaint descriptions and submitter names, but not roll numbers. The export is capped at 5,000 matching rows; narrow filters are required above that limit. User-controlled CSV values are quoted and formula-prefixed values are escaped. Credentials, tokens, and internal filesystem references are never exported.

## Complaint error statuses

- 400: invalid fields, category, priority, or image content
- 401: missing/invalid authentication
- 403: role or complaint ownership/assignment not authorized
- 404: complaint or associated image not found
- 413: upload exceeds configured request/image size
- 500: image storage failure (safe message only)

Image storage location is configured with `UPLOAD_DIR`; only a generated relative filename is kept in MySQL. See [SECURITY.md](./SECURITY.md) for persistence guidance.

## Still Planned

Password reset/change and refresh tokens remain unimplemented.
