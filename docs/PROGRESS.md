# Progress Log

## Phase 1

- React/Vite/Bootstrap frontend and introductory student, faculty, staff, and admin pages implemented.
- Spring Boot `GET /api/health` endpoint implemented and tested.
- Frontend Vite `/api` proxy and Axios connection indicator implemented.
- Frontend production build and backend package/test passed during Phase 1.

## Phase 2

### Implemented

- Added Spring Data JPA, Hibernate, MySQL Connector/J, Flyway, and test-scoped H2 dependencies.
- Added environment-configured datasource settings and Hibernate schema validation.
- Added the versioned Flyway migration for the seven initial schema tables.
- Added seven JPA entities and their Spring Data repositories.
- Added `GET /api/health/database` with minimal UP/UNAVAILABLE responses.
- Added database-health tests and isolated H2 repository relationship tests.
- Updated README, database setup, design, and API contract documentation.

### Environment checks

- Java 21 is available.
- Maven is available at `C:\Users\gango\Maven\apache-maven-3.10.0\bin\mvn.cmd` (not on the default PATH).
- The Windows `MySQL80` service was running and its configuration sets port 3306.
- `DB_PASSWORD` was present in the current environment; its value was not displayed.
- The pre-existing Phase 1 backend remained active on port 8080 and returned its application health response. Its open JAR prevented Maven from replacing that specific output file, so the Phase 2 package output uses a distinct final name to preserve the running server.
- The requested database did not exist initially. It was created with `CREATE DATABASE IF NOT EXISTS`; no MySQL account or credential changes were made.
- The Phase 2 application ran on port 8081 to avoid interrupting the existing Phase 1 server on port 8080. Flyway applied migration V1 and Hibernate validated the schema.
- `GET /api/health` returned HTTP 200 on both ports 8080 and 8081. `GET /api/health/database` returned HTTP 200 with `{"status":"UP"}` on port 8081.
- MySQL listed all seven application tables plus `flyway_schema_history`.

### Verification record

- `mvn test` passed using isolated H2 tests.
- Maven test report: 4 tests total across 2 test classes, 0 failures, 0 errors, 0 skipped.
- `mvn package` passed and produced `target/campus-complaints-0.0.1-SNAPSHOT.jar`.
- The initial default package artifact was locked by the active Phase 1 process. A distinct final artifact name preserves that running server while allowing the Phase 2 package to complete.
- Automated tests use in-memory H2 and do not modify local MySQL data.

## Not implemented

Complaint operations, dashboards, and other later-phase features remain deferred. MySQL tables exist only when Flyway successfully runs against the configured MySQL database.

## Phase 3

### Implemented

- Added additive Flyway V2 migration for `users.password_hash`; V1 was left unchanged.
- Added BCrypt password storage, student-only public registration, login, signed/expiring JWT access tokens, bearer authentication, profile endpoint, and database-backed active-role verification.
- Added role restrictions for student/faculty/staff/admin API path spaces and admin-only user provisioning/listing endpoints.
- Added controlled one-time environment-based Admin bootstrap. The bootstrap refuses to proceed if an Admin already exists.
- Added exact-origin configurable CORS for the current local Vite ports.
- Added React registration/login, session-based auth context, bearer Axios interceptor, logout, guarded portal routes, and unauthorized state.
- Added authentication/security docs and API request/response examples.

### Verification

- `mvn test` passed: 10 tests across authentication, repository, and health test classes; 0 failures, 0 errors, 0 skipped. Authentication tests ran with isolated H2.
- `mvn package` passed and produced `backend/target/campus-complaints-phase3-0.0.1-SNAPSHOT.jar`.
- `npm run build` passed: Vite production build completed.
- The Phase 3 application started on port 8080 using the configured MySQL database; Flyway validated versions 1 and 2 and reported the schema up to date. Hibernate validated the mapped entities.
- `GET /api/health` and `GET /api/health/database` returned HTTP 200; the database response was `{"status":"UP"}`.
- Live MySQL smoke checks passed for student-only registration (even when the request supplied a different role), password login, authenticated profile retrieval, missing-token rejection (401), and Student access denial to Admin resources (403). The generated temporary test account was removed by its exact generated ID and email afterward.
- CORS preflights allowed `http://localhost:5173` and `http://localhost:5174`; a non-configured origin received HTTP 403.
- Browser checked the preserved homepage, registration form and unauthenticated `/student` redirect to login. Browser console showed React Router v6 future-flag notices.
- Existing Phase 1 Vite port configuration was not changed. The configured port is 5173; CORS also permits 5174 as requested for local use.

### Limitations

- Password reset/change, token revocation/refresh, and department management are not in scope.
- `departmentId` is optional until departments can be managed.
- The JWT is held in `sessionStorage`, with the XSS limitation documented in `docs/SECURITY.md`.
- The Vite development server started for browser verification remains available on port 5173; the Phase 3 backend remains available on port 8080.

