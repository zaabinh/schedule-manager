CREATE TABLE departments (
    id uuid PRIMARY KEY,
    name varchar(150) NOT NULL,
    normalized_name varchar(150) NOT NULL UNIQUE,
    description text,
    is_active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE business_roles (
    id uuid PRIMARY KEY,
    name varchar(150) NOT NULL,
    normalized_name varchar(150) NOT NULL UNIQUE,
    description text,
    is_protected boolean NOT NULL DEFAULT false,
    is_active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE users (
    id uuid PRIMARY KEY,
    email varchar(320) NOT NULL,
    normalized_email varchar(320) NOT NULL UNIQUE,
    password_hash varchar(255) NOT NULL,
    display_name varchar(150) NOT NULL,
    system_role varchar(20) NOT NULL CHECK (system_role IN ('ADMIN', 'USER')),
    status varchar(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'ACTIVE', 'INACTIVE')),
    department_id uuid REFERENCES departments(id) ON DELETE RESTRICT,
    approved_by uuid REFERENCES users(id) ON DELETE SET NULL,
    approved_at timestamptz,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_users_approval CHECK (
        (status = 'PENDING' AND approved_at IS NULL) OR status <> 'PENDING'
    )
);
CREATE UNIQUE INDEX ux_one_active_admin ON users ((system_role))
    WHERE system_role = 'ADMIN' AND status = 'ACTIVE';
CREATE INDEX ix_users_department ON users (department_id) WHERE department_id IS NOT NULL;

CREATE TABLE user_roles (
    user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    business_role_id uuid NOT NULL REFERENCES business_roles(id) ON DELETE RESTRICT,
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, business_role_id)
);
CREATE INDEX ix_user_roles_role ON user_roles (business_role_id, user_id);

CREATE TABLE academic_years (
    id uuid PRIMARY KEY,
    name varchar(20) NOT NULL UNIQUE,
    start_date date NOT NULL,
    is_active boolean NOT NULL DEFAULT true,
    created_by uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE school_classes (
    id uuid PRIMARY KEY,
    academic_year_id uuid NOT NULL REFERENCES academic_years(id) ON DELETE RESTRICT,
    name varchar(50) NOT NULL,
    normalized_name varchar(50) NOT NULL,
    grade smallint NOT NULL CHECK (grade BETWEEN 10 AND 12),
    homeroom_teacher_id uuid REFERENCES users(id) ON DELETE SET NULL,
    is_active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (academic_year_id, normalized_name)
);
CREATE UNIQUE INDEX ux_active_homeroom_teacher ON school_classes (homeroom_teacher_id)
    WHERE is_active AND homeroom_teacher_id IS NOT NULL;

CREATE TABLE school_weeks (
    id uuid PRIMARY KEY,
    academic_year_id uuid NOT NULL REFERENCES academic_years(id) ON DELETE RESTRICT,
    sequence_number smallint NOT NULL CHECK (sequence_number > 0),
    display_number smallint NOT NULL CHECK (display_number > 0),
    week_type varchar(20) NOT NULL CHECK (week_type IN ('ORIENTATION', 'STUDY')),
    start_date date NOT NULL,
    end_date date NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_school_weeks_dates CHECK (start_date <= end_date),
    UNIQUE (academic_year_id, sequence_number)
);
CREATE INDEX ix_school_weeks_dates ON school_weeks (start_date, end_date);
CREATE INDEX ix_school_weeks_display ON school_weeks (academic_year_id, week_type, display_number);

CREATE TABLE weekly_plans (
    id uuid PRIMARY KEY,
    school_week_id uuid NOT NULL UNIQUE REFERENCES school_weeks(id) ON DELETE RESTRICT,
    status varchar(20) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'PUBLISHED')),
    morning_duty_class_id uuid REFERENCES school_classes(id) ON DELETE SET NULL,
    afternoon_duty_class_id uuid REFERENCES school_classes(id) ON DELETE SET NULL,
    published_at timestamptz,
    published_by uuid REFERENCES users(id) ON DELETE SET NULL,
    version bigint NOT NULL DEFAULT 0,
    created_by uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_weekly_plans_publish CHECK (
        (status = 'DRAFT' AND published_at IS NULL AND published_by IS NULL)
        OR (status = 'PUBLISHED' AND published_at IS NOT NULL AND published_by IS NOT NULL)
    )
);
CREATE INDEX ix_weekly_plans_status ON weekly_plans (status, published_at);

CREATE TABLE plan_sections (
    id uuid PRIMARY KEY,
    weekly_plan_id uuid NOT NULL REFERENCES weekly_plans(id) ON DELETE CASCADE,
    section_type varchar(40) NOT NULL CHECK (section_type IN (
        'ACADEMIC_AFFAIRS', 'FACILITIES_OFFICE', 'YOUTH_UNION', 'HOMEROOM_TEACHERS', 'TEACHERS'
    )),
    content text,
    display_order smallint NOT NULL CHECK (display_order BETWEEN 1 AND 5),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (weekly_plan_id, section_type),
    UNIQUE (weekly_plan_id, display_order)
);

