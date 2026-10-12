# Phase 3 Security Notes

## Authentication

- Passwords are BCrypt-hashed before persistence (BCrypt work factor 12); clear-text passwords and hashes are not exposed by profile DTOs or logs.
- Passwords must be at least 12 characters and contain a letter and a number; input is limited to BCrypt's 72 UTF-8-byte maximum.
- Access tokens are signed JWTs with an expiry configured by `JWT_ACCESS_TOKEN_MINUTES` (default 30 minutes). The JWT signing key must be supplied as a Base64-encoded value of at least 32 random bytes through `JWT_SECRET`.
- Every protected request verifies the signature/expiry and reloads the active user and current role from the database. User deactivation and role changes therefore take effect immediately.
- No refresh-token or password-change/reset flow exists yet.

## Environment Configuration

Set values only in the backend process environment; never commit secrets. For Windows CMD:

```cmd
set "DB_USERNAME=root"
set "DB_PASSWORD=<your-local-MySQL-password>"
set "DB_URL=jdbc:mysql://localhost:3306/campus_complaint_management"
set "JWT_SECRET=<base64-encoded-random-value-of-at-least-32-bytes>"
set "JWT_ACCESS_TOKEN_MINUTES=30"
set "CORS_ALLOWED_ORIGINS=http://localhost:5173,http://localhost:5174"
set "INITIAL_DEPARTMENT_NAME=<official-department-name>"
set "UPLOAD_DIR=C:\Users\gango\OneDrive\Desktop\CampusComplaintManagement\backend\uploads"
```

`INITIAL_DEPARTMENT_NAME` is optional. When set, the startup initializer inserts it only if `departments` is completely empty; if any row exists, it skips initialization without editing that row. Use only a campus-approved name, never a placeholder.

Generate a signing secret in PowerShell, then copy the printed Base64 value into the CMD `JWT_SECRET` assignment. Do not paste the generated value into source control or a shared document:

```powershell
[Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
```

The backend intentionally has no fallback production JWT secret; without a valid `JWT_SECRET` it will not start. CORS allows only the configured exact origins and includes the Vite defaults at ports 5173 and 5174.

## Initial Administrator and Staff/Faculty Provisioning

Public registration always creates a Student. It ignores a supplied `role`; it does not create Faculty, Staff, or Admin accounts.

For a brand-new installation with no Admin, a designated operator may perform a one-time bootstrap through environment variables. Use a secure, private terminal, set a strong unique password, and enable bootstrap only for this startup:

```cmd
set "BOOTSTRAP_ADMIN_ENABLED=true"
set "BOOTSTRAP_ADMIN_NAME=<administrator-name>"
set "BOOTSTRAP_ADMIN_EMAIL=<administrator-email>"
set "BOOTSTRAP_ADMIN_PASSWORD=<unique-password-of-at-least-12-characters>"
mvn spring-boot:run
```

After confirming the Admin can log in, stop the process and clear the bootstrap variables before normal startup:

```cmd
set "BOOTSTRAP_ADMIN_ENABLED=false"
set "BOOTSTRAP_ADMIN_NAME="
set "BOOTSTRAP_ADMIN_EMAIL="
set "BOOTSTRAP_ADMIN_PASSWORD="
mvn spring-boot:run
```

Bootstrap refuses to run if an Admin already exists. It is not an HTTP endpoint. For Faculty and Staff, an already-authorized Admin uses the protected `POST /api/admin/users` endpoint. The endpoint rejects `STUDENT` and `ADMIN` roles; further Admins require an operator-controlled procedure. Admins may update non-Admin profiles and deactivate/reactivate non-Admin accounts. Roles are immutable through account editing, and Admin accounts cannot be edited or deactivated using these endpoints. Department editing and deactivation/reactivation are also Admin-only; deactivation retains user and complaint relationships.

## Browser Token Storage

The prototype stores its short-lived JWT in `sessionStorage`: it is cleared when the tab's session ends and is not a password store. JavaScript running in the origin can still read it, so cross-site scripting (XSS) could steal the token. Use a hardened production frontend and consider migrating to a secure, HttpOnly, SameSite cookie with CSRF protections before deployment.

## Postman Smoke Tests

1. Start MySQL and the backend with the environment variables above; ensure `campus_complaint_management` exists.
2. Ensure an active official department is configured, then send `POST http://localhost:8080/api/auth/register` with JSON:
   `{"name":"Example Student","rollNumber":"00012345","departmentId":1,"email":"student@example.edu","password":"CampusExample123"}`.
3. Copy `accessToken` from the response. Send `GET http://localhost:8080/api/auth/me` with header `Authorization: Bearer <accessToken>`.
4. Send `POST http://localhost:8080/api/auth/login` with the same email/password; try an incorrect password and verify HTTP 401.
5. Send `GET http://localhost:8080/api/admin/users` without a token (HTTP 401) and using the Student token (HTTP 403).
6. After initial Admin bootstrap, log in as that Admin and use its token for `POST /api/admin/users` to provision a Faculty or Staff account. Admin department management and non-Admin account edits/deactivation are also available under `/api/admin/departments` and `/api/admin/users`.

Use sample credentials only in local testing; never use real passwords in shared Postman collections or examples.

## Limitations

- No refresh-token, logout-revocation, password reset, or password change workflow.
- The UI stores access tokens in `sessionStorage`, as described above.
- Student registration requires an active department and roll number. Class and section are optional and preserved for compatibility with existing records.
- To initialize an empty department table at startup, an operator may set `INITIAL_DEPARTMENT_NAME` to the campus's actual approved department name. The startup initializer inserts it only when no department rows exist and does not overwrite existing departments. Never configure an invented placeholder; without this variable the Admin-managed setup flow remains available.
- Complaint images are stored outside the packaged JAR in the configured `UPLOAD_DIR`. Back up this directory with the database; for another computer/server, set `UPLOAD_DIR` to a persistent writable volume before starting the backend. Do not configure it under `src/main/resources`.
- Students can access only their own complaints; Staff can access only currently assigned complaints; Faculty can access only complaints they submitted; Admin can view complaints. Assignment creation and status workflows are not implemented.
- Password reset is not implemented. Authenticated in-app notifications are persisted and scoped to the recipient; see [API_CONTRACT.md](./API_CONTRACT.md).
