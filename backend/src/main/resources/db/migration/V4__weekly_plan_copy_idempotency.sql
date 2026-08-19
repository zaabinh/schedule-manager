CREATE TABLE api_idempotency_keys (
    actor_user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    operation varchar(80) NOT NULL,
    idempotency_key varchar(200) NOT NULL,
    resource_id uuid NOT NULL,
    warnings jsonb NOT NULL DEFAULT '[]'::jsonb,
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (actor_user_id, operation, idempotency_key)
);

CREATE INDEX ix_api_idempotency_created ON api_idempotency_keys (created_at);
