# Database Design

Phase 2 implements an initial MySQL 8 schema for the database `campus_complaint_management`. The schema is created through a versioned Flyway migration; Hibernate is configured with `ddl-auto=validate` and does not create, update, or drop tables.

## Implemented Tables

- `departments`: unique department name, description, active flag, creation/update timestamps.
- `users`: unique email, name, nullable BCrypt password hash, role, optional department, active flag, timestamps. The hash was added through the additive V2 migration; older user records with a null hash cannot authenticate until provisioned with a password.
- `complaint_categories`: unique category name, description, active flag, timestamps.
- `campus_locations`: building, optional room, description, timestamps, and a building/room unique constraint.
- `complaints`: unique generated `complaint_code`, reporter, category, location, optional department, title, description, status, priority, optional image storage reference, timestamps.
- `complaint_assignments`: complaint, assignee, optional assigning user, assignment/unassignment timestamps, and note.
- `complaint_status_history`: complaint, optional responsible user, previous/new statuses, note, and change timestamp.
- `complaint_work_notes`: complaint, authenticated author, note content (up to 1000 characters), record type (`WORK_NOTE`, `COMMENT`, or `RESOLUTION_NOTE`), optional comment request UUID, and creation timestamp.
- `complaint_resolution_proofs`: one separate generated image reference per resolved complaint, the Staff uploader, and upload timestamp.
- `notifications`: authenticated recipient, optional complaint, title/message/event type, idempotency key, creation timestamp, and optional read timestamp.
- `audit_trail`: unique event key, actor user ID and safe actor name/role snapshot, action/entity references, optional complaint reference, safe description, optional before/after JSON, and timestamp. The table intentionally retains actor ID/name snapshots without a user foreign key so audit records survive account deletion or future identity archival.

The enums in Java and the database check constraints constrain user roles and complaint statuses. Common foreign-key lookup paths and complaint status/date lookup paths are indexed.

## Relationships and Deletion Rules

- A user may belong to a department; a department may have many users.
- Each complaint has one reporter, category, and campus location. It may refer to a department.
- Each complaint assignment belongs to a complaint and a Staff user, and records the assigning Admin where available. Reassignment closes the prior active row by setting `unassigned_at` and inserts a new row, preserving assignment history.
- Assignment eligibility uses the existing `users.department_id` relationship; a Staff account must be active, have role `STAFF`, and belong to the selected active department.
- Complaint assignment updates `complaints.department_id` but does not change complaint status or status history.
- `complaint_work_notes` references its complaint and author with `ON DELETE RESTRICT`; it preserves each note and author association and has indexes for chronological complaint reads and author lookups. Record types keep legacy internal Staff work notes separate from user-visible comments and resolution notes.
- `complaint_resolution_proofs` has a unique complaint reference and unique generated image filename, with restrictive foreign keys to the complaint and authenticated uploader. The resolution note remains in `complaint_status_history`, not in the original complaint or image data.
- `notifications` belongs to one recipient (`ON DELETE CASCADE`) and may reference a complaint (`ON DELETE SET NULL`), preserving a notification if its complaint is removed. A unique recipient/event key prevents duplicate event notifications; recipient/created and recipient/read indexes support paginated reads and unread counts.
- Foreign keys use `ON DELETE RESTRICT` to prevent deleting records still referenced by complaints, assignments, or history. Optional actor references use `ON DELETE SET NULL`; the records remain, but the optional actor reference is cleared.
- Entities expose no bidirectional collections and are not returned by API endpoints, avoiding circular JSON serialization.

## Initialization and Safety Assumptions

