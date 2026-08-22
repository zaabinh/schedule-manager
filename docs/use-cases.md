# Use Case Specification

Các use case dùng thuật ngữ và rule từ [business-rules.md](business-rules.md). API mapping ở [api-design.md](api-design.md).

## Quy ước

- Preconditions mặc định: actor đã đăng nhập và `ACTIVE`, trừ UC-01/02; request có correlation ID.
- Mutation thành công đều ghi AuditLog khi BR-041 yêu cầu.
- `A` = ADMIN, `U` = USER, `S` = SYSTEM.

## Danh mục

| ID | Tên | Actor | Requirement |
|---|---|---|---|
| UC-01 | Register Account | Guest | FR-AUTH-001 |
| UC-02 | Login/Logout/Reset Password | ADMIN, USER | FR-AUTH-002..004 |
| UC-03 | Approve and Manage User | ADMIN | FR-USER-001..004 |
| UC-04 | Manage Organization | ADMIN | FR-ORG-001..002 |
| UC-05 | Manage SchoolClass | ADMIN | FR-CLASS-001..002 |
| UC-06 | Create AcademicYear and Generate Weeks | ADMIN | FR-ACADEMIC-001..003 |
| UC-07 | Create WeeklyPlan | ADMIN | FR-PLAN-001 |
| UC-08 | Copy Previous Plan | ADMIN | FR-PLAN-002 |
| UC-09 | Edit WeeklyPlan | ADMIN | FR-PLAN-003..007 |
| UC-10 | Validate and Publish | ADMIN | FR-PLAN-008..009 |
| UC-11 | Update Published Plan | ADMIN | FR-PLAN-011 |
| UC-12 | View WeeklyPlan and Relevant To Me | USER | FR-PLAN-010..012 |
| UC-13 | Use Admin Dashboard | ADMIN | FR-PLAN-013 |
| UC-14 | Assign/Manage Task | ADMIN | FR-TASK-001,003,004 |
| UC-15 | View/Complete Task | USER | FR-TASK-002 |
| UC-16 | Use Notification Center | ADMIN, USER | FR-NOTIFY-001..002 |
| UC-17 | Create/Cancel Reminder | ADMIN, USER | FR-REMINDER-001..002 |
| UC-18 | Deliver Due Reminder | SYSTEM | FR-REMINDER-003 |
| UC-19 | Send Saturday Admin Reminder | SYSTEM | FR-REMINDER-004 |
| UC-20 | Start Conversation | USER | FR-CONV-001 |
| UC-21 | Reply/Close Conversation | ADMIN, USER | FR-CONV-002..003 |
| UC-22 | Review AuditLog | ADMIN | FR-AUDIT-002 |
| UC-23 | Export WeeklyPlan | ADMIN | FR-EXPORT-001 |
| UC-24 | Manage Task Attachments | ADMIN, USER | FR-TASK-FILE-001..004 |

## ADMIN use cases

### UC-03 — Approve and Manage User

- **Actor/Goal:** ADMIN cấu hình và kích hoạt hoặc vô hiệu tài khoản.
- **Preconditions:** Admin active; target User tồn tại.
- **Trigger:** Admin mở danh sách pending hoặc hồ sơ User.
- **Main flow:** (1) hệ thống trả hồ sơ pending; (2) Admin chọn Department, các BusinessRole và optional SchoolClass; (3) hệ thống validate BR-003/004/008; (4) cập nhật `ACTIVE`; (5) ghi audit; (6) gửi email kết quả.
- **Alternative:** Admin cập nhật cấu hình User active; hoặc chuyển `ACTIVE <-> INACTIVE`.
- **Exception:** Department/role/class inactive `422`; conflict GVCN/Admin `409`; stale update `409`.
- **Postconditions:** cấu hình nhất quán; session của User vừa inactive bị revoke.
- **Rules:** BR-002..009.

### UC-04 — Manage Organization

- **Actor/Goal:** ADMIN quản lý Department và BusinessRole.
- **Preconditions:** Admin active.
- **Trigger:** mở màn hình tổ chức.
- **Main flow:** liệt kê; tạo hoặc sửa tên/mô tả; validate unique; lưu; audit.
- **Alternative:** deactivate bản ghi không còn dùng mới.
- **Exception:** trùng tên `409`; cố xóa/vi phạm protected/reference `409`.
- **Postconditions:** dữ liệu lịch sử không mất.
- **Rules:** BR-003, BR-004, BR-006, BR-007.

### UC-05 — Manage SchoolClass

