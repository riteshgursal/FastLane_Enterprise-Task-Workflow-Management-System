# Flowlane

**Every task, in its lane.** Flowlane is a Java-first task and workflow platform: workspaces, projects, Kanban boards, calendars, approvals and reports, with status rules the server enforces.

```
backend/    Spring Boot 4 API (Java 21, MySQL 8.4, Flyway)
frontend/   React + TypeScript web client (Vite, Tailwind, TanStack Query)
design/     Brand and prototype screens the UI was built from
```

## Quick start

### With Docker (everything)

```bash
cp .env.example .env          # then set DB_PASSWORD, DB_ROOT_PASSWORD and JWT_SECRET
docker compose up --build
```

Open http://localhost:8081, create an account, then a workspace and a project.

### For development

```bash
cp .env.example .env
docker compose up -d mysql

# Terminal 1: API on :8080 (dev profile enables Swagger UI)
cd backend
set -a; source ../.env; set +a
./mvnw spring-boot:run

# Terminal 2: web client on :5173 (proxies /api to :8080)
cd frontend
npm install
npm run dev
```

On Windows use `mvnw.cmd` and set the variables from `.env` in your shell or IDE run configuration.

| URL | What |
|---|---|
| http://localhost:5173 | Web client (dev) |
| http://localhost:8080/api/v1 | REST API |
| http://localhost:8080/swagger-ui.html | API docs (dev profile only) |
| http://localhost:8080/actuator/health | Health check |

## Features

| Area | What works |
|---|---|
| Accounts | Register, sign in, rotating refresh tokens with reuse detection, sign out, sign out other devices, change password, rate limiting and lockout after 5 failed attempts |
| Workspaces | Create and switch workspaces, invite by email (7-day links), accept invitations, change roles, remove members, last-owner protection |
| Projects | Create with key and colour, project roles (Manager, Contributor, Viewer), progress and overdue counts |
| Tasks | Keys like `WEB-12`, assignee, priority, due and start dates, labels, estimates, one level of subtasks, dependencies, weekly or monthly repeats, soft delete, optimistic locking |
| Board | Drag and drop between and within columns (fractional ranks), list view with bulk assign and priority, priority filters |
| Workflow | Per-project statuses mapped to fixed categories, editable transition matrix, approval-required transitions, open-subtask and dependency checks, append-only history |
| Collaboration | Comments with @mentions, file attachments up to 25 MB, activity feed |
| Notifications | In-app inbox with filters and unread counts; emails for assignments, mentions, approvals and due dates via a retrying outbox; daily reminder job |
| Calendar | Month and week views across projects, overdue highlighting, recurring tasks |
| Reports | Status mix, completed per week, workload by assignee, project health, CSV export, print to PDF |
| Settings | Dark, light or system theme saved to the profile, per-type email preferences, time zone |

## Architecture

A modular monolith. Each module owns its entities, repositories, services and controllers; modules talk through services and domain events.

```
com.flowlane
├── common        config, security (JWT, AccessGuard, rate limit), exceptions (RFC 9457), persistence (UUIDv7, ranks), events
├── identity      users, authentication, refresh tokens, profile
├── workspace     workspaces, members, invitations
├── project       projects, project members
├── task          tasks, dependencies, board, search, calendar
├── workflow      statuses, transitions, approvals, history
├── comment       comments and mentions
├── attachment    uploads and downloads (local storage, swappable)
├── notification  in-app notifications, email outbox, reminders
└── reporting     overview metrics and CSV export
```

A request flows through the JWT filter, a thin controller, then a transactional service that checks access with `AccessGuard` before touching data. Services publish domain events; listeners in the same transaction write notifications and queue emails, and a scheduled worker sends them with exponential backoff.

### Key decisions

| Decision | Choice |
|---|---|
| Versions | Java 21, Spring Boot 4.0.x, MySQL 8.4 LTS |
| IDs | UUIDv7 primary keys stored as `BINARY(16)`; human task keys from a row-locked per-project counter |
| Tenancy | `workspace_id` on every tenant table; non-members get 404, never 403 |
| Roles | Workspace (Owner, Admin, Member) and project (Manager, Contributor, Viewer); admins act as managers everywhere |
| Workflow | Fixed categories `TODO`, `IN_PROGRESS`, `DONE`, `CANCELLED` behind custom statuses, so reports compare across projects |
| Ordering | Fractional string ranks for drag and drop; `@Version` optimistic locking (409 on conflict) |
| Auth | 15-minute HS256 access token kept in memory; opaque refresh token stored hashed, rotated on use, sent as an `HttpOnly; SameSite=Strict` cookie scoped to `/api/v1/auth`; refresh and logout also require the `X-Flowlane-Csrf` header |
| Errors | RFC 9457 Problem Details with a stable `code` field |
| Schema | Flyway owns it; Hibernate never changes it |

