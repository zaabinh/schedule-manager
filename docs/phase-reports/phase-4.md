# Phase 4 Report — WeeklyPlan DRAFT và Copy

Ngày hoàn thành: 2026-08-19  
Trạng thái: **PASS**  
Phase kế tiếp: **Phase 5 — Event, validation và publish**

## Phạm vi đã hoàn thành

- REST API cho danh sách plan theo năm học, đọc plan theo tuần, tạo DRAFT, copy tuần trước, lấy options và full-save editor.
- Tạo aggregate nguyên tử với đúng 5 PlanSection và 2 DaySession cho mỗi ngày trong khoảng tuần.
- Lưu section target, baseContent, nội dung sáng/chiều và lớp trực; kiểm tra role/department/user/class active và lớp thuộc đúng năm học.
- Optimistic locking bằng `version`; mutation ghi AuditLog trong cùng transaction.
- DRAFT bị che với User bằng 404; endpoint mutation chỉ dành cho Admin và giữ CSRF/session policy.
- Frontend danh sách và editor quản trị chuyển từ mock sang REST.

## Copy và idempotency

- Copy theo ordinal ngày/buổi, chỉ gồm section/target/baseContent.
- Không copy Event, Task, Reminder, Notification hoặc lớp trực.
- `Idempotency-Key` là bắt buộc; retry cùng actor/key/target trả lại cùng plan và warnings.
- Flyway V4 thêm `api_idempotency_keys`; advisory lock tuần tự hóa các request cùng khóa.

## Verification

| Gate | Kết quả |
|---|---:|
| Frontend lint | PASS |
| TypeScript typecheck | PASS |
| Frontend production build | PASS — 23 routes |
| Backend `mvn verify` | PASS |
| Unit + architecture tests | 5 PASS |
| Integration tests | 6 PASS |
| Tổng backend tests | 11 PASS, 0 failure/error |

Integration coverage gồm create/duplicate/options/full-save, optimistic conflict, invalid structure, copy/idempotency/warnings, không sao chép thực thể bị cấm, DRAFT 404, User mutation 403, published visibility, audit và clean migration V1→V4 trên PostgreSQL Testcontainers.

## Quyết định và technical debt

- Event CRUD, validate/publish, notification và published editor thuộc Phase 5; không mở rộng ngoài scope Phase 4.
- `getCurrent` cho màn hình User tạm giữ mock cho tới khi Phase 5 hoàn thiện published projection.
- Browser Playwright và password reset tiếp tục là backlog đã biết.
- Local JDK 26 làm ArchUnit cảnh báo parser class major 70 nhưng rule vẫn PASS; CI và baseline dự án dùng Java 21.

## Kết luận

Phase 4 đạt exit gate, không còn blocker để bắt đầu Phase 5.