- **Actor/Goal:** ADMIN quản lý lớp và GVCN.
- **Preconditions:** AcademicYear tồn tại.
- **Trigger:** Admin tạo/sửa/deactivate lớp.
- **Main flow:** nhập tên/khối/năm; optional chọn GVCN; validate active và uniqueness; lưu; audit.
- **Alternative:** bỏ GVCN hoặc deactivate lớp.
- **Exception:** User đã chủ nhiệm lớp khác `409`; khối ngoài 10..12 `422`.
- **Postconditions:** assignment GVCN nhất quán.
- **Rules:** BR-006, BR-008.

### UC-06 — Create AcademicYear and Generate Weeks

- **Actor/Goal:** ADMIN khởi tạo lịch năm học.
- **Preconditions:** tên năm chưa tồn tại.
- **Trigger:** nhập tên và ngày bắt đầu.
- **Main flow:** tạo AcademicYear; sinh 39 SchoolWeek theo BR-012; hiển thị preview; Admin có thể sửa từng tuần; audit.
- **Alternative:** chỉ tạo năm rồi gọi generate sau; sửa tuần không tự dời tuần sau.
- **Exception:** duplicate `409`; date invalid `422`; generate lần hai `409`.
- **Postconditions:** 39 tuần có UUID và sequence duy nhất.
- **Rules:** BR-010..014.

### UC-07 — Create WeeklyPlan

- **Actor/Goal:** ADMIN tạo bản nháp tuần.
- **Preconditions:** SchoolWeek tồn tại, chưa có plan.
- **Trigger:** chọn “Tạo kế hoạch”.
- **Main flow:** tạo DRAFT, 5 PlanSection và 2 DaySession cho mỗi ngày; trả editor model.
- **Alternative:** chuyển sang UC-08 để copy tuần trước.
- **Exception:** plan đã có `409`; tuần invalid `422`.
- **Postconditions:** cấu trúc plan hợp lệ cơ bản.
- **Rules:** BR-014..018.

### UC-08 — Copy Previous Plan

- **Actor/Goal:** ADMIN tạo tuần mới từ nội dung nền tuần trước.
- **Preconditions:** source plan tồn tại; target week chưa có plan.
- **Trigger:** “Copy tuần trước”.
- **Main flow:** tải source; tạo target DRAFT; copy content/targets của 5 section và ánh xạ baseContent theo thứ tự ngày+session; trả summary.
- **Alternative:** source có số ngày khác: chỉ copy theo ordinal ngày tồn tại ở cả hai tuần.
- **Exception:** không có source `404`; target đã có `409`; reference target inactive vẫn copy để giữ nội dung nhưng trả warning.
- **Postconditions:** các entity bị cấm không xuất hiện ở target.
- **Rules:** BR-016, BR-018, BR-022.

### UC-09 — Edit WeeklyPlan

- **Actor/Goal:** ADMIN biên tập toàn bộ plan.
- **Preconditions:** plan tồn tại.
- **Trigger:** Save trong editor.
- **Main flow:** gửi version và các thay đổi; validate sections/targets/sessions/events/duty; transaction lưu; tăng version; audit diff.
- **Alternative:** CRUD Event riêng để autosave; plan published chuyển UC-11.
- **Exception:** validation `422`; stale version `409`; reference inactive `409/422`.
- **Postconditions:** plan không có trạng thái nửa chừng.
- **Rules:** BR-016..021.

### UC-10 — Validate and Publish

- **Actor/Goal:** ADMIN công bố plan.
- **Preconditions:** plan DRAFT.
- **Trigger:** Publish.
- **Main flow:** validate; nếu không warning thì xác nhận; transaction set PUBLISHED/published metadata, audit và outbox; sau commit worker tạo notification.
- **Alternative:** có warning: Admin chọn “Publish with warnings”; hoặc quay lại sửa.
- **Exception:** blocking errors `422`; stale version `409`; retry cùng idempotency key trả kết quả cũ.
- **Postconditions:** User thấy plan; recipients được tạo.
- **Rules:** BR-023..025.

### UC-11 — Update Published Plan

- **Actor/Goal:** ADMIN sửa plan đã công bố có kiểm soát thông báo.
- **Preconditions:** plan PUBLISHED.
- **Trigger:** Save.
- **Main flow:** validate/lưu/audit; UI hỏi notification website/email; request chứa hai boolean explicit; tạo outbox tương ứng.
- **Alternative:** cả hai false chỉ lưu và audit.
- **Exception:** thiếu lựa chọn explicit `422`; version conflict `409`.
- **Postconditions:** plan vẫn PUBLISHED; không tự spam.
- **Rules:** BR-026.

### UC-13 — Use Admin Dashboard