## Complaint submission foundation (Phase 5 request)

### Implemented

- Added additive Flyway V3 for unique human-readable complaint IDs, constrained priority values, optional image references, and idempotent category seed rows. V1 and V2 were not edited.
- Extended the existing complaint entity/repository and added validated multipart submission, student history, authorized complaint details/image retrieval, Staff assigned-list and Admin all-list endpoints.
- Submission derives reporter and department from the authenticated account, starts at `SUBMITTED`, and writes its initial status-history row in the same database transaction.
- Added generated UUID file storage outside the application source/JAR. JPG, PNG, and WEBP content is signature-checked and decoded, limited to 5 MiB and 25 megapixels, with content type derived from validated bytes.
- Added Student complaint form, history and details pages with optional image preview, replacement/removal, client-side checks, request states, and existing bearer-token integration.
- Added isolated H2 multipart, persistence, status-history, ownership, image access, missing-auth, invalid-image tests.
- Updated README, API contract, database design and security/progress documentation.

### Automated verification

- `mvn test` passed: 15 tests, 0 failures, 0 errors, 0 skipped. Complaint database tests use isolated H2 and do not modify MySQL; the suite includes a WebP encode/store/read round trip.
- `mvn package` passed with the Phase 5 artifact name after the final service/test changes.
- `npm run build` passed using Vite after the frontend changes; no frontend port configuration was changed.

### Database and live integration verification

- The Phase 5 backend started on port 8080 and Flyway successfully applied V3 to the configured `campus_complaint_management` schema on MySQL 8.0.46; schema history contains V1, V2 and V3.
- `GET /api/health` returned UP and `GET /api/health/database` returned HTTP 200 with `{"status":"UP"}`.
- A temporary Student registration/login, profile lookup, category listing (11 seeded categories), multipart complaint submission with a real PNG, owner history/detail/image retrieval were exercised against live MySQL. History and detail returned successfully after adding read-only service transactions for lazy entity mapping.
- A different Student received HTTP 403 for the complaint detail and image; an unauthenticated detail request received HTTP 401. The authorized owner received the PNG with `image/png`.
- The exact smoke-test complaint, history row, generated location, two generated Student accounts, and stored PNG were removed after verification. No uploaded files remain in the configured upload directory.
- Browser verification confirmed the existing homepage, login form and Student registration form still render, and an unauthenticated visit to the new Student complaint submission route redirects to login.
- MySQL emitted a non-blocking deprecation warning for `VALUES()` in the V3 category upsert. V3 applied successfully and was left unchanged after application; future upserts should use MySQL row-alias syntax.

## Portal redesign and student registration expansion

### Implemented

- Reworked the shared header/footer, responsive homepage, role portal cards, login and registration layouts, status/data tables, dashboard navigation, cards, badges, and chart styling without adding a frontend dependency.
- Added dedicated role login and dashboard URLs for Student, Faculty, Staff, and Admin, plus role-specific protected routing while retaining existing URLs.
- Student and Staff dashboards use their own complaint APIs; the Admin overview derives metrics and charts from existing Admin complaint/user APIs. Faculty page explicitly explains that no Faculty complaint-list API exists rather than showing invented data.
- Admin has complaint/user reads, authorized Staff/Faculty provisioning, and Admin-only department setup. Admins can edit non-Admin account details, activate/deactivate non-Admin accounts, and edit/activate/deactivate departments while preserving history; account roles are immutable and Admin accounts cannot be modified through this interface.
- Added a separate Student registration route with Full Name, Roll Number, Department, Email Address, and Password; it loads departments from the existing API, validates inputs, and disables submission while pending.
- Added additive Flyway V4 for student roll number, class, and section, with a unique roll-number constraint. New Student registration requires a department and roll number; class and section are optional. Existing records are not deleted or rewritten.
- Set Vite's strict frontend port to 5174; backend remains 8080 and supports CORS from both configured development ports.

### Verification

- Spring Boot integration tests use isolated H2 and cover authentication, role restrictions, Admin department management, and Admin-only account editing/deactivation. They do not modify local MySQL data.
- Flyway V4 was verified against local MySQL 8.0.46; it adds the student registration fields without rewriting existing users. At initial inspection there were no active departments; the registration fix later initialized the approved department only after confirming the table was empty.
- `/api/health` and `/api/health/database` returned healthy responses against the running MySQL-backed application.
- Vite serves the redesigned frontend at port 5174 and proxies `/api` to port 8080. At initial inspection, the registration page correctly reported the empty department state; after guarded initialization it loaded the real department from MySQL.
- Final test/package/build commands and results are recorded at task completion below.

### Not implemented

- Complaint assignments, assignment creation, status transitions/resolution, notifications and analytics remain outside this phase.

### Final verification results

