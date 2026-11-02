-- Flowlane baseline schema. UUIDv7 ids stored as BINARY(16); timestamps in UTC.

CREATE TABLE app_user (
    id                BINARY(16)   NOT NULL PRIMARY KEY,
    version           BIGINT       NOT NULL,
    created_at        DATETIME(6)  NOT NULL,
    updated_at        DATETIME(6)  NOT NULL,
    email             VARCHAR(254) NOT NULL,
    password_hash     VARCHAR(100) NOT NULL,
    display_name      VARCHAR(80)  NOT NULL,
    system_role       VARCHAR(16)  NOT NULL,
    timezone          VARCHAR(64)  NOT NULL DEFAULT 'UTC',
    theme             VARCHAR(16)  NOT NULL DEFAULT 'system',
    enabled           BOOLEAN      NOT NULL DEFAULT TRUE,
    failed_logins     INT          NOT NULL DEFAULT 0,
    locked_until      DATETIME(6)  NULL,
    notify_assigned   BOOLEAN      NOT NULL DEFAULT TRUE,
    notify_mentions   BOOLEAN      NOT NULL DEFAULT TRUE,
    notify_due        BOOLEAN      NOT NULL DEFAULT TRUE,
    notify_approvals  BOOLEAN      NOT NULL DEFAULT TRUE,
    daily_digest      BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_user_email UNIQUE (email)
);

CREATE TABLE refresh_token (
    id           BINARY(16)  NOT NULL PRIMARY KEY,
    version      BIGINT      NOT NULL,
    created_at   DATETIME(6) NOT NULL,
    updated_at   DATETIME(6) NOT NULL,
    user_id      BINARY(16)  NOT NULL,
    token_hash   CHAR(64)    NOT NULL,
    family_id    BINARY(16)  NOT NULL,
    expires_at   DATETIME(6) NOT NULL,
    revoked_at   DATETIME(6) NULL,
    replaced_by  BINARY(16)  NULL,
    CONSTRAINT uq_refresh_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE
);
CREATE INDEX ix_refresh_family ON refresh_token (family_id);

CREATE TABLE workspace (
    id           BINARY(16)   NOT NULL PRIMARY KEY,
    version      BIGINT       NOT NULL,
    created_at   DATETIME(6)  NOT NULL,
    updated_at   DATETIME(6)  NOT NULL,
    name         VARCHAR(80)  NOT NULL,
    slug         VARCHAR(80)  NOT NULL,
    owner_id     BINARY(16)   NOT NULL,
    archived_at  DATETIME(6)  NULL,
    CONSTRAINT uq_workspace_slug UNIQUE (slug),
    CONSTRAINT fk_workspace_owner FOREIGN KEY (owner_id) REFERENCES app_user (id)
);

CREATE TABLE workspace_member (
    id            BINARY(16)  NOT NULL PRIMARY KEY,
    version       BIGINT      NOT NULL,
    created_at    DATETIME(6) NOT NULL,
    updated_at    DATETIME(6) NOT NULL,
    workspace_id  BINARY(16)  NOT NULL,
    user_id       BINARY(16)  NOT NULL,
    role          VARCHAR(16) NOT NULL,
    CONSTRAINT uq_ws_member UNIQUE (workspace_id, user_id),
    CONSTRAINT fk_wsm_ws FOREIGN KEY (workspace_id) REFERENCES workspace (id) ON DELETE CASCADE,
    CONSTRAINT fk_wsm_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE
);
CREATE INDEX ix_wsm_user ON workspace_member (user_id);

CREATE TABLE workspace_invitation (
    id            BINARY(16)   NOT NULL PRIMARY KEY,
    version       BIGINT       NOT NULL,
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    workspace_id  BINARY(16)   NOT NULL,
    email         VARCHAR(254) NOT NULL,
    role          VARCHAR(16)  NOT NULL,
    token_hash    CHAR(64)     NOT NULL,
    invited_by    BINARY(16)   NOT NULL,
    expires_at    DATETIME(6)  NOT NULL,
    accepted_at   DATETIME(6)  NULL,
    revoked_at    DATETIME(6)  NULL,
    CONSTRAINT uq_invite_hash UNIQUE (token_hash),
    CONSTRAINT fk_invite_ws FOREIGN KEY (workspace_id) REFERENCES workspace (id) ON DELETE CASCADE
);
CREATE INDEX ix_invite_ws ON workspace_invitation (workspace_id, accepted_at);

CREATE TABLE project (
    id                BINARY(16)   NOT NULL PRIMARY KEY,
    version           BIGINT       NOT NULL,
    created_at        DATETIME(6)  NOT NULL,
    updated_at        DATETIME(6)  NOT NULL,
    workspace_id      BINARY(16)   NOT NULL,
    project_key       VARCHAR(10)  NOT NULL,
    name              VARCHAR(80)  NOT NULL,
    description       VARCHAR(2000) NULL,
    color             VARCHAR(7)   NOT NULL,
    start_date        DATE         NULL,
    end_date          DATE         NULL,
    next_task_number  INT          NOT NULL DEFAULT 1,
    archived_at       DATETIME(6)  NULL,
    CONSTRAINT uq_project_key UNIQUE (workspace_id, project_key),
    CONSTRAINT fk_project_ws FOREIGN KEY (workspace_id) REFERENCES workspace (id) ON DELETE CASCADE
);

