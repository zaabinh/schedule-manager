# Phase 3 Plan — Academic Calendar

**Ngày:** 2026-08-19  
**Phụ thuộc:** Phase 0–2 PASS

## Phạm vi

1. CRUD cần thiết cho AcademicYear: list, create, update trạng thái/thông tin với optimistic locking.
2. Sinh đúng 39 SchoolWeek trong một transaction: 2 `ORIENTATION`, 37 `STUDY`.
3. List và chỉnh độc lập `displayNumber`, `weekType`, `startDate`, `endDate` của từng tuần.
4. Tích hợp màn hình Admin quản lý năm học/tuần và cập nhật lookup năm học cho SchoolClass.
5. Session/CSRF/Admin enforcement, audit và integration/E2E trên PostgreSQL thật.

## Quyết định

- Tuần mặc định dài 7 ngày liên tiếp từ `AcademicYear.startDate`.
- Overlap giữa các tuần là warning (`WEEK_OVERLAP`), không chặn lưu.
- `sequenceNumber` bất biến; `displayNumber` có thể chỉnh và không dùng làm identifier.
- Tối đa một AcademicYear active; có thể tạo năm tương lai ở trạng thái inactive.
- Không cho deactivate AcademicYear còn SchoolClass active (`ACADEMIC_YEAR_IN_USE`).
- Generator chỉ nhận `count=39`, chỉ chạy khi năm chưa có tuần và thực hiện nguyên tử.

## Definition of Done

- Contract/schema/docs đồng bộ, Flyway nâng schema sạch.
- CF-02 và các negative path về duplicate, version, CSRF, role, generator và date validation PASS.
- Frontend lint/typecheck/build và backend full verify PASS.
- Phase 3 Report tồn tại và roadmap chuyển Phase 4 thành NEXT.