- Earlier `mvn package` completed successfully: 19 tests, 0 failures, 0 errors, 0 skipped; the later registration/sign-in fix was validated separately below.
- `npm run build` completed successfully with Vite.
- Before the final rebuild, the previous backend returned `UP` from `/api/health` and `/api/health/database`. The frontend registration route returned HTTP 200 on port 5174, and the Vite `/api/auth/departments` proxy returned an empty list at that time.
- Package output is `backend/target/campus-complaints-redesign-0.0.1-SNAPSHOT.jar`. The Maven artifact name was changed so packaging does not attempt to replace the artifact associated with the already-running backend.
- Earlier account and department management API verification used isolated H2, not live MySQL.

### Registration and sign-in bug fix

- The registration UI now contains exactly five inputs in the requested order. It submits the chosen department ID and does not require or submit class/section. After a confirmed registration response, it offers the Student Login link instead of treating registration as an automatic sign-in.
- The public department endpoint initially returned an empty array. With the operator-approved name configured for the single rebuilt backend startup, the empty-table-guarded initializer inserted the first department; the live endpoint now returns it as `{ "id": 1, "name": "Artificial Intelligence and Machine Learning" }`. Existing rows would have been left untouched.
- Backend registration tests use isolated H2. The latest `mvn test` run passed 23 tests (0 failures, 0 errors, 0 skipped), including tests for optional class/section, selected department association, BCrypt storage, subsequent student authentication, department initialization, and existing role/health/complaint behavior.
- The final `mvn package` also succeeded with 23 tests, 0 failures, 0 errors, and 0 skipped.
- The latest `npm run build` passed. The frontend showed exactly five registration fields and loaded the real department from MySQL.
- Browser end-to-end Student registration, Student login, and profile retrieval succeeded using one unique synthetic test account; the account remains in MySQL as requested for live verification. Its department ID is `1`, `/api/auth/me` returned `STUDENT` without a password hash, and login reached `/student/dashboard`.
- Against the live backend, a Student request to the Admin users API returned HTTP 403, an unauthenticated request returned HTTP 401, and the Student browser session saw the unauthorized portal page. H2 integration tests separately verified Admin/Staff/Faculty login, invalid credentials/tokens, role restrictions, and student-only public registration. Real Admin/Staff/Faculty passwords were not supplied, so their account sign-ins were not tested against live MySQL.

### Resolution upload runtime deployment repair

- Root cause verified: the backend serving port 8080 was running `backend/target/campus-complaints-redesign-0.0.1-SNAPSHOT.jar`, which predated the resolution-proof workflow and did not contain the V6 migration. The source frontend submitted to `/api/complaints/{id}/resolution`, but the deployed backend artifact did not include the proof table migration or current resolution-proof implementation.
- Kept the old backend running while building; Spring Boot's repackage goal attempted to rename its locked artifact and failed after the tests passed. Changed Maven's final artifact name to `campus-complaints-resolution-${project.version}` and successfully packaged the complete executable JAR separately without overwriting the old artifact path.
- After approval, stopped only the verified old backend process and started the new artifact as the sole listener on port 8080, preserving the configured MySQL/JWT environment and using the existing `backend/uploads` directory.
- Flyway successfully applied additive V6, moving the live schema from version 5 to version 6. Hibernate schema validation/startup and both health endpoints succeeded. V6 creates proof metadata storage; no complaint row or original attachment was modified.
- Full Maven package passed 31 tests (0 failures/errors); frontend tests passed 11 tests and the production build passed. H2 integration tests use generated fixtures and cover resolution upload, proof retrieval, owner/role authorization, and persistence across a new login.
- The shared Staff browser remained signed out, so an authenticated live upload and its original failure response could not be captured. The exact pre-fix HTTP response for the user's click is therefore unverified; the verified deployment mismatch explains that the live service did not contain the endpoint/schema the UI expected. Live write verification must use a new isolated test complaint, not a pre-existing complaint.
- Health and MySQL connectivity returned HTTP 200/`UP` after the rebuilt backend restarted on port 8080. No existing user, complaint, or department rows were modified; one department and one clearly synthetic test Student account were added.

## Phase 6 — My Complaints and Tracking

- Reused the existing Student-protected `GET /api/complaints/my` and owner-authorized `GET /api/complaints/{id}` endpoints; no complaint tables or endpoints were duplicated.
- Complaint details now include the complaint's stored department, currently active assigned staff names (when any), and chronological status changes read from `complaint_status_history`. The My Complaints list exposes department alongside existing ID, title, category, date, priority, and current status.
- My Complaints has loading/error/empty states, an empty-state Submit Complaint action, ID/title search, actual backend status filters, newest-first date sorting, and client-side pagination in pages of 10. The dashboard uses the same search/filter/sort behavior.
- Student dashboard summary cards are derived from that student's actual API list and the existing statuses: pending (`SUBMITTED`, `UNDER_REVIEW`), in progress (`ASSIGNED`, `IN_PROGRESS`), resolved (`RESOLVED`, `CLOSED`); `REJECTED` is counted only in total.
- Added isolated H2 tests for detail ownership/stored status history and empty-list/auth behavior. The existing suite also covers a second Student being denied access to another Student's complaint and missing-token rejection. Frontend Node tests cover status filtering, ID/title search, date sorting, empty results, and exclusive summary counts.
- Phase 6 verification: `mvn test` passed 25 tests with 0 failures/errors/skips; `npm test` passed 2 tests; `npm run build` passed. Live MySQL records were not changed for Phase 6 checks. The browser confirmed that unauthenticated access redirects to Student login; authenticated Phase 6 page checks remain pending because the existing backend JAR process was kept running and was not replaced.

