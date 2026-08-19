# Phase 5 Report — Events & Publishing

Ngày hoàn thành: 2026-08-19  
Trạng thái: **PASS**  
Phase kế tiếp: **Phase 6 — Relevant To Me & Dashboards**

## Đã hoàn thành

- Event create/update/delete, optimistic locking, date/time validation và audit.
- Event nhiều ngày được chiếu vào mọi ngày inclusive; Event không chỉ định buổi hiển thị ở cả hai buổi.
- Validation trả `errors`, `warnings`, `valid`; warnings gồm lớp trực, section/ngày trống và Event thiếu ngày giờ.
- Publish idempotent với advisory lock, metadata `publishedAt/by`, AuditLog và outbox trong cùng transaction.
- User current plan và deep-link chỉ thấy `PUBLISHED`; DRAFT vẫn trả 404.
- Full-save và Event mutation trên published plan yêu cầu lựa chọn website/email rõ ràng.
- CORS cho phép `Idempotency-Key`; frontend editor/list/User view dùng REST thật.

## Verification

| Gate | Kết quả |
|---|---:|
| Frontend lint/typecheck/build | PASS — 23 routes |
| Backend `mvn verify` | PASS |
| Backend tests | 12 PASS, 0 failure/error |
| PostgreSQL integration | PASS trên PostgreSQL 18 Testcontainers |

CF-05/CF-06 kiểm tra Event invalid/multi-day, validation warnings, publish thiếu key, warnings chưa xác nhận, publish thành công/retry, metadata/outbox/audit, DRAFT 404, User published/current visibility và notification-choice guard.

## Technical debt

- Outbox mới dừng ở durable hook; fan-out Notification/email worker thuộc Phase 8.
- Local JDK 26 vẫn tạo cảnh báo ArchUnit class major 70; CI/baseline dùng Java 21.