CREATE TABLE plan_section_targets (
    id uuid PRIMARY KEY,
    plan_section_id uuid NOT NULL REFERENCES plan_sections(id) ON DELETE CASCADE,
    target_type varchar(20) NOT NULL CHECK (target_type IN ('ALL', 'ROLE', 'DEPARTMENT', 'USER')),
    business_role_id uuid REFERENCES business_roles(id) ON DELETE RESTRICT,
    department_id uuid REFERENCES departments(id) ON DELETE RESTRICT,
    user_id uuid REFERENCES users(id) ON DELETE RESTRICT,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_plan_section_target_reference CHECK (
        (target_type = 'ALL' AND business_role_id IS NULL AND department_id IS NULL AND user_id IS NULL)
        OR (target_type = 'ROLE' AND business_role_id IS NOT NULL AND department_id IS NULL AND user_id IS NULL)
        OR (target_type = 'DEPARTMENT' AND business_role_id IS NULL AND department_id IS NOT NULL AND user_id IS NULL)
        OR (target_type = 'USER' AND business_role_id IS NULL AND department_id IS NULL AND user_id IS NOT NULL)
    )
);
CREATE UNIQUE INDEX ux_plan_target_all ON plan_section_targets (plan_section_id) WHERE target_type = 'ALL';
CREATE UNIQUE INDEX ux_plan_target_role ON plan_section_targets (plan_section_id, business_role_id) WHERE target_type = 'ROLE';
CREATE UNIQUE INDEX ux_plan_target_department ON plan_section_targets (plan_section_id, department_id) WHERE target_type = 'DEPARTMENT';
CREATE UNIQUE INDEX ux_plan_target_user ON plan_section_targets (plan_section_id, user_id) WHERE target_type = 'USER';
CREATE INDEX ix_plan_target_role_reverse ON plan_section_targets (business_role_id, plan_section_id) WHERE business_role_id IS NOT NULL;
CREATE INDEX ix_plan_target_department_reverse ON plan_section_targets (department_id, plan_section_id) WHERE department_id IS NOT NULL;
CREATE INDEX ix_plan_target_user_reverse ON plan_section_targets (user_id, plan_section_id) WHERE user_id IS NOT NULL;

CREATE TABLE day_sessions (
    id uuid PRIMARY KEY,
    weekly_plan_id uuid NOT NULL REFERENCES weekly_plans(id) ON DELETE CASCADE,
    session_date date NOT NULL,
    session varchar(20) NOT NULL CHECK (session IN ('MORNING', 'AFTERNOON')),
    base_content text,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (weekly_plan_id, session_date, session)
);
CREATE INDEX ix_day_sessions_plan_date ON day_sessions (weekly_plan_id, session_date);

CREATE TABLE events (
    id uuid PRIMARY KEY,
    weekly_plan_id uuid NOT NULL REFERENCES weekly_plans(id) ON DELETE CASCADE,
    content text NOT NULL CHECK (length(trim(content)) > 0),
    start_date date,
    end_date date,
    session varchar(20) CHECK (session IN ('MORNING', 'AFTERNOON')),
    start_time time,
    end_time time,
    location varchar(255),
    note text,
    version bigint NOT NULL DEFAULT 0,
    created_by uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_events_dates CHECK (end_date IS NULL OR (start_date IS NOT NULL AND start_date <= end_date)),
    CONSTRAINT ck_events_times CHECK (end_time IS NULL OR (start_time IS NOT NULL AND end_time >= start_time))
);
CREATE INDEX ix_events_plan_dates ON events (weekly_plan_id, start_date, end_date);

CREATE TABLE tasks (
    id uuid PRIMARY KEY,
    weekly_plan_id uuid NOT NULL REFERENCES weekly_plans(id) ON DELETE RESTRICT,
    assignee_user_id uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    title varchar(255) NOT NULL CHECK (length(trim(title)) > 0),
    description text,
    due_at timestamptz NOT NULL,
    status varchar(20) NOT NULL DEFAULT 'TODO' CHECK (status IN ('TODO', 'COMPLETED')),
    completed_at timestamptz,
    created_by uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_tasks_completion CHECK (
        (status = 'TODO' AND completed_at IS NULL) OR (status = 'COMPLETED' AND completed_at IS NOT NULL)
    )
);
CREATE INDEX ix_tasks_assignee_status_due ON tasks (assignee_user_id, status, due_at);
CREATE INDEX ix_tasks_plan_status ON tasks (weekly_plan_id, status);

CREATE TABLE reminders (
    id uuid PRIMARY KEY,
    event_id uuid NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    owner_user_id uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    source varchar(20) NOT NULL CHECK (source IN ('ADMIN', 'USER')),
    created_by uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    remind_at timestamptz NOT NULL,
    status varchar(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'PROCESSING', 'SENT', 'FAILED', 'CANCELLED')),
    attempt_count smallint NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    next_attempt_at timestamptz,
    processing_lease_until timestamptz,
    sent_at timestamptz,
    last_error_code varchar(100),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_reminders_due ON reminders ((COALESCE(next_attempt_at, remind_at))) WHERE status = 'PENDING';
