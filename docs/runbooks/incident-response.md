# Incident Response Runbook

## Triage

1. Ghi thời điểm, correlation ID, release version và phạm vi User bị ảnh hưởng.
2. Kiểm tra readiness, PostgreSQL connections, disk, outbox/reminder backlog và SMTP.
3. Không log/copy cookie, password, reset token, pepper hoặc SMTP secret vào ticket.

## Common incidents

- `outbox FAILED`: sửa provider/config, giữ business record đã commit, tạo quy trình replay có kiểm soát sau khi xác minh dedup key.
- `reminder FAILED`: kiểm tra SMTP, account active và `last_error_code`; không đổi trực tiếp sang SENT.
- Database unavailable: ngăn writer/restart loop, xác minh volume; dùng restore chỉ sau khi database owner duyệt.
- Suspected credential leak: rotate secret, revoke sessions liên quan, kiểm tra AuditLog và lưu evidence read-only.
- Audit tamper attempt: database trigger sẽ từ chối; cảnh báo từ log DB phải được điều tra.

## Exit

Khôi phục service, smoke critical flows, xác nhận không còn backlog bất thường, ghi timeline/root cause/action item và cập nhật test/runbook trước release kế tiếp.