CREATE TABLE project_member (
    id            BINARY(16)  NOT NULL PRIMARY KEY,
    version       BIGINT      NOT NULL,
    created_at    DATETIME(6) NOT NULL,
    updated_at    DATETIME(6) NOT NULL,
    workspace_id  BINARY(16)  NOT NULL,
    project_id    BINARY(16)  NOT NULL,
    user_id       BINARY(16)  NOT NULL,
    role          VARCHAR(16) NOT NULL,
    CONSTRAINT uq_project_member UNIQUE (project_id, user_id),
    CONSTRAINT fk_pm_project FOREIGN KEY (project_id) REFERENCES project (id) ON DELETE CASCADE,
    CONSTRAINT fk_pm_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE
);
CREATE INDEX ix_pm_user ON project_member (user_id);

CREATE TABLE task_status (
    id            BINARY(16)  NOT NULL PRIMARY KEY,
    version       BIGINT      NOT NULL,
    created_at    DATETIME(6) NOT NULL,
    updated_at    DATETIME(6) NOT NULL,
    workspace_id  BINARY(16)  NOT NULL,
    project_id    BINARY(16)  NOT NULL,
    name          VARCHAR(40) NOT NULL,
    category      VARCHAR(16) NOT NULL,
    color         VARCHAR(7)  NOT NULL,
    position      INT         NOT NULL,
    is_initial    BOOLEAN     NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_status_name UNIQUE (project_id, name),
    CONSTRAINT fk_status_project FOREIGN KEY (project_id) REFERENCES project (id) ON DELETE CASCADE
);

CREATE TABLE task_transition (
    id                 BINARY(16)  NOT NULL PRIMARY KEY,
    version            BIGINT      NOT NULL,
    created_at         DATETIME(6) NOT NULL,
    updated_at         DATETIME(6) NOT NULL,
    workspace_id       BINARY(16)  NOT NULL,
    project_id         BINARY(16)  NOT NULL,
    from_status_id     BINARY(16)  NOT NULL,
    to_status_id       BINARY(16)  NOT NULL,
    requires_approval  BOOLEAN     NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_transition UNIQUE (from_status_id, to_status_id),
    CONSTRAINT fk_tr_project FOREIGN KEY (project_id) REFERENCES project (id) ON DELETE CASCADE,
    CONSTRAINT fk_tr_from FOREIGN KEY (from_status_id) REFERENCES task_status (id) ON DELETE CASCADE,
    CONSTRAINT fk_tr_to FOREIGN KEY (to_status_id) REFERENCES task_status (id) ON DELETE CASCADE
);

CREATE TABLE task (
    id                          BINARY(16)    NOT NULL PRIMARY KEY,
    version                     BIGINT        NOT NULL,
    created_at                  DATETIME(6)   NOT NULL,
    updated_at                  DATETIME(6)   NOT NULL,
    workspace_id                BINARY(16)    NOT NULL,
    project_id                  BINARY(16)    NOT NULL,
    number                      INT           NOT NULL,
    task_key                    VARCHAR(20)   NOT NULL,
    title                       VARCHAR(200)  NOT NULL,
    description                 TEXT          NULL,
    status_id                   BINARY(16)    NOT NULL,
    pending_approval_status_id  BINARY(16)    NULL,
    priority                    VARCHAR(10)   NOT NULL,
    assignee_id                 BINARY(16)    NULL,
    reporter_id                 BINARY(16)    NOT NULL,
    parent_task_id              BINARY(16)    NULL,
    start_date                  DATE          NULL,
    due_date                    DATE          NULL,
    estimate_points             INT           NULL,
    labels                      VARCHAR(500)  NULL,
    recurrence                  VARCHAR(10)   NOT NULL DEFAULT 'NONE',
    task_rank                   VARCHAR(64)   NOT NULL,
    completed_at                DATETIME(6)   NULL,
    deleted_at                  DATETIME(6)   NULL,
    CONSTRAINT uq_task_number UNIQUE (project_id, number),
    CONSTRAINT fk_task_project FOREIGN KEY (project_id) REFERENCES project (id) ON DELETE CASCADE,
    CONSTRAINT fk_task_status FOREIGN KEY (status_id) REFERENCES task_status (id),
    CONSTRAINT fk_task_assignee FOREIGN KEY (assignee_id) REFERENCES app_user (id) ON DELETE SET NULL,
    CONSTRAINT fk_task_reporter FOREIGN KEY (reporter_id) REFERENCES app_user (id),
    CONSTRAINT fk_task_parent FOREIGN KEY (parent_task_id) REFERENCES task (id) ON DELETE SET NULL
);
CREATE INDEX ix_task_board ON task (project_id, status_id, task_rank);
CREATE INDEX ix_task_assignee ON task (workspace_id, assignee_id, due_date);
CREATE INDEX ix_task_due ON task (workspace_id, due_date);
CREATE INDEX ix_task_key ON task (workspace_id, task_key);

