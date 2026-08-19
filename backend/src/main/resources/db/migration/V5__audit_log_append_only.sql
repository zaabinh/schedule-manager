CREATE OR REPLACE FUNCTION reject_audit_log_mutation()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'audit_logs is append-only' USING ERRCODE = '55000';
END;
$$;

CREATE TRIGGER audit_logs_reject_update_delete
BEFORE UPDATE OR DELETE ON audit_logs
FOR EACH ROW EXECUTE FUNCTION reject_audit_log_mutation();
