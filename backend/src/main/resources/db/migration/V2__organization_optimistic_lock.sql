ALTER TABLE departments ADD COLUMN version bigint NOT NULL DEFAULT 0;
ALTER TABLE business_roles ADD COLUMN version bigint NOT NULL DEFAULT 0;
ALTER TABLE school_classes ADD COLUMN version bigint NOT NULL DEFAULT 0;

CREATE INDEX ix_departments_active_name ON departments (is_active, name);
CREATE INDEX ix_business_roles_active_name ON business_roles (is_active, name);
CREATE INDEX ix_school_classes_year_active_name ON school_classes (academic_year_id, is_active, name);
