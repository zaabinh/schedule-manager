# Phase 11 Report — Audit & Excel Export

Ngày hoàn thành: 2026-08-19  
Trạng thái: **PASS**  
Phase kế tiếp: **Phase 12 — Testing & Hardening**

## Đã hoàn thành

- API AuditLog Admin-only có filter actor/entity/action/time/size, detail và redaction các key password/token/secret/pepper/cookie.
- Flyway V5 gắn trigger database, AuditLog là append-only đối với UPDATE/DELETE.
- Trang Audit chuyển từ mock sang REST.
- Excel exporter dùng Apache POI, font Unicode, A4 landscape fit one page, tiêu đề, DRAFT watermark, 5 section, lớp trực và bảng ngày–sáng–chiều gồm Event nhiều ngày.
- Nội dung bắt đầu bằng `=`, `+`, `-`, `@` được prefix apostrophe để ngăn formula injection.
- Admin Weekly Plans có nút tải `.xlsx`; User bị 403 ở audit/export.

## Verification

| Gate | Kết quả |
|---|---|
| Frontend lint/typecheck/build | PASS — 23 routes |
| Backend clean focused verify | PASS — 13 tests tổng cộng |
| CF-13 Excel | PASS |
| CF-14 Audit | PASS |

## Technical debt chuyển Phase 12

- Fixture export dữ liệu cực dài và đo memory/time với dataset giới hạn thực tế.
- UAT mẫu in chính thức với Hiệu trưởng; VBA/PDF vẫn là extension ngoài MVP.
