# Phase 2 Report — Organization & SchoolClass

**Ngày:** 2026-08-19  
**Trạng thái:** PASS  
**Phase kế tiếp:** Phase 3 — AcademicYear & SchoolWeek

## Phạm vi đã hoàn thành

- Department CRUD với chuẩn hóa tên, chống trùng, pagination, soft-deactivate và optimistic locking.
- BusinessRole CRUD; role seed được bảo vệ không cho sửa nội dung hoặc trạng thái (`ROLE_PROTECTED 409`).
- SchoolClass CRUD theo AcademicYear, khối 10–12, phân công GVCN và soft-deactivate.
- Ràng buộc một giáo viên chỉ làm GVCN cho tối đa một lớp active; chỉ User ACTIVE mới được phân công.
- Endpoint lookup AcademicYear và giáo viên còn khả dụng cho form quản trị lớp.
- Mọi mutation yêu cầu Admin/session/CSRF và ghi AuditLog cùng transaction.
- Frontend phòng ban, vai trò và lớp/GVCN đã bỏ mock, dùng REST API thật và xử lý lỗi/version conflict.

## Database

Migration `V2__organization_optimistic_lock.sql` bổ sung `version` cho `departments`, `business_roles`, `school_classes` và index phục vụ danh sách active/name. Clean migration từ V1 đến V2 đã được kiểm chứng trên PostgreSQL 18 Testcontainers.

## Kết quả kiểm thử

| Gate | Kết quả |
|---|---|
| `npm run lint` | PASS |
| `npm run typecheck` | PASS |
| `npm run build` | PASS — 22 routes |
| `mvn verify` | PASS — exit code 0 |
| Unit + architecture | 5 tests, 0 failure |
| Integration/E2E | 4 tests, 0 failure |

Integration suite kiểm tra database migration sạch, hồi quy onboarding Phase 1, CRUD Phase 2, duplicate normalized name, optimistic conflict, protected role, grade invalid, GVCN conflict/reuse, AuditLog, CSRF, unauthenticated `401` và USER `403`.

## Quyết định và tồn đọng

- Protected BusinessRole bất biến trong MVP; thay đổi chính sách cần ADR/migration riêng.
- Phase 2 chỉ đọc AcademicYear hiện có; CRUD AcademicYear và generator 39 SchoolWeek thuộc Phase 3.
- Browser-level E2E và password reset tiếp tục là backlog đã xác định, không chặn DoD Phase 2.
- Máy local chạy JDK 26 nên ArchUnit cảnh báo parser với class JDK 26 nhưng rule vẫn PASS; baseline và CI của dự án dùng Java 21.

## Kết luận

Phase 2 đáp ứng Definition of Done và không có blocker chuyển Phase 3.
