CREATE TABLE task_attachments (
    id uuid PRIMARY KEY,
    task_id uuid NOT NULL REFERENCES tasks(id) ON DELETE RESTRICT,
    uploaded_by uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    original_name varchar(255) NOT NULL CHECK (length(trim(original_name)) > 0),
    storage_key varchar(500) NOT NULL UNIQUE,
    content_type varchar(255) NOT NULL,
    file_size bigint NOT NULL CHECK (file_size > 0),
    checksum varchar(64) CHECK (checksum IS NULL OR checksum ~ '^[0-9a-f]{64}$'),
    created_at timestamptz NOT NULL DEFAULT now(),
    deleted_at timestamptz
);

CREATE INDEX ix_task_attachments_active_task
    ON task_attachments (task_id, created_at, id)
    WHERE deleted_at IS NULL;

