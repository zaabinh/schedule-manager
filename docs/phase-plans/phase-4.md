# Phase 4 Plan — WeeklyPlan Core

**Ngày:** 2026-08-19  
**Phụ thuộc:** Phase 0–3 PASS

## Phạm vi

1. Danh sách tuần và trạng thái kế hoạch theo AcademicYear.
2. Tạo WeeklyPlan DRAFT nguyên tử với đúng 5 PlanSection và 2 DaySession/ngày.
3. Copy kế hoạch theo ordinal ngày/buổi, chỉ gồm section/target/baseContent.
4. Full-save DRAFT: section, target, duty classes và baseContent với optimistic locking.
5. Lookup target/duty class cho editor; Admin security, CSRF, audit và DRAFT visibility.
6. Chuyển trang danh sách/editor Admin từ mock sang REST API thật.

## Quyết định

- Event CRUD, validation/publish và sửa published content thuộc Phase 5.
- `ALL` là target độc quyền trong một section; không kết hợp target khác.
- Duty class phải active và thuộc cùng AcademicYear với SchoolWeek.
- Target ROLE/DEPARTMENT/USER phải active tại thời điểm lưu.
- Copy yêu cầu `Idempotency-Key`; cùng actor/key/target trả lại cùng plan.
- Copy không mang Event, Task, Reminder, Notification hoặc duty class; ngày map theo ordinal và session.
- User đọc DRAFT nhận `404`; Admin đọc được DRAFT.

## Definition of Done

- BR-015..018, BR-021..022 và CF-03/CF-04 DRAFT PASS trên PostgreSQL thật.
- Full-save transaction/version/validation/security/audit có negative tests.
- Frontend lint/typecheck/build và backend full verify PASS.
- Phase 4 Report tồn tại, roadmap chuyển Phase 5 thành NEXT.