- **Actor/Goal:** ADMIN xử lý việc cần chú ý.
- **Preconditions:** Admin active.
- **Trigger:** mở dashboard.
- **Main flow:** lấy current plan status/actions, pending users, open conversations, task summary, next week publication, duty classes; điều hướng quick action.
- **Alternative:** chưa xác định current week thì hiển thị setup AcademicYear.
- **Exception:** partial read failure hiển thị retry và correlation ID.
- **Postconditions:** không thay đổi dữ liệu.
- **Rules:** BR-013, BR-028, BR-036.

### UC-14 — Assign/Manage Task

- **Actor/Goal:** ADMIN giao và theo dõi Task.
- **Preconditions:** WeeklyPlan và assignee active tồn tại.
- **Trigger:** Add/Update Task.
- **Main flow:** validate; tạo TODO hoặc cập nhật; audit; outbox notification; dashboard tính aggregates.
- **Alternative:** đổi assignee thông báo cả theo chính sách triển khai (recipient mới bắt buộc).
- **Exception:** inactive assignee `422`; stale update `409`.
- **Postconditions:** Task thuộc đúng plan/user.
- **Rules:** BR-028..030.

### UC-22 — Review AuditLog

- **Actor/Goal:** ADMIN truy vết thay đổi.
- **Preconditions:** Admin active.
- **Trigger:** mở audit, chọn filter actor/entity/action/date.
- **Main flow:** API trả page theo `createdAt DESC`; Admin mở redacted diff.
- **Alternative:** export audit không thuộc MVP.
- **Exception:** range/filter invalid `422`.
- **Postconditions:** audit không bị thay đổi.
- **Rules:** BR-040..041.

### UC-23 — Export WeeklyPlan

- **Actor/Goal:** ADMIN tải `.xlsx` đúng mẫu cơ bản.
- **Preconditions:** plan tồn tại.
- **Trigger:** Export Excel.
- **Main flow:** đọc snapshot; render tiêu đề/5 sections/bảng ngày; stream file với filename an toàn.
- **Alternative:** plan DRAFT vẫn được Admin export với watermark “DỰ THẢO”.
- **Exception:** plan missing `404`; render failure `500` có correlation ID.
- **Postconditions:** không thay đổi plan; audit EXPORT.
- **Rules:** BR-042.

## USER use cases

### UC-01 — Register Account

- **Actor/Goal:** Guest tạo yêu cầu tài khoản.
- **Preconditions:** chưa đăng nhập.
- **Trigger:** submit email, tên, mật khẩu.
- **Main flow:** normalize email; validate; hash password; tạo USER/PENDING; trả thông báo chờ duyệt.
- **Alternative:** email đã tồn tại trả thông báo chung phù hợp.
- **Exception:** payload invalid `422`; rate limit `429`.
- **Postconditions:** không có session đăng nhập.
- **Rules:** BR-001, BR-002.

### UC-02 — Login/Logout/Reset Password

- **Actor/Goal:** ADMIN/USER tạo hoặc kết thúc session; Guest lấy lại mật khẩu.
- **Preconditions:** login yêu cầu ACTIVE.
- **Trigger:** credentials/logout/request reset.
- **Main flow:** verify hash; rotate session ID; set secure cookie; logout revoke. Reset tạo token hash một lần và gửi email.
- **Alternative:** request reset luôn trả cùng response.
- **Exception:** credentials/status invalid `401`; throttled `429`; token invalid `422`.
- **Postconditions:** session state đúng và được audit security.
- **Rules:** BR-002, BR-009.

### UC-12 — View WeeklyPlan and Relevant To Me

- **Actor/Goal:** USER xem kế hoạch công bố và nội dung ưu tiên.
- **Preconditions:** User active.
- **Trigger:** mở dashboard/chọn tuần.
- **Main flow:** xác định current week; chỉ query PUBLISHED; match BR-027; trả Relevant, Today, full plan và notifications.
- **Alternative:** không có published plan hiển thị empty state; chuyển tuần trước/sau.
- **Exception:** DRAFT/missing đều `404` ở endpoint plan.
- **Postconditions:** read-only.
- **Rules:** BR-015, BR-020, BR-027.

### UC-15 — View/Complete Task

- **Actor/Goal:** USER quản lý Task của mình.
- **Preconditions:** task assignee là User.
- **Trigger:** mở Tasks/Complete.
- **Main flow:** list và derive overdue; complete TODO; lưu completedAt; audit.
- **Alternative:** complete lại trả representation hiện tại.
- **Exception:** task người khác `404`.
- **Postconditions:** status COMPLETED.
- **Rules:** BR-028, BR-029.

### UC-16 — Use Notification Center