CREATE INDEX ix_reminders_owner_event ON reminders (owner_user_id, event_id);

CREATE TABLE notifications (
    id uuid PRIMARY KEY,
    type varchar(50) NOT NULL,
    title varchar(255) NOT NULL,
    body text NOT NULL,
    entity_type varchar(50),
    entity_id uuid,
    deduplication_key varchar(200) NOT NULL UNIQUE,
    created_by uuid REFERENCES users(id) ON DELETE SET NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE notification_recipients (
    notification_id uuid NOT NULL REFERENCES notifications(id) ON DELETE CASCADE,
    user_id uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    sent_at timestamptz NOT NULL,
    read_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (notification_id, user_id)
);
CREATE INDEX ix_notification_recipients_inbox ON notification_recipients (user_id, read_at, created_at DESC) INCLUDE (notification_id);

CREATE TABLE conversations (
    id uuid PRIMARY KEY,
    created_by uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    subject varchar(255) NOT NULL CHECK (length(trim(subject)) > 0),
    category varchar(100),
    status varchar(20) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'CLOSED')),
    closed_at timestamptz,
    closed_by uuid REFERENCES users(id) ON DELETE SET NULL,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_conversations_closed CHECK (
        (status = 'OPEN' AND closed_at IS NULL AND closed_by IS NULL)
        OR (status = 'CLOSED' AND closed_at IS NOT NULL AND closed_by IS NOT NULL)
    )
);
CREATE INDEX ix_conversations_status ON conversations (status, updated_at DESC);
CREATE INDEX ix_conversations_creator ON conversations (created_by, updated_at DESC);

CREATE TABLE conversation_messages (
    id uuid PRIMARY KEY,
    conversation_id uuid NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    sender_id uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    content text NOT NULL CHECK (length(trim(content)) BETWEEN 1 AND 10000),
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_conversation_messages_cursor ON conversation_messages (conversation_id, created_at, id);

CREATE TABLE audit_logs (
    id uuid PRIMARY KEY,
    actor_user_id uuid REFERENCES users(id) ON DELETE SET NULL,
    actor_type varchar(20) NOT NULL CHECK (actor_type IN ('USER', 'SYSTEM')),
    entity_type varchar(80) NOT NULL,
    entity_id uuid NOT NULL,
    action varchar(100) NOT NULL,
    old_value jsonb,
    new_value jsonb,
    correlation_id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_audit_actor CHECK (
        (actor_type = 'SYSTEM' AND actor_user_id IS NULL) OR actor_type = 'USER'
    )
);
CREATE INDEX ix_audit_entity ON audit_logs (entity_type, entity_id, created_at DESC);
CREATE INDEX ix_audit_actor ON audit_logs (actor_user_id, created_at DESC) WHERE actor_user_id IS NOT NULL;
CREATE INDEX ix_audit_created_brin ON audit_logs USING brin (created_at);

CREATE TABLE auth_sessions (
    id_hash varchar(128) PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    csrf_secret_hash varchar(128) NOT NULL,
    expires_at timestamptz NOT NULL,
    revoked_at timestamptz,
    last_seen_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_auth_sessions_user ON auth_sessions (user_id, expires_at);
CREATE INDEX ix_auth_sessions_expiry ON auth_sessions (expires_at) WHERE revoked_at IS NULL;

CREATE TABLE password_reset_tokens (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash varchar(128) NOT NULL UNIQUE,
    expires_at timestamptz NOT NULL,
    used_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_password_reset_tokens_user ON password_reset_tokens (user_id, expires_at);

CREATE TABLE outbox_messages (
    id uuid PRIMARY KEY,
    event_type varchar(100) NOT NULL,
    aggregate_type varchar(80) NOT NULL,
    aggregate_id uuid NOT NULL,
    deduplication_key varchar(200) NOT NULL UNIQUE,
    payload jsonb NOT NULL,
    status varchar(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'PROCESSING', 'PROCESSED', 'FAILED')),
    attempt_count smallint NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    available_at timestamptz NOT NULL DEFAULT now(),
    locked_until timestamptz,
    processed_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_outbox_available ON outbox_messages (available_at) WHERE status = 'PENDING';

CREATE TABLE scheduler_job_runs (
    job_key varchar(150) PRIMARY KEY,
    started_at timestamptz NOT NULL,
    completed_at timestamptz,
    result varchar(40) CHECK (result IN ('RUNNING', 'SUCCEEDED', 'FAILED', 'SKIPPED'))
);

INSERT INTO business_roles (id, name, normalized_name, description, is_protected)
VALUES
    ('00000000-0000-0000-0000-000000000101', 'Ban giám hiệu', 'ban giam hieu', 'Vai trò nghiệp vụ được bảo vệ', true),
    ('00000000-0000-0000-0000-000000000102', 'Giáo viên chủ nhiệm', 'giao vien chu nhiem', 'Vai trò nghiệp vụ được bảo vệ', true),
    ('00000000-0000-0000-0000-000000000103', 'Giáo viên', 'giao vien', 'Vai trò nghiệp vụ được bảo vệ', true)
ON CONFLICT (normalized_name) DO NOTHING;