## Phase 7 — Admin Complaint Management

- Reused Admin-only `GET /api/complaints` and role/ownership-protected `GET /api/complaints/{id}` plus the protected complaint image route; no competing assignment or status endpoints were added.
- The Admin list response now includes the reporter name and roll number only for Admin callers, existing complaint department, and current assigned Staff names. Admin details include those same reporter fields with stored assignment and status history. Non-Admin responses do not populate reporter fields.
- Added an **All complaints** Admin route and navigation entry with real API data, loading/error/empty/retry states, ID/title search, status/category/priority/department filters, reset, date sorting, and client-side pagination.
- Admin overview cards now show Total complaints, Submitted (`SUBMITTED` only), In progress (`ASSIGNED` and `IN_PROGRESS`), and Resolved (`RESOLVED` and `CLOSED`) using actual records. The management UI explicitly notes that assignment and status changes are unavailable.
- H2 integration coverage creates complaints for separate students, confirms Admin list/detail access and reporter fields, and verifies unauthenticated, Student, Staff, and Faculty list access is rejected. Existing tests continue to cover Student owner checks and details.
- Phase 7 verification: full `mvn test` passed 26 tests with 0 failures/errors/skips; `npm test` passed 4 tests; `npm run build` passed. The live `/api/health` endpoint returned `UP`. Tests use isolated H2 data; no live MySQL complaint records were changed.
- Browser verification was limited to unauthenticated navigation, which correctly redirected to `/admin/login`. An authenticated Admin browser/API check remains manual because no Admin session was available. The backend process on port 8080 (PID 36476) was left untouched; it is the previously packaged JAR and does not include the current Phase 7 source changes.

## Phase 8 — Department and Staff Assignment

- Reused the existing `complaints.department_id`, `users.department_id`, and `complaint_assignments` table. No Flyway migration or database reset was needed; existing assignments remain in history.
- Added Admin-only `GET /api/admin/assignment-options/staff?departmentId={id}` and `PUT /api/admin/complaints/{complaintId}/assignment`. The service validates active department, active STAFF role, and department membership; safe staff options contain only ID and name.
- Assignment changes close prior active assignment rows and insert a new row attributed to the authenticated Admin. They update the complaint department but do not change complaint status or status history.
- Reused `GET /api/admin/departments`, Admin complaint list/detail, and Staff-only `GET /api/complaints/assigned`. The Staff queue remains scoped to the authenticated JWT principal, not a client-supplied ID.
- Added assignment controls to the Admin complaint table and detail page, plus assignment details in the Staff list. The Admin page uses live department and eligible-staff API data, with loading/error/empty states.
- Full backend `mvn test` passed 29 tests (0 failures/errors/skips); focused assignment integration tests passed 12 tests; frontend `npm test` passed 4 tests; `npm run build` passed.
- Tests use isolated H2; no complaint/user records were changed. The stale backend was stopped after approval, rebuilt with `mvn package` (29 tests passed), and restarted once as PID 42736. `/api/health` and `/api/health/database` both return UP; the executable JAR now contains `AdminAssignmentController`. Unauthenticated assignment API calls return 401.
- Assignment authentication follow-up: the frontend reads `campusAccessToken` from `sessionStorage` and sends it as a Bearer token. The shared browser tab had no token, consistent with the protected endpoint's 401; JWT access tokens expire after 30 minutes. A response interceptor now clears an expired/invalid token, redirects to role-specific sign-in, and displays a reauthentication message. Frontend tests verify the header, expired-session path, and that failed login does not trigger global logout.
- Read-only MySQL inspection found one active STAFF account, zero inactive STAFF accounts, and no department association for that account. The eligible-staff API therefore correctly returns no options for the selected departments; it now explicitly reports that no active Staff account is associated. No account was changed. Authenticated Admin assignment could not be browser-verified because the shared browser currently has no Admin token.
- Follow-up verification: `mvn package` passed 29 tests, focused `ComplaintFlowIntegrationTest` passed 12 tests, `npm test` passed 7 tests, and `npm run build` passed.

## Phase 9 — Staff Dashboard and Complaint Handling