- **Actor/Goal:** actor xem và đánh dấu thông báo.
- **Preconditions:** active.
- **Trigger:** mở chuông/mark read/all.
- **Main flow:** lấy unread count/page; mark recipient `readAt`; cập nhật count.
- **Alternative:** thao tác lặp idempotent.
- **Exception:** notification không thuộc actor `404`.
- **Postconditions:** tracking recipient cập nhật.
- **Rules:** BR-031, BR-032.

### UC-17 — Create/Cancel Reminder

- **Actor/Goal:** ADMIN/USER nhận email nhắc Event.
- **Preconditions:** Event tồn tại; User xem được published plan, Admin xem mọi plan.
- **Trigger:** chọn preset/custom.
- **Main flow:** tính/validate remindAt; tạo source theo actor; scheduler nhận bản ghi.
- **Alternative:** thiếu event datetime thì nhập absolute remindAt; owner hủy personal pending reminder.
- **Exception:** remindAt quá khứ `422`; User tác động ADMIN reminder `403`.
- **Postconditions:** reminder PENDING hoặc CANCELLED.
- **Rules:** BR-033, BR-034.

### UC-20 — Start Conversation

- **Actor/Goal:** USER mở trao đổi với Admin.
- **Preconditions:** User active.
- **Trigger:** submit subject/category/message.
- **Main flow:** tạo OPEN conversation và first message trong một transaction; outbox notify Admin.
- **Alternative:** category bỏ trống.
- **Exception:** content invalid `422`; throttled `429`.
- **Postconditions:** thread sẵn sàng polling.
- **Rules:** BR-037, BR-039.

### UC-21 — Reply/Close Conversation

- **Actor/Goal:** hai phía trao đổi; Admin đóng khi xử lý xong.
- **Preconditions:** actor là creator hoặc Admin; conversation OPEN để reply.
- **Trigger:** gửi message/Close.
- **Main flow:** verify ownership/status; append message; notify phía kia; Admin close và ghi closedAt.
- **Alternative:** polling theo cursor; close lặp idempotent.
- **Exception:** unauthorized `404`; closed reply `409`.
- **Postconditions:** message append-only hoặc thread CLOSED.
- **Rules:** BR-037..039.

### UC-24 — Manage Task Attachments

- **Actor/Goal:** Admin đính kèm tài liệu hướng dẫn; assignee xem và tải tài liệu của Task.
- **Preconditions:** Task tồn tại; actor active; upload/delete yêu cầu Admin, list/download yêu cầu Admin hoặc assignee.
- **Main flow:** Admin chọn tệp; frontend kiểm tra sớm; backend kiểm tra giới hạn, extension, MIME và signature; sinh attachment ID/storage key; lưu binary; transaction lưu metadata + audit. Assignee tải qua endpoint có authorization.
- **Alternative:** Một tệp upload lỗi thì Task và các tệp thành công được giữ; frontend cho thử lại riêng tệp lỗi. Admin có thể thêm/xóa tệp trên Task đã tồn tại.
- **Exception:** file/total/count/type invalid `422`; storage lỗi `503`; User khác `403`; missing `404`; thiếu CSRF `403`.
- **Postconditions:** add/remove có audit; xóa storage lỗi không báo thành công; DB insert lỗi kích hoạt best-effort cleanup.
- **Rules:** BR-043..047.

## SYSTEM / scheduled use cases

### UC-18 — Deliver Due Reminder

- **Actor/Goal:** SYSTEM gửi reminder đến hạn an toàn khi retry.
- **Preconditions:** worker leader active; reminder PENDING và due.
- **Trigger:** fixed-delay scheduler.
- **Main flow:** claim batch bằng row locking; commit PROCESSING; gửi email; transaction mark SENT; ghi metrics/audit.
- **Alternative:** transient error tăng attempt và schedule retry.
- **Exception:** permanent/max attempts -> FAILED và alert; cancelled item bị skip.
- **Postconditions:** mỗi reminder kết thúc SENT/FAILED/CANCELLED.
- **Rules:** BR-035.

### UC-19 — Send Saturday Admin Reminder

- **Actor/Goal:** SYSTEM nhắc Admin hoàn tất tuần kế tiếp.
- **Preconditions:** cron 08:00 hoặc 17:00 Thứ Bảy theo timezone trường.
- **Trigger:** scheduler slot.
- **Main flow:** xác định next SchoolWeek; kiểm tra plan; nếu chưa PUBLISHED tạo outbox email với unique job key.
- **Alternative:** đã published hoặc không có next week -> skip có log.
- **Exception:** provider lỗi dùng outbox retry; không chạy lại logic tạo bản ghi trùng.
- **Postconditions:** tối đa một email/slot/Admin.
- **Rules:** BR-036.