- `backend/src/main/resources/db/migration/V1__create_initial_schema.sql` is the initial, non-destructive Flyway migration. It creates tables; it does not drop or replace existing data or tables.
- V2 adds only the nullable `password_hash` column to `users`; V1 remains unchanged. The column is nullable to preserve compatibility with records that predate authentication.
- V3 additively adds unique complaint codes, a `MEDIUM` default priority, and an optional image reference; existing complaints are backfilled with a code based on their existing numeric ID. V3 also inserts the initial complaint categories without duplicating existing category names.
- V4 additively adds nullable `roll_number`, `class_name`, and `section` columns plus a unique roll-number constraint. Existing users are preserved; roll number is required for new public Student registrations while class and section remain nullable and optional. Roll numbers are stored as strings, preserving leading zeroes. The database unique constraint applies globally to populated roll numbers.
- V5 additively creates `complaint_work_notes`. It does not alter complaints, assignments, users, or prior status history. The table stores Staff work notes separately from status transitions, preserving existing records and preventing note entry from implying a status change.
- V6 additively creates `complaint_resolution_proofs`; existing complaint attachments, statuses, assignments, history, and users are not updated. Proof image bytes remain in the configured upload directory and are never stored in a description or database blob.
- V7 additively creates `notifications`; it does not modify or backfill existing users, complaints, status history, assignment history, or uploaded files.
- V8 additively adds a constrained `record_type` column to `complaint_work_notes`, defaulting existing and legacy Staff work-note records to `WORK_NOTE`. New ordinary comments and resolution notes use the same durable table and retain the authenticated author and creation timestamp; no existing note content or relationship is rewritten.
- V9 additively adds nullable `request_id` and a unique constraint on `(complaint_id, author_id, request_id)` for idempotent comment submissions. Existing work-note rows retain NULL request IDs and remain unchanged.
- V10 additively creates `audit_trail`; it does not backfill or modify existing accounts, complaints, uploaded files, notifications, comments, or history. Only successful operations after deployment produce audit records.
- Admins can create and list departments through `/api/admin/departments`; the existing `departments` table is used. Departments are never seeded with invented campus names. Student registration requires selecting an active existing department.
- Admins can edit or soft-deactivate/reactivate departments and non-Admin accounts using the existing `active` flags and profile columns. These operations preserve complaint and assignment foreign-key relationships; account roles and passwords are not editable through the profile endpoint.
- Optional startup department initialization uses `INITIAL_DEPARTMENT_NAME`; when explicitly configured, it inserts that approved name only if the departments table is empty. It performs no DDL and does not alter or duplicate existing rows. The live database now has the operator-approved `Artificial Intelligence and Machine Learning` department configured.
- Uploaded image bytes are stored outside source and JAR directories in the configurable `UPLOAD_DIR` (default `./uploads` relative to the backend process working directory). MySQL stores only a generated UUID filename reference, never an absolute path or image bytes.
- The MySQL database itself must exist before application startup. The migration does not create/drop databases.
- Flyway records applied migrations in `flyway_schema_history`. Do not edit an applied migration; add a new versioned migration for future schema changes.
- If a table with a migration's name already exists but is not recorded by Flyway, startup can fail. Inspect and back up existing data before resolving that case; do not delete tables to make startup pass.
- MySQL service configuration was inspected during Phase 2: the local `MySQL80` service is running and its configured port is `3306`. Database existence and credentials must be verified by a real connection check.
- The local username defaults to `root`; `DB_USERNAME` may override it. The password is required through `DB_PASSWORD`, and neither its value nor any real credentials belong in source control.
- Tests use an isolated in-memory H2 database and never connect to or modify local MySQL data.

## Complaint submission foundation

- New complaints start in `SUBMITTED`; their first status-history record is written in the same transaction.
- Supported initial priorities are `LOW`, `MEDIUM`, `HIGH`, and `URGENT`.
- MySQL contains category seed rows matching the submission form, while category and location records remain normal relational rows.
- Staff assignment was added in Phase 8 using the existing `complaint_assignments` table; no schema migration was needed. Phases 9 and 10 status transitions reuse `complaint_status_history`; work notes use V5 and resolution proof metadata uses V6.

## Deferred

Feedback and escalation features are not implemented. Notification delivery is persisted and retrieved through ordinary authenticated API requests; real-time push delivery is not implemented. Complaint image data is file-backed; user authentication credentials are password-hashed and never included in API responses.

## Faculty dashboard queries

The Faculty dashboard adds no tables or migration. Its summary, filters, paginated complaint list, analytics, and exports query the existing `complaints`, `users`, `complaint_assignments`, and `complaint_status_history` tables. Faculty list/report queries are read-only and include complaints linked to reporters with the `STUDENT` role, across departments and workflow statuses. Authenticated Faculty may submit through the existing complaint flow; the server sets the reporter to the JWT principal and stores the account's existing department. `GET /api/complaints/my` scopes personal Student/Faculty history to the authenticated reporter ID, keeping Faculty-submitted complaints out of the campus Student list. Complaint details and protected images are available to a Faculty reporter for their own complaints and to Faculty for Student-reported complaints. Faculty cannot assign, change status, or delete complaints.