- Enhanced the existing Staff workspace with unique total-assignment, awaiting-work, in-progress, and resolved counts and a live assigned-complaint table including location, department, priority, submission date, status, and details link.
- Added Staff-only `PATCH /api/complaints/{id}/status`; the authenticated Staff member must have the active complaint assignment. Allowed transitions are `SUBMITTED`/`UNDER_REVIEW` → `ASSIGNED` → `IN_PROGRESS` → `RESOLVED`; each transition is stored in `complaint_status_history`. Prior `SUBMITTED` states from Phase 8 assignment are not rewritten.
- Added Staff-only `POST /api/complaints/{id}/work-notes`; work notes retain the complaint, authenticated Staff author, and timestamp. Notes are separate from status history and do not change status or complaint content.
- Added additive Flyway V5 `complaint_work_notes` with complaint/author foreign keys and lookup indexes. No existing complaint, assignment, user, or history rows are deleted or modified. Hibernate continues to validate the schema.
- Complaint details retain existing descriptions, attachment access, status history, and assignment display; Staff can perform the next valid transition and add notes. Internal work notes are included only for Admin and Staff detail callers, not Student/Faculty responses.
- Backend integration tests cover valid and invalid transitions, status persistence/history, note validation and persistence, assignment ownership, JWT role restrictions, and assigned queue status. Frontend dashboard counts derive only from the authenticated user's live assigned-list response and refresh when the workspace regains focus.
- Verification: `mvn package` passed the full suite (30 tests, 0 failures/errors/skips) and produced the executable JAR. `ComplaintFlowIntegrationTest` passed all 13 tests, including the Phase 9 transition/note authorization and persistence cases. `npm test` passed 8 tests; `npm run build` passed.
- The old PID 42736 was stopped only after confirming it was the process locking the existing application JAR. The newly packaged backend is the only process on port 8080 (PID 22992); startup connected to MySQL, Flyway applied V5 successfully, and Hibernate validated the mapped schema.
- Live checks returned `UP` from `/api/health` and `/api/health/database`; unauthenticated PATCH status and POST work-note requests returned HTTP 401. Read-only MySQL verification found schema version 5 and confirmed the supplied complaint remains `SUBMITTED` with its active matching-department assignment. `complaint_work_notes` has zero live rows; no complaint/status/assignment/note data was modified by these checks.
- Automated integration tests use isolated H2. No authenticated live Staff/Admin workflow was performed; those secured transitions, role denials, and note persistence were verified by H2 integration tests instead.

## Phase 9.5 and Phase 10 — Resolution Proof and Status Workflow

- Reused the existing status enum and history table: Staff can move `SUBMITTED`/`UNDER_REVIEW` → `ASSIGNED` → `IN_PROGRESS`; moving to `RESOLVED` now requires the authenticated Staff resolution endpoint with image proof and a resolution note. Admin may close `RESOLVED` complaints. History responses include actor and note.
- Added a protected resolution-proof retrieval endpoint. Authorized Student/Faculty owners, currently assigned Staff, and Admin can view proof metadata and image; the image bytes remain in the configured upload directory.
- Added V6 `complaint_resolution_proofs` with a unique complaint association, unique generated file reference, uploader FK, and upload timestamp. V6 is additive and does not alter existing complaints, complaint images, users, assignments, or status history.
- Frontend resolution form previews/removes/replaces valid JPG/JPEG/PNG/WEBP files, enforces a 5 MiB limit, requires a resolution note, prevents duplicate submission, and displays proof and resolution history on authorized details pages. Existing complaint images remain separate.
- Verification: full Maven test suite passed (30 tests) against the isolated H2 test profile; frontend tests passed (8 tests) and the Vite production build passed. The live MySQL database has not been migrated to V6 or used for authenticated complaint actions in this work; the running backend remains the existing pre-V6 JAR, so the new endpoints are not live until a controlled deployment/restart applies the additive migration.

## Phase 11 — Faculty Dashboard

