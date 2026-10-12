# Campus Complaint and Resolution Management System

This project is a full-stack campus management application designed to help colleges manage complaints related to infrastructure, utilities, classroom conditions, digital resources, and campus facilities.

## Technology Stack

- Frontend: React, Vite, Bootstrap, React Router, Axios
- Backend: Java 21, Spring Boot, Maven
- Database: MySQL 8.0, Spring Data JPA/Hibernate, Flyway
- Testing: JUnit, Spring Boot testing tools, frontend production build verification

## Prerequisites

- Java 21
- Maven
- Node.js 18+
- npm 9+
- MySQL Server 8.0.46
- A Base64 JWT signing secret containing at least 32 random bytes

## Project Structure

```text
CampusComplaintManagement/
├── backend/
│   ├── pom.xml
│   └── src/
├── frontend/
│   ├── package.json
│   ├── vite.config.js
│   ├── index.html
│   └── src/
├── database/
│   └── README.md
├── docs/
│   ├── PROJECT_PLAN.md
│   ├── DATABASE_DESIGN.md
│   ├── API_CONTRACT.md
│   ├── SECURITY.md
│   └── PROGRESS.md
├── README.md
├── .gitignore
└── .
```

## Frontend Setup

```bash
cd frontend
npm install
npm run dev
```

Open: http://localhost:5174

## Backend Setup

Set database configuration in the same Windows CMD terminal used to start the backend. Replace the password placeholder locally; never commit a real password:

```cmd
set "DB_USERNAME=root"
set "DB_PASSWORD=<your-local-MySQL-password>"
set "DB_URL=jdbc:mysql://localhost:3306/campus_complaint_management"
set "UPLOAD_DIR=C:\Users\gango\OneDrive\Desktop\CampusComplaintManagement\backend\uploads"
```

Create the database if needed (the MySQL client prompts for the password):

```cmd
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS campus_complaint_management CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"
```

Then run the backend from `backend`:

```bash
cd backend
mvn test
mvn package
mvn spring-boot:run
```

Also set a JWT signing secret in the same terminal. For Windows CMD, generate a Base64 value with PowerShell and copy it into the placeholder:

```cmd
set "JWT_SECRET=<base64-encoded-random-value-of-at-least-32-bytes>"
set "JWT_ACCESS_TOKEN_MINUTES=30"
set "CORS_ALLOWED_ORIGINS=http://localhost:5174,http://localhost:5173"
```

The default JDBC URL is `jdbc:mysql://localhost:3306/campus_complaint_management`; override it with `DB_URL` if needed. `DB_USERNAME` defaults to `root`; `DB_PASSWORD` and `JWT_SECRET` are required. Flyway applies versioned migrations and Hibernate validates the schema without dropping or updating tables.

Complaint images are validated JPG/PNG/WEBP files (maximum 5 MiB) saved outside the JAR under `UPLOAD_DIR`; MySQL stores a generated file reference. Keep this directory on persistent storage and back it up with the database. The default is `.\uploads` relative to the backend working directory. Authenticated image retrieval uses the complaint image and resolution-proof API endpoints; the frontend may create temporary `blob:` object URLs to render fetched bytes, but these are browser-local display URLs and must never be persisted.

## SLA deadlines and escalation

Complaint SLAs use elapsed hours, stored and compared as UTC `LocalDateTime` values; the duration starts at submission and is fixed from the priority at that time. Hibernate and each MySQL connection use UTC, and V11 backfills MySQL timestamps in a UTC session. Defaults are URGENT (Critical) 4 hours, HIGH 24 hours, MEDIUM 72 hours, and LOW 120 hours. Override them with `SLA_HOURS_URGENT`, `SLA_HOURS_HIGH`, `SLA_HOURS_MEDIUM`, and `SLA_HOURS_LOW`. The final 20% of each target duration is `DUE_SOON` (configurable through `SLA_DUE_SOON_PERCENT`). RESOLVED and CLOSED stop the SLA clock.

The scheduler checks unresolved overdue complaints every 60 seconds by default (`SLA_ESCALATION_CHECK_MS`). It records escalation level 1 as soon as the deadline is reached, level 2 after 24 elapsed overdue hours, and level 3 after 48 hours. Active Admin accounts receive deduplicated notifications. The Admin-only SLA dashboard is available at `/admin/sla`; its list and summary use backend complaint data. Flyway V11 additively stores the SLA dates and escalation history and initializes dates from existing complaint priorities/submission times and genuine resolution history; it does not create historical escalation events.

For Windows CMD, first change to the backend folder with `cd /d "C:\Users\gango\OneDrive\Desktop\CampusComplaintManagement\backend"` and add Maven to PATH for that terminal if it is not already available:

```cmd
set "PATH=C:\Users\gango\Maven\apache-maven-3.10.0\bin;%PATH%"
```

Run tests with `mvn test`, package with `mvn package`, then start with `mvn spring-boot:run`. Keep the database and JWT environment values in that terminal. The Vite frontend runs in a separate terminal from `frontend`.

To initialize the department table on first startup, set `INITIAL_DEPARTMENT_NAME` in the backend's Windows CMD terminal to the campus-approved name before starting Spring Boot:

```cmd
set "INITIAL_DEPARTMENT_NAME=<official-department-name>"
```

The initializer inserts the configured name only when the `departments` table has no rows; it does nothing when unset and never modifies existing rows. Clear the variable after initialization if you do not want it retained in that terminal (`set "INITIAL_DEPARTMENT_NAME="`).

