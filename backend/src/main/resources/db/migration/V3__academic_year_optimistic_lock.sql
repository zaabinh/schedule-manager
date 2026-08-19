ALTER TABLE academic_years ADD COLUMN version bigint NOT NULL DEFAULT 0;

CREATE INDEX ix_academic_years_active_start
    ON academic_years (is_active, start_date DESC);