- Added authenticated Faculty summary, paginated complaint-list, filter-options, analytics, and CSV report APIs. Search/filter/sort and all metric/grouping queries execute in the database; pagination is capped at 100 rows and exports at 5,000.
- Faculty dashboard reads were expanded to campus-wide Student-submitted complaints after explicit scope approval. Lists, details, protected images, metrics, filters, analytics, and CSV exports use the Student reporter-role scope; Faculty-owned detail access remains available. Student/Staff roles are denied Faculty APIs, and Faculty remain unable to assign or change complaint status. No schema migration or database reset was required.
- Verification after this change: `mvn package` passed all 33 backend tests and `npm run build` passed. A read-only MySQL inspection confirmed the four reported records have Student reporters. The running frontend is currently at Faculty login without an authenticated session, so retrieving those production MySQL records through the live Faculty API has not been verified; no database records were changed.
- Faculty complaint submissions are now enabled through the existing authenticated multipart endpoint and shared form. Server-side reporter identity is read from the JWT user; Student submissions remain Student-reported. `GET /api/complaints/my` is available to both roles and remains scoped to the caller. Faculty has separate My complaints and Submit complaint navigation/routes; the campus-wide dashboard continues to include only Student submissions. Automated integration tests cover Faculty submission persistence/identity, private-history separation, Admin visibility, new Student complaint listing across a department boundary and status progression, and denied Faculty mutations.
- Live verification: the previous backend process (PID 3048) had started before the packaged authorization changes and was restarted in place as PID 37024 using the built JAR; no second listener was started. Startup connected to MySQL 8.0, validated all six migrations, and found the schema current. Health and database-health return HTTP 200; protected Faculty endpoints without credentials return HTTP 401. A read-only MySQL query confirmed current Student complaint reporter IDs/roles. The shared browser remains unauthenticated, so successful requests from a live Faculty session are still unverified; no MySQL rows were changed.
- Added Faculty Overview, All Complaints, Analytics, and Reports pages with live metrics, filters, date range, sorting, pagination, status/category/priority/department charts, recent complaints, and filtered CSV download. CSV text is quoted and formula-prefixed content is escaped.
- Reused authorized complaint detail and image APIs for status history, original attachments, resolution proof, resolution note, assignment details/timestamps, and authorized Staff work notes. Authorized Faculty owners can view these only on their own reported complaints; Student detail responses still omit internal Staff work notes.
- Added isolated H2 integration coverage for summary counts, pagination, search and filters, invalid sort/size/date handling, analytics and average resolution query, scoped CSV and formula-injection escaping, missing/wrong-role requests, Faculty mutation denials, and Faculty-owner detail/proof/note visibility. Test fixtures roll back and do not modify MySQL records.
- Verification: `mvn package` passed with 32 tests (0 failures/errors/skips); `npm test` passed with 11 tests; `npm run build` passed. No live MySQL data was changed and no live Faculty account/API workflow was run; the build and integration tests confirm source behavior in isolated H2 only.

## Phase 12 — Persistent In-App Notifications

- Added additive Flyway V7 notification storage with recipient/event uniqueness, recipient and complaint foreign keys, read timestamps, and indexes for recipient-scoped pagination and unread counts. Existing rows and uploaded files are not modified.
- Added authenticated, recipient-scoped APIs for paginated notification listing, unread counts, marking one notification read, and marking all read. Ownership is always derived from the JWT; attempts to mark another user's notification return 404.
- Integrated notifications transactionally with successful complaint submission, assignment/reassignment, and status/resolution changes. Current recipient behavior is documented in the Notification Role Fan-out Follow-up section below.
- Added a shared notification bell to all four role dashboards, with unread count, paginated loading, read actions, errors, and navigation to the existing role-specific complaint list. Notifications load from the authenticated Axios client.
- Updated README, API contract, database design, security, and progress documentation. No schema migration has been applied to live MySQL as part of this implementation.
- Verification: backend `mvn test` passed 37 tests (0 failures/errors/skips), including two new isolated-H2 notification integration tests for submission recipients/idempotency, owner isolation, unread/read-all behavior, and Faculty reporter assignment/status notifications. Frontend `npm test` passed 19 tests (0 failures); `npm run build` passed. `mvn package` ran all tests successfully but its Spring Boot repackage step could not rename the configured output JAR because it is locked by the running backend; the live process was not stopped or overwritten.
- Non-invasive service checks returned HTTP 200 from the existing frontend (`:5174`), backend health (`:8080/api/health`), and database health (`:8080/api/health/database`). These checks do not establish that the running older backend contains the new notification API or that V7 has been applied.

## Phase 13 — Comments and Resolution Notes

- Extended the existing `complaint_work_notes` table rather than creating a duplicate note store. Additive Flyway V8 adds constrained `record_type` values; existing records default to `WORK_NOTE` and retain their complaint/author/content/timestamp associations.
- Added authenticated comment list/create and resolution-note list/create APIs. Authors and roles are taken from the active authenticated account. Student comment writes are owner-only; Staff writes require an active assignment; Admin writes follow existing complaint management scope; Faculty remains read-only. Resolution notes are readable/writable only by assigned Staff and Admin. Empty and over-1000-character content is rejected.
- Added Comments and Resolution Notes sections to complaint details, including author role, content, timestamps, loading/empty/error/success states, and forms only for permitted roles. Legacy Staff work notes remain separately typed and continue through the existing work-note endpoint. Neither new note type changes status or status history.
- Verification: `mvn test` passed all 38 backend tests with isolated H2 (0 failures/errors/skips), including new ownership/assignment/role checks, author derivation, validation, separation from legacy work notes, and unchanged complaint status. `npm test` passed all 20 tests; `npm run build` passed.
- Existing frontend, backend health, and database-health endpoints returned HTTP 200. The shared browser tabs are on Student and Faculty login pages with no authenticated session, so authenticated persistence-after-restart and role-specific visual workflows were not browser-tested. V8 has not been applied to live MySQL and the existing backend process was not restarted; the running server is not verified to include the Phase 13 API. H2 integration tests validate source behavior without touching MySQL.