The redesigned portal is available at http://localhost:5174. Student registration is `/student/register`; it requires full name, roll number, department, email, and password. Separate role login pages are `/student/login`, `/faculty/login`, `/staff/login`, and `/admin/login`. Public registration always creates a Student account. Configure an official department via the Admin dashboard or set `INITIAL_DEPARTMENT_NAME` to the campus-approved name before startup when initializing an empty departments table. See [docs/SECURITY.md](docs/SECURITY.md) for environment configuration, controlled Admin bootstrap, and Admin-only Faculty/Staff provisioning.

Authenticated Students can submit complaints, view `/student/complaints`, and open individual complaint details. Their complaint history comes from `GET /api/complaints/my`; details reuse `GET /api/complaints/{id}` and include stored assignment/status-history information only when available. Faculty can submit complaints at `/faculty/complaints/new`, view their own submission history at `/faculty/my-complaints`, and use `/faculty/dashboard`, `/faculty/complaints`, `/faculty/analytics`, and `/faculty/reports`. Faculty's personal history is principal-scoped through the same `/api/complaints/my` endpoint. The separate campus-wide Faculty monitoring APIs include only Student-reported complaints; Faculty can read those records but cannot assign, change status, or delete them.

Admins can open **All complaints** from the administration navigation. The page reads `GET /api/complaints`, supports ID/title, status, category, priority and department filters, and provides client-side pagination. Admins can assign or reassign complaints to active Staff in an active matching department from the complaint list or details. The existing `complaints.department_id`, `users.department_id`, and `complaint_assignments` relationships are reused; no schema migration is required. Reassignment retains prior assignment history, and assignment does not change complaint status. Details reuse `GET /api/complaints/{id}` and the protected image route.

Staff can view their own assigned queue at `/staff/dashboard`. `GET /api/complaints/assigned` derives the Staff identity from the authenticated JWT, so a caller cannot request another Staff member's queue by changing a user ID. The dashboard shows live assignment/status counts and complaint context. Assigned Staff can advance complaints through `SUBMITTED`/`UNDER_REVIEW` → `ASSIGNED` → `IN_PROGRESS`; resolving requires a resolution image and note. The authenticated workflow stores proof separately, then records `RESOLVED` in status history. Admin may close a resolved complaint. Staff notes and resolution proof are displayed only through authorized complaint details/image endpoints. The complaint reporter's Faculty owner may read notes and proof on that complaint only. Student, Faculty (subject to their existing owner permissions), Staff, and Admin details use the same protected resolution-proof image endpoint and stored image reference; Student details continue to omit internal Staff work notes.

Authenticated dashboards for Student, Staff, Faculty, and Admin include a notification bell backed by the additive Flyway V7 `notifications` table. Successful complaint submission notifies the reporter and active Admin accounts; assignment/reassignment notifies affected Staff and the complaint reporter; complaint status changes notify the reporter. `GET /api/notifications` and `/unread-count`, plus the read/read-all PATCH endpoints, derive the recipient from the authenticated JWT. Notifications are paginated, persisted, and scoped to that user; Faculty receives complaint notifications for their own submitted complaints, not every Student report visible campus-wide. Notifications do not use push delivery or polling.

Complaint details include a persistent Comments stream for authenticated complaint viewers. Students may comment only on their own complaints, assigned Staff and Admin may comment within their existing management scope, and Faculty remains read-only. Comments display author name/role and timestamp. Assigned Staff and Admin also have a separate Resolution Notes stream; adding a note does not change status or replace the existing proof-required resolution workflow. These record types share `complaint_work_notes` using additive Flyway V8; existing Staff work notes are retained and remain separate from student-visible comments.

Open:

- Application health: http://localhost:8080/api/health
- Database health: http://localhost:8080/api/health/database
- Authentication profile (requires bearer token): http://localhost:8080/api/auth/me

## Local URLs

- Frontend: http://localhost:5174
- Backend health API: http://localhost:8080/api/health

## Current Limitations

- The application foundation, student registration/login, JWT authentication, and role-based access controls are implemented.
- Student complaint submission, own-history and detail/image retrieval are implemented. Submissions begin in `SUBMITTED`, create an initial status-history record, and accept an optional validated image.
- Complaint assignment, Staff assigned-complaint viewing, validated status transitions, persistent Staff work notes, comments, resolution notes, resolution-proof image storage/display, persistent authenticated notifications, and SLA deadline tracking/escalation are implemented.
- The redesigned dashboards use current API responses. Admins can edit non-Admin account profiles and soft-deactivate/reactivate those accounts, and can edit/deactivate/reactivate departments without removing relational history. Account roles remain immutable through editing; Admin profiles cannot be edited or deactivated here.
- Faculty monitoring is read-only and campus-wide for Student-submitted complaints. Lists, details, protected attachments/resolution proofs, summary metrics, analytics, filters, and CSV exports use that same Student reporter-role scope. Faculty may also submit complaints; those are saved under the authenticated Faculty reporter and shown only in that Faculty member's personal history, separate from the Student campus-wide list. No database migration or new dependency is required.
- Automated complaint-flow tests use isolated H2; V3 applies additively to MySQL at application startup and does not modify V1 or V2.

## Project Roadmap

1. Foundation, homepage, portals, navigation and backend health API
2. MySQL configuration, schema, relationships and connectivity verification
3. Registration, login, faculty verification, staff/admin provisioning and role-based security (initial auth and provisioning foundations implemented)
4. Student/faculty complaint workflows and lifecycle tracking (student submission and history foundation implemented)
5. Admin review, assignment and management features
6. Staff complaint handling and resolution workflow
7. Notifications implemented; escalation, analytics and exports
8. Testing, integration verification, security checks and deployment preparation
