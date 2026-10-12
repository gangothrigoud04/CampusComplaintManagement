# Project Plan

## Phase 1: Foundation, homepage, portals, navigation and backend health API

This phase establishes the project foundation, the frontend structure, navigation, portal pages, and a working Spring Boot health API. It also includes the required connection between the frontend and backend using a Vite proxy and Axios.

## Phase 2: MySQL configuration, schema, relationships and connectivity verification

Implemented. This phase configures MySQL 8, defines the initial schema, validates it with Hibernate, and verifies the database connection. Application features remain deferred.

## Phase 3: Registration, login, faculty verification, staff/admin provisioning and role-based security

Initial authentication foundations implemented: student registration/login, BCrypt password hashing, JWT bearer authentication, role-aware portal access, controlled admin bootstrap, and admin-only Faculty/Staff provisioning. Faculty verification and additional account workflows remain planned.

## Phase 4: Student/faculty complaints, photos, unique IDs, search, filters, status history, resolution notes, feedback and reopening requests

Planned. This phase introduces complaint creation, tracking, and workflow updates.

## Phase 5: Admin review, rejection reasons, user management, department/category management and complaint assignment

Planned. This phase adds complaint assignment and administrative management features.

## Phase 6: Staff complaint handling, progress updates and resolution records

Planned. This phase builds the staff-resolution workflow.

## Phase 7: Duplicate warnings, in-app notifications, email notifications, overdue escalation, campus hotspot analysis, performance analytics and CSV export

Partially implemented: authenticated, persisted in-app notifications are available for complaint submission, assignment, and status events. Duplicate warnings, email delivery, escalation, hotspot analysis, and additional reporting remain planned.

## Phase 8: Testing, integration verification, security checks, bug fixes and deployment preparation

Planned. This phase focuses on final verification and release readiness.

## Phase 13: Comments and resolution notes

Implemented in source: complaint comments and separately permissioned resolution notes use additive Flyway V8 and authenticated APIs. See the Phase 13 verification record in [PROGRESS.md](./PROGRESS.md).
