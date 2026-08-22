ALTER TABLE users ADD COLUMN admin_slot smallint;

WITH ranked_admins AS (
    SELECT id, row_number() OVER (ORDER BY created_at, id)::smallint AS slot
    FROM users
    WHERE system_role = 'ADMIN' AND status = 'ACTIVE'
)
UPDATE users u
SET admin_slot = ranked.slot
FROM ranked_admins ranked
WHERE u.id = ranked.id;

DROP INDEX ux_one_active_admin;

ALTER TABLE users ADD CONSTRAINT ck_users_admin_slot CHECK (
    (system_role = 'ADMIN' AND status = 'ACTIVE' AND admin_slot BETWEEN 1 AND 2)
    OR
    ((system_role <> 'ADMIN' OR status <> 'ACTIVE') AND admin_slot IS NULL)
);

CREATE UNIQUE INDEX ux_active_admin_slot ON users (admin_slot)
    WHERE system_role = 'ADMIN' AND status = 'ACTIVE';

CREATE FUNCTION assign_active_admin_slot() RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    available_slot smallint;
BEGIN
    IF NEW.system_role = 'ADMIN' AND NEW.status = 'ACTIVE' THEN
        -- Serialize slot allocation so two concurrent inserts cannot both choose the same free slot.
        PERFORM pg_advisory_xact_lock(20260822);

        IF TG_OP = 'UPDATE'
                AND OLD.system_role = 'ADMIN'
                AND OLD.status = 'ACTIVE'
                AND OLD.admin_slot IS NOT NULL THEN
            NEW.admin_slot := OLD.admin_slot;
        ELSIF NEW.admin_slot IS NULL THEN
            SELECT candidate::smallint
            INTO available_slot
            FROM generate_series(1, 2) AS candidate
            WHERE NOT EXISTS (
                SELECT 1
                FROM users existing
                WHERE existing.system_role = 'ADMIN'
                  AND existing.status = 'ACTIVE'
                  AND existing.admin_slot = candidate
                  AND existing.id <> NEW.id
            )
            ORDER BY candidate
            LIMIT 1;

            IF available_slot IS NULL THEN
                RAISE EXCEPTION 'At most two active Admin accounts are allowed'
                    USING ERRCODE = '23514', CONSTRAINT = 'ck_users_max_two_active_admins';
            END IF;
            NEW.admin_slot := available_slot;
        END IF;
    ELSE
        NEW.admin_slot := NULL;
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_assign_active_admin_slot
BEFORE INSERT OR UPDATE OF system_role, status, admin_slot ON users
FOR EACH ROW EXECUTE FUNCTION assign_active_admin_slot();