## API overview

All paths are under `/api/v1`. Lists support `page`, `size` (max 100) and whitelisted `sort`.

| Area | Endpoints |
|---|---|
| Auth | `POST /auth/register` `/auth/login` `/auth/refresh` `/auth/logout` `/auth/sessions/revoke-others` |
| Me | `GET/PATCH /users/me`, `POST /users/me/password` |
| Workspaces | `GET/POST /workspaces`, `PATCH /workspaces/{id}`, `POST /workspaces/{id}/archive` |
| Members | `GET /workspaces/{id}/members`, `PATCH/DELETE /workspaces/{id}/members/{userId}` |
| Invitations | `GET/POST /workspaces/{id}/invitations`, `POST …/{invitationId}/resend`, `DELETE …/{invitationId}`, `GET /invitations/{token}`, `POST /invitations/{token}/accept` |
| Projects | `GET/POST /workspaces/{id}/projects`, `GET/PATCH /projects/{id}`, `POST /projects/{id}/archive`, `GET/POST/PATCH/DELETE /projects/{id}/members…` |
| Tasks | `GET/POST /projects/{id}/tasks`, `GET /projects/{id}/board`, `POST /projects/{id}/tasks/bulk`, `GET/PATCH/DELETE /tasks/{id}`, `GET /workspaces/{id}/tasks/by-key/{key}` |
| Moves | `POST /tasks/{id}/move`, `POST /tasks/{id}/transitions`, `POST /tasks/{id}/approval/approve` and `/reject` |
| Task details | `GET /tasks/{id}/subtasks`, `GET/POST /tasks/{id}/dependencies`, `DELETE /tasks/{id}/dependencies/{blockedById}`, `GET /tasks/{id}/history` |
| Workflow | `GET/PUT /projects/{id}/workflow` |
| Comments | `GET/POST /tasks/{id}/comments`, `PATCH/DELETE /comments/{id}` |
| Attachments | `GET/POST /tasks/{id}/attachments`, `GET /attachments/{id}/download`, `DELETE /attachments/{id}` |
| Views | `GET /workspaces/{id}/my-tasks`, `/calendar?from&to`, `/search?q`, `/approvals`, `/activity` |
| Notifications | `GET /notifications`, `GET /notifications/unread-count`, `PATCH /notifications/{id}/read`, `POST /notifications/read-all` |
| Reports | `GET /workspaces/{id}/reports/overview?weeks=6`, `GET …/overview.csv` |

## Security notes

- Secrets come only from the environment. `.env` is ignored by Git; `.env.example` holds placeholders.
- **The original starter repository committed a real database password.** It stays in Git history, so rotate it and publish this code from a fresh repository (or rewrite history with `git filter-repo`).
- Passwords use BCrypt (cost 12). Refresh tokens and invitation tokens are stored only as SHA-256 hashes.
- Uploads are limited to 25 MB and an allow-list of types, stored outside the database under random keys, and downloaded with `nosniff`.
- Set `COOKIE_SECURE=true` and serve over HTTPS in every deployment. In production put the API and web client on the same origin (the included nginx config does this).

## Testing

```bash
cd backend && ./mvnw verify          # unit tests (JWT, ranks, UUIDv7)
cd frontend && npm run build         # type-check and production build
cd frontend && npx playwright install && npm run test:e2e   # needs the stack running
```

## Known limits and next steps

- The login rate limiter and reminder job are per instance. Use Redis and ShedLock (or Quartz) before running more than one backend instance.
- `LoggingEmailSender` logs emails. Add an SMTP or provider-backed `EmailSender` bean for real delivery.
- `LocalFileStorage` writes to disk. Implement `FileStorage` for S3-compatible storage in production.
- Real-time updates poll every 30 seconds for unread counts; add WebSocket (STOMP) push next.
- Integration tests with Testcontainers for services, security boundaries and migrations are the next testing milestone.
- Password reset by email and SSO are not built yet.

## License

Choose a license before publishing.