CREATE TABLE task_dependency (
    id              BINARY(16)  NOT NULL PRIMARY KEY,
    version         BIGINT      NOT NULL,
    created_at      DATETIME(6) NOT NULL,
    updated_at      DATETIME(6) NOT NULL,
    workspace_id    BINARY(16)  NOT NULL,
    task_id         BINARY(16)  NOT NULL,
    blocked_by_id   BINARY(16)  NOT NULL,
    CONSTRAINT uq_dependency UNIQUE (task_id, blocked_by_id),
    CONSTRAINT fk_dep_task FOREIGN KEY (task_id) REFERENCES task (id) ON DELETE CASCADE,
    CONSTRAINT fk_dep_blocker FOREIGN KEY (blocked_by_id) REFERENCES task (id) ON DELETE CASCADE
);

CREATE TABLE task_history (
    id            BINARY(16)   NOT NULL PRIMARY KEY,
    version       BIGINT       NOT NULL,
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    workspace_id  BINARY(16)   NOT NULL,
    project_id    BINARY(16)   NOT NULL,
    task_id       BINARY(16)   NOT NULL,
    actor_id      BINARY(16)   NOT NULL,
    action        VARCHAR(32)  NOT NULL,
    detail        VARCHAR(500) NOT NULL,
    CONSTRAINT fk_hist_task FOREIGN KEY (task_id) REFERENCES task (id) ON DELETE CASCADE
);
CREATE INDEX ix_hist_task ON task_history (task_id, created_at);
CREATE INDEX ix_hist_ws ON task_history (workspace_id, created_at);

CREATE TABLE task_comment (
    id            BINARY(16)  NOT NULL PRIMARY KEY,
    version       BIGINT      NOT NULL,
    created_at    DATETIME(6) NOT NULL,
    updated_at    DATETIME(6) NOT NULL,
    workspace_id  BINARY(16)  NOT NULL,
    task_id       BINARY(16)  NOT NULL,
    author_id     BINARY(16)  NOT NULL,
    body          TEXT        NOT NULL,
    edited_at     DATETIME(6) NULL,
    deleted_at    DATETIME(6) NULL,
    CONSTRAINT fk_comment_task FOREIGN KEY (task_id) REFERENCES task (id) ON DELETE CASCADE
);
CREATE INDEX ix_comment_task ON task_comment (task_id, created_at);

CREATE TABLE task_attachment (
    id            BINARY(16)   NOT NULL PRIMARY KEY,
    version       BIGINT       NOT NULL,
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    workspace_id  BINARY(16)   NOT NULL,
    task_id       BINARY(16)   NOT NULL,
    uploader_id   BINARY(16)   NOT NULL,
    file_name     VARCHAR(255) NOT NULL,
    content_type  VARCHAR(100) NOT NULL,
    size_bytes    BIGINT       NOT NULL,
    storage_key   VARCHAR(64)  NOT NULL,
    CONSTRAINT fk_att_task FOREIGN KEY (task_id) REFERENCES task (id) ON DELETE CASCADE
);
CREATE INDEX ix_att_task ON task_attachment (task_id);

CREATE TABLE notification (
    id            BINARY(16)   NOT NULL PRIMARY KEY,
    version       BIGINT       NOT NULL,
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    workspace_id  BINARY(16)   NULL,
    recipient_id  BINARY(16)   NOT NULL,
    actor_id      BINARY(16)   NULL,
    type          VARCHAR(20)  NOT NULL,
    task_id       BINARY(16)   NULL,
    message       VARCHAR(500) NOT NULL,
    read_at       DATETIME(6)  NULL,
    CONSTRAINT fk_notif_user FOREIGN KEY (recipient_id) REFERENCES app_user (id) ON DELETE CASCADE
);
CREATE INDEX ix_notif_recipient ON notification (recipient_id, read_at, created_at);

CREATE TABLE outbox_event (
    id               BINARY(16)   NOT NULL PRIMARY KEY,
    version          BIGINT       NOT NULL,
    created_at       DATETIME(6)  NOT NULL,
    updated_at       DATETIME(6)  NOT NULL,
    type             VARCHAR(32)  NOT NULL,
    recipient_email  VARCHAR(254) NOT NULL,
    subject          VARCHAR(200) NOT NULL,
    body             TEXT         NOT NULL,
    status           VARCHAR(12)  NOT NULL,
    attempts         INT          NOT NULL DEFAULT 0,
    next_attempt_at  DATETIME(6)  NOT NULL,
    last_error       VARCHAR(500) NULL
);
CREATE INDEX ix_outbox_due ON outbox_event (status, next_attempt_at);
