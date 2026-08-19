# Phase 3 Report — Academic Calendar

**Ngày:** 2026-08-19  
**Trạng thái:** PASS  
**Phase kế tiếp:** Phase 4 — WeeklyPlan Core

## Phạm vi đã hoàn thành

- List/create/update AcademicYear với optimistic locking và tối đa một năm active.
- Tạo năm tương lai ở trạng thái inactive; chặn deactivate năm còn SchoolClass active.
- Generator nguyên tử đúng 39 SchoolWeek: 2 `ORIENTATION`, 37 `STUDY`.
- Ngày mặc định là các khoảng 7 ngày liên tiếp từ `AcademicYear.startDate`.
- Chỉnh độc lập loại, số hiển thị và khoảng ngày; `sequenceNumber` bất biến.
- Overlap được lưu và trả warning `WEEK_OVERLAP` cho API/UI.
- GET dành cho mọi tài khoản authenticated; mutation chỉ dành cho Admin, có CSRF và AuditLog.
- Màn hình `/admin/academic-years` quản lý năm, sinh tuần, chỉnh tuần và hiển thị cảnh báo.
- Lookup năm học của SchoolClass tự dùng danh sách AcademicYear active từ backend.

## Database và concurrency

Flyway `V3__academic_year_optimistic_lock.sql` thêm `academic_years.version` và index active/start date. Create/activate dùng PostgreSQL transaction advisory lock để tránh race tạo nhiều năm active; SchoolWeek đã có unique `(academic_year_id, sequence_number)` và version từ baseline.

## Kết quả kiểm thử

| Gate | Kết quả |
|---|---|
| `npm run lint` | PASS |
| `npm run typecheck` | PASS |
| `npm run build` | PASS — 23 routes |
| `mvn verify` | PASS — exit code 0 |
| Unit + architecture | 5 tests, 0 failure |
| Integration/E2E | 5 tests, 0 failure |
| Tổng | 10 tests, 0 failure/error |

CF-02 kiểm tra mapping 2+37, 39 UUID/sequence, ngày mặc định, duplicate generation/name, invalid date/name, version conflict, one-active-year, class-year guard, overlap warning, AuditLog, CSRF, unauthenticated `401`, USER mutation `403` và clean migration V1→V3 trên PostgreSQL 18.

## Quyết định và tồn đọng

- Overlap là warning, không phải blocking error.
- Đổi `AcademicYear.startDate` sau khi đã sinh tuần không tự dịch tuần; từng tuần là dữ liệu độc lập.
- Browser-level E2E và password reset tiếp tục là backlog đã xác định, không chặn DoD Phase 3.
- Máy local chạy JDK 26 nên ArchUnit cảnh báo parser với class JDK 26 nhưng rule vẫn PASS; baseline và CI dùng Java 21.
- WeeklyPlan hiện còn mock và là phạm vi chính của Phase 4.

## Kết luận

Phase 3 đáp ứng Definition of Done và không có blocker chuyển Phase 4.