## Notifications 401 Follow-up

- Root cause in the UI: `NotificationBell` requested `/notifications/unread-count` unconditionally on mount, without checking AuthContext initialization, role identity, or token presence. React StrictMode also replays mount effects in development, accounting for duplicate requests in that case. The backend correctly protects notification routes; anonymous and invalid bearer requests return HTTP 401 with `{"error":"unauthorized","message":"Authentication is required."}`.
- The bell now waits for completed authentication, matching role and access token, suppresses requests when signed out/token changed, and shares concurrent unread-count requests to avoid StrictMode duplicates. Authenticated requests still use the existing Axios bearer interceptor.
- Verification: backend suite passed 39 isolated-H2 tests (0 failures/errors/skips); authenticated unread-count returned 200 for all four roles with recipient-specific counts, while missing/invalid tokens returned 401. Frontend suite passed 20 tests and production build passed. The signed-out browser remains on the Student login page and its resource list contains no unread-count request. After restarting the confirmed project backend with the rebuilt executable JAR, `/api/health` and `/api/health/database` returned HTTP 200; an anonymous unread-count request returned HTTP 401 as intended.
- The rebuilt backend started successfully against the configured MySQL database and Flyway validated and applied additive migrations V7 and V8, advancing the schema from v6 to v8 without dropping or resetting data. A live authenticated unread-count request returning HTTP 200 could not be tested because no authorized account credentials or existing browser session were available. Automated tests used isolated H2 and did not modify live MySQL records.

## Notification Role Fan-out Follow-up

- Root cause: the backend event hook notified only the complaint submitter and Admins on submission, only the assignee and reporter on assignment, and only the reporter on status/resolution changes. Thus Admins and Staff missed later events, and Faculty monitoring of student complaints had no notifications. The bell and authenticated list/count/read endpoints were already mounted for all four roles without role-specific filtering.
- Submission now notifies the submitter, active Admins, and active Faculty only when the reporter is a Student. Assignment changes notify the assigned and formerly assigned Staff, reporter, active Admins, and active Faculty monitoring a Student complaint. Status and resolution events notify the reporter, active Admins, currently assigned Staff, and active Faculty monitoring a Student complaint.
- Faculty scope follows the existing monitoring policy: Faculty dashboards can monitor campus-wide Student-reported complaints. Faculty reporters still receive their own complaint events, but Faculty are not broadcast notifications for complaints submitted by other Faculty.
- Recipient IDs are taken from the saved complaint, active user/assignment records, and authenticated account; existing recipient-scoped APIs and JWT protection are unchanged. Existing per-recipient event keys and database uniqueness prevent repeated handling of the same event from creating duplicates. No new migration or table was needed.
- Verification: backend `mvn package` passed all 40 isolated-H2 tests (0 failures/errors/skips); the updated notification integration tests assert authenticated HTTP 200 and recipient-specific unread counts for Student, Admin, assigned/former Staff, and Faculty, verify resolution fan-out, repeated-event deduplication, and the Faculty monitoring boundary. Existing tests also cover anonymous/invalid-token HTTP 401 and private mark-as-read HTTP 404. Frontend `npm test` passed all 20 tests and `npm run build` passed; no frontend code change was necessary.
- Live tests with existing authenticated accounts for all four roles could not be performed because no authorized sessions/accounts were available in the browser. Automated role tests use generated test users and JWTs in isolated H2 and do not change live MySQL data. No real-account event workflow or persistence-after-restart test is claimed here.
- The confirmed backend process was restarted with the updated packaged JAR. The MySQL connection and schema v8 were validated at startup with no new migration required; `/api/health` and `/api/health/database` returned HTTP 200, while an anonymous unread-count request remained HTTP 401. No live complaint or notification records were created or changed during verification.

## Automatic Notification Updates

- The authenticated notification bell now loads the latest notification page and unread count immediately, then polls the existing recipient-scoped endpoints every five seconds for all four dashboards. The notification list refreshes even while the dropdown is closed; opening it and pagination still use the existing UI.
- Polling starts only after AuthContext initialization confirms the matching role and a stored access token. The interval is cleared on logout, token/role changes, or unmount. A polling lock prevents overlapping poll cycles; in-flight list requests for the same page are shared, and refreshed records are merged by notification ID to avoid duplicates.
- Session-expired 401 behavior remains handled by the shared API client; its existing AuthContext event removes the session and stops polling. Endpoints remain JWT-protected and return persisted MySQL-backed notification state; no backend schema or logic changes were needed for polling.
- Verification: frontend `npm test` passed all 20 tests and `npm run build` passed. Backend `mvn test` passed all 40 tests (0 failures/errors/skips), including authenticated role-scoped notification APIs and database-backed list/count behavior. The shared browser is signed out, so no authorized cross-browser event-and-poll workflow could be performed; no claim is made that real notifications were observed arriving within 5–10 seconds.

