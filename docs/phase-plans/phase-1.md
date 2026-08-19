# Phase 1 Plan – Authentication, Session and User Approval

Ngày đối chiếu: 2026-08-19

## Baseline đã xác nhận

- Phase 0 PASS; Flyway V1, backend skeleton, CI và clean PostgreSQL migration đang hoạt động.
- Frontend login/register/admin users hiện chỉ thao tác giả lập.
- Database đã có `users`, `user_roles`, `auth_sessions`, `departments`, `business_roles`, `school_classes` và `audit_logs`.
- Chưa có entity/repository/service/controller/session filter/rate limiter hoặc REST adapter.

## Scope

1. Register tạo `USER/PENDING`, normalize email, hash password, không tạo session.
2. Login chỉ cho `ACTIVE`, lỗi credential/status dùng chung, tạo opaque session và CSRF token.
3. `GET /auth/me`, rotate CSRF và logout/revoke session.
4. Admin liệt kê User, xem lookup active và approve với Department + ít nhất một BusinessRole.
5. Admin đổi ACTIVE/INACTIVE; inactive revoke toàn bộ session.
6. Authorization theo SystemRole, không theo BusinessRole; CORS/origin, CSRF và rate baseline.
7. Frontend login/register/admin users dùng REST adapter cho các flow trên.
8. Unit, integration/security và critical onboarding API E2E trên PostgreSQL thật.

## Assumptions có kiểm soát

- Password: ít nhất 8 ký tự, bắt buộc có chữ thường, chữ hoa và ký tự đặc biệt không phải khoảng trắng; tối đa 72 byte UTF-8 và chặn danh sách mật khẩu phổ biến tối thiểu. Policy được cập nhật theo quyết định ngày 2026-08-19.
- Hash dùng bcrypt cost 12 theo fallback đã được security design cho phép; Argon2id vẫn là target sau benchmark production.
- Session idle 8 giờ, absolute 24 giờ; cookie local `session` không Secure, production phải cấu hình `__Host-session` + Secure.
- Rate limit auth là in-memory 5 request/phút theo IP + normalized-email hash; cần distributed store trước horizontal scaling.
- Department/BusinessRole chỉ có lookup read-only trong Phase 1. CRUD đầy đủ vẫn thuộc Phase 2.
- Bootstrap Admin chỉ qua biến môi trường một lần và tắt mặc định; không seed credential vào migration.
- Password reset (SHOULD) deferred: không nằm trong exit signal Phase 1 đã thống nhất.

## Definition of Done

- Critical flow Register → PENDING → Admin Approve → ACTIVE → Login → Me → Logout pass trên PostgreSQL sạch.
- Pending/inactive không login; USER không gọi endpoint Admin; CSRF thiếu/sai bị chặn; BusinessRole không cấp ADMIN.
- Password hash/session/CSRF raw không lưu DB và không xuất API.
- Frontend lint/typecheck/build pass; backend verify pass; Phase Report được xuất.
