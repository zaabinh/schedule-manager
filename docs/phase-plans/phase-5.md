# Phase 5 Plan — Events & Publishing

## Scope

- Event CRUD với optimistic version và validation ngày/giờ.
- Projection Event nhiều ngày vào từng ngày/buổi của WeeklyPlan.
- Validation tách blocking errors và warnings.
- Publish DRAFT idempotent, metadata/audit/outbox atomic.
- User chỉ đọc published plan; current published projection.
- Sửa plan/Event đã publish với lựa chọn website/email bắt buộc và không gửi ngầm.

## Invariants

- Event chỉ bắt buộc content; end không đứng trước start.
- Publish yêu cầu `Idempotency-Key`, đúng version và xác nhận warnings.
- DRAFT tiếp tục trả 404 cho User.
- Published mutation phải có lựa chọn thông báo rõ ràng; cả hai false là hợp lệ.