## Comment Notifications

- Root cause: the authenticated comment endpoint persisted comments in the existing `complaint_work_notes` table, but it did not call the notification service. The shared five-second bell polling already refreshes the newest persisted notification page and unread count for authenticated users, including while the dropdown is closed.
- After a COMMENT row is saved and flushed, the same transaction creates persistent notification rows. Student comments notify currently assigned Staff and active Admins; Staff comments notify the complaint reporter and active Admins; Admin comments notify the reporter and currently assigned Staff. Each comment event also notifies active Faculty monitoring a Student-reported complaint, matching Faculty's existing campus-wide Student-complaint read scope. The author is excluded, and Faculty remains read-only under the existing policy.
- Notification messages include only the complaint reference and event description, not the comment text. Recipient IDs are derived from the saved author, complaint reporter, active assignments, and active Admin records. A per-comment/per-recipient event key plus the existing notification unique constraint prevents duplicate notification delivery for the same saved comment; notification rows retain the existing creation timestamp.
- Added additive Flyway V9 with a nullable per-comment request UUID and a unique complaint/author/request constraint. The frontend reuses that UUID if a submission response is lost and the same comment is retried; the API returns the existing comment instead of creating another. Reusing the same key with different content returns HTTP 409. Existing work-note rows retain NULL request IDs.
- Existing comment validation (nonblank, maximum 1,000 characters), JWT-derived authorship, complaint access rules, and authenticated recipient-scoped notification endpoints are unchanged. Notification creation occurs after the comment save within the existing transaction, so a notification failure rolls back the comment rather than returning a false success.
- Verification: isolated-H2 backend package passed 40 tests; comment flow coverage verifies Student/Staff/Admin/Faculty recipient counts via authenticated unread-count responses, list entry type and complaint reference, author exclusion, private comment text not appearing in notifications, same-comment retry returning the same ID, conflicting retry returning HTTP 409, same-comment notification deduplication, blank/overlength rejection, unauthorized writes, and Faculty read-only behavior. Frontend `npm test` passed 20 tests and `npm run build` passed. The final updated backend JAR was staged separately and the confirmed backend process restarted; startup applied additive Flyway V9, advancing live schema from v8 to v9 without rewriting existing rows. Live health and database-health returned HTTP 200 and anonymous unread-count remained HTTP 401. No authenticated real-account or multi-browser delivery check was possible because the available browser sessions are signed out.

## Faculty Complaint View Image Authorization

- Runtime log evidence showed authorized complaint-image requests failing with `LazyInitializationException` while `findAuthorizedComplaint` accessed the lazy-loaded reporter role outside a transaction. This was an HTTP 500, not an authentication failure; the frontend only clears a session for a matching authenticated 401 response.
- Added a read-only transaction to `ComplaintService.getImage`, keeping the existing role/ownership authorization check inside the persistence context. Added a Faculty integration test with the test caller transaction disabled to reproduce and prevent regression.
- Verification: the focused Faculty image authorization integration test passed; full backend package passed 41 tests and frontend tests/build passed. The tested JAR was deployed by restarting the verified CampusCare process; live health and database-health returned HTTP 200, and anonymous unread-count remained HTTP 401. An existing browser session still passed `/api/auth/me` with HTTP 200 after restart. A live Faculty credential was not available to verify the dashboard and View workflow in a real signed-in browser.

## Phase 14 — Admin Audit Trail and People Navigation

- The Admin sidebar routes People & departments to `/admin/people`, rendering the existing account and department management section directly. The route works on direct entry and refresh and participates in browser history. The legacy `/admin/dashboard#people` URL still opens that section with the correct active navigation item; this avoids depending on hash scrolling before dashboard data finishes loading.
- Added `/admin/audit-trail` and an ADMIN-only, paginated `/api/admin/audit` endpoint backed by additive Flyway V10 table `audit_trail`. Records resolve the actor from the JWT principal and database, include safe before/after state where relevant, and omit secrets and note contents. Each audit write joins the business transaction and uses a unique event key.
- Recorded successful complaint submissions and attachment uploads, comments, resolution notes, work notes, assignment/status/resolution/proof events, Admin Staff/Faculty/Student account provisioning/edits/activation/deactivation, and department create/edit/activation/deactivation. No legacy events are fabricated or backfilled.
- Verification: existing Maven reports show passing backend suites, including audit checks for account/department events, server-derived actor attribution, filtering/pagination, no password disclosure, and ADMIN access control; those reports predate the second audit integration test in the current source, and Maven is unavailable in this workspace to rerun them. The prebuilt Phase 14 application was started against MySQL, confirmed schema version 10 is current, returned healthy, and returned 401 for an unauthenticated audit request.
