# Đặc tả yêu cầu phần mềm (SRS)

Phiên bản: 1.0 — Phạm vi MVP  
Hệ thống: Quản lý kế hoạch tuần trường THPT

## Mục lục

1. [Mục đích và phạm vi](#1-mục-đích-và-phạm-vi)
2. [Tác nhân và thuật ngữ](#2-tác-nhân-và-thuật-ngữ)
3. [Yêu cầu chức năng](#3-yêu-cầu-chức-năng)
4. [Yêu cầu phi chức năng](#4-yêu-cầu-phi-chức-năng)
5. [Phạm vi MVP](#5-phạm-vi-mvp)
6. [Giả định và câu hỏi mở](#6-giả-định-và-câu-hỏi-mở)
7. [Tiêu chí chấp nhận cấp hệ thống](#7-tiêu-chí-chấp-nhận-cấp-hệ-thống)

## 1. Mục đích và phạm vi

Tài liệu này là nguồn yêu cầu có định danh cho website thay thế quy trình lập và phát hành kế hoạch tuần bằng Word/Excel. Hệ thống quản lý tài khoản, cơ cấu trường, năm/tuần học, `WeeklyPlan`, phân công, lịch theo ngày, `Event`, `Task`, thông báo, nhắc việc, trao đổi, nhật ký và xuất Excel.

Mục tiêu sản phẩm:

- Admin lập, kiểm tra, công bố và sửa kế hoạch tuần trong một quy trình có kiểm soát.
- User chỉ xem kế hoạch đã công bố và thấy trước nội dung “Dành cho bạn”.
- Các tác vụ có hạn, thông báo và reminder được theo dõi, không phụ thuộc thao tác thủ công.
- Thay đổi nghiệp vụ quan trọng có thể truy vết.

## 2. Tác nhân và thuật ngữ

| Tác nhân | Mô tả |
|---|---|
| `ADMIN` | Hiệu trưởng; system role duy nhất được quản trị và biên tập/công bố kế hoạch. |
| `USER` | Giáo viên/cán bộ; xem kế hoạch công bố, xử lý task/reminder/conversation. |
| `SYSTEM` | Scheduler và worker gửi email/notification. |

`SystemRole` (`ADMIN`, `USER`) dùng để phân quyền hệ thống. `BusinessRole` mô tả vai trò nghiệp vụ và chỉ dùng để phân loại/target. Mỗi `User` thuộc đúng một `Department`, có 0..n `BusinessRole`, và có thể chủ nhiệm tối đa một `SchoolClass` đang active.

Mức ưu tiên: `MUST` bắt buộc trong MVP, `SHOULD` cần có nếu không có trở ngại đã phê duyệt, `COULD` là mở rộng.

## 3. Yêu cầu chức năng

### 3.1 Xác thực và phân quyền

| ID | Yêu cầu | Actor | Ưu tiên | Tiêu chí chấp nhận |
|---|---|---|---|---|
| FR-AUTH-001 | Đăng ký bằng email hợp lệ và mật khẩu. | Guest | MUST | Tạo `User` role `USER`, status `PENDING`; email trùng bị từ chối. |
| FR-AUTH-002 | Đăng nhập chỉ khi tài khoản `ACTIVE`. | ADMIN, USER | MUST | Sai thông tin trả lỗi chung; `PENDING`/`INACTIVE` không tạo session. |
| FR-AUTH-003 | Đăng xuất và vô hiệu session hiện tại. | ADMIN, USER | MUST | Cookie/session không dùng lại được sau logout. |
| FR-AUTH-004 | Khôi phục mật khẩu qua token email một lần, có hạn. | Guest | SHOULD | Không tiết lộ email có tồn tại; token hết hạn/đã dùng bị từ chối. |
| FR-AUTHZ-001 | Backend bắt buộc kiểm tra `SystemRole`; UI không phải biện pháp bảo mật. | SYSTEM | MUST | USER gọi endpoint Admin nhận `403`. |
| FR-AUTHZ-002 | `BusinessRole` không cấp quyền Admin. | SYSTEM | MUST | User có role “Ban giám hiệu” vẫn không gọi được API Admin. |

### 3.2 User, tổ chức và lớp

| ID | Yêu cầu | Actor | Ưu tiên | Tiêu chí chấp nhận |
|---|---|---|---|---|
| FR-USER-001 | Liệt kê tài khoản chờ duyệt. | ADMIN | MUST | Có phân trang, không lộ password hash. |
| FR-USER-002 | Duyệt User sau khi gán một Department, BusinessRole và lớp chủ nhiệm nếu có. | ADMIN | MUST | Thiếu Department bị chặn; trạng thái thành `ACTIVE`. |
| FR-USER-003 | Chuyển tài khoản giữa `ACTIVE` và `INACTIVE`. | ADMIN | MUST | User inactive không đăng nhập mới; dữ liệu nghiệp vụ còn nguyên. |
| FR-USER-004 | Quản lý hồ sơ và cấu hình nghiệp vụ của User. | ADMIN | MUST | Đảm bảo đúng một Department và không trùng BusinessRole. |
| FR-ORG-001 | Tạo, sửa, liệt kê và deactivate `Department`. | ADMIN | MUST | Không xóa vật lý Department đang được tham chiếu. |
| FR-ORG-002 | Tạo, sửa, liệt kê và deactivate `BusinessRole`. | ADMIN | MUST | Role mặc định được bảo vệ khỏi xóa; lịch sử liên kết được giữ. |
| FR-CLASS-001 | Tạo, sửa, liệt kê và deactivate `SchoolClass`. | ADMIN | MUST | Tên lớp duy nhất trong phạm vi năm học theo ASSUMPTION-03. |
| FR-CLASS-002 | Gán/bỏ GVCN cho lớp. | ADMIN | MUST | Một User không chủ nhiệm đồng thời hai lớp active. |

### 3.3 Năm học và tuần học

| ID | Yêu cầu | Actor | Ưu tiên | Tiêu chí chấp nhận |
|---|---|---|---|---|
| FR-ACADEMIC-001 | Tạo `AcademicYear` với tên và ngày bắt đầu. | ADMIN | MUST | Tên năm học duy nhất; ngày hợp lệ. |
| FR-ACADEMIC-002 | Sinh mặc định 39 `SchoolWeek`: 2 `ORIENTATION`, 37 `STUDY`. | ADMIN | MUST | `sequenceNumber` 1..39; `displayNumber` 1..2 cho orientation và 1..37 cho study. |
| FR-ACADEMIC-003 | Cho sửa độc lập `startDate`, `endDate`, `displayNumber`, `weekType`. | ADMIN | MUST | `startDate <= endDate`; không suy ra ID từ số hiển thị. |
| FR-ACADEMIC-004 | Tùy chọn tự dời các tuần sau khi sửa ngày. | ADMIN | COULD | Được xem là extension point, không thuộc MVP. |

### 3.4 WeeklyPlan, sections, sessions và Event

| ID | Yêu cầu | Actor | Ưu tiên | Tiêu chí chấp nhận |
|---|---|---|---|---|
| FR-PLAN-001 | Tạo tối đa một `WeeklyPlan` cho mỗi `SchoolWeek`, trạng thái đầu `DRAFT`. | ADMIN | MUST | Tạo đồng thời đúng 5 `PlanSection` và các `DaySession` trong khoảng tuần. |
| FR-PLAN-002 | Copy kế hoạch tuần trước. | ADMIN | MUST | Copy 5 sections và `baseContent`; không copy Event, Reminder, Notification, duty class. |
| FR-PLAN-003 | Sửa DRAFT gồm sections, targets, base content, Event và duty classes. | ADMIN | MUST | Lưu atomically hoặc trả lỗi validation. |
| FR-PLAN-004 | Mỗi section hỗ trợ nhiều target `ALL`, `ROLE`, `DEPARTMENT`, `USER`. | ADMIN | MUST | Target tham chiếu phải active/tồn tại; target trùng bị loại. |
| FR-PLAN-005 | Mỗi ngày có `MORNING` và `AFTERNOON`, mỗi buổi có `baseContent` tùy chọn. | ADMIN | MUST | Unique theo plan/date/session. |
| FR-PLAN-006 | CRUD `Event`, chỉ `content` bắt buộc; thời gian/địa điểm/note tùy chọn. | ADMIN | MUST | Nếu có hai đầu ngày/giờ thì đầu <= cuối; Event nhiều ngày hiển thị trên mọi ngày trong khoảng. |
| FR-PLAN-007 | Chọn tối đa một lớp trực sáng và một lớp trực chiều. | ADMIN | MUST | Chỉ lớp active; cảnh báo nếu bỏ trống khi publish. |
| FR-PLAN-008 | Validate trước publish với blocking errors và warnings. | ADMIN | MUST | Error chặn; warning yêu cầu xác nhận “publish with warnings”. |
| FR-PLAN-009 | Publish chuyển trạng thái thành `PUBLISHED` và phát notification cho User. | ADMIN | MUST | `publishedAt/by` được lưu; User đọc được sau commit. |
| FR-PLAN-010 | User chỉ xem `PUBLISHED`, gồm điều hướng tuần và chế độ plan/calendar. | USER | MUST | DRAFT luôn trả `404` cho USER để tránh lộ tồn tại. |
| FR-PLAN-011 | Admin sửa plan đã publish và chủ động chọn Website notification, Email, cả hai hoặc không. | ADMIN | MUST | Không tự gửi nếu cả hai false; thay đổi vẫn có AuditLog. |
| FR-PLAN-012 | Dashboard User ưu tiên Current Week, Relevant To Me, Today, Weekly Plan, Notifications. | USER | MUST | Relevant khớp role, department, direct target/task và homeroom duty. |
| FR-PLAN-013 | Dashboard Admin hiển thị hành động và mục cần chú ý. | ADMIN | MUST | Có trạng thái plan, pending users, open conversations, task chưa xong và next week unpublished. |

### 3.5 Task

| ID | Yêu cầu | Actor | Ưu tiên | Tiêu chí chấp nhận |
|---|---|---|---|---|
| FR-TASK-001 | Giao Task thuộc WeeklyPlan cho một User. | ADMIN | MUST | Assignee active; status `TODO`; tạo notification. |
| FR-TASK-002 | User xem task của chính mình và đánh dấu hoàn thành. | USER | MUST | Hoàn thành lưu `COMPLETED`, `completedAt`; không sửa task người khác. |
| FR-TASK-003 | Admin xem thống kê tổng/hoàn thành/chưa hoàn thành/quá hạn. | ADMIN | MUST | `OVERDUE` tính tại thời điểm truy vấn, không persisted. |
| FR-TASK-004 | Admin cập nhật task và phát notification khi thay đổi có ý nghĩa. | ADMIN | MUST | Assignee nhận notification; thay đổi được audit. |

### 3.6 Notification và email

| ID | Yêu cầu | Actor | Ưu tiên | Tiêu chí chấp nhận |
|---|---|---|---|---|
| FR-NOTIFY-001 | Notification Center trả unread count và danh sách có phân trang. | ADMIN, USER | MUST | Chỉ trả recipient hiện tại. |
| FR-NOTIFY-002 | Mark one/all as read. | ADMIN, USER | MUST | Lưu `readAt`; thao tác idempotent. |
| FR-NOTIFY-003 | Tách notification khỏi recipient để thống kê sent/read/unread. | SYSTEM | MUST | Một notification có nhiều recipient và trạng thái giao riêng. |
| FR-NOTIFY-004 | Tạo notification khi publish/update được chọn, task mới/thay đổi, lớp chủ nhiệm trực, message mới. | SYSTEM | MUST | Chống tạo trùng khi retry bằng idempotency key. |
| FR-NOTIFY-005 | Email dùng outbox/worker và retry có giới hạn. | SYSTEM | SHOULD | Lỗi provider không rollback nghiệp vụ đã commit. |

### 3.7 Reminder

| ID | Yêu cầu | Actor | Ưu tiên | Tiêu chí chấp nhận |
|---|---|---|---|---|
| FR-REMINDER-001 | Tạo reminder Event theo 15/30 phút, 1 giờ, 1 ngày hoặc custom. | ADMIN, USER | MUST | Có Event datetime thì tính tương đối; thiếu thì bắt buộc `remindAt`. |
| FR-REMINDER-002 | Reminder có source `ADMIN` hoặc `USER` và độc lập. | SYSTEM | MUST | User không xóa reminder ADMIN; personal reminder không overwrite reminder khác. |
| FR-REMINDER-003 | Scheduler gửi email đến hạn đúng một lần về mặt nghiệp vụ. | SYSTEM | MUST | Claim atomically; retry không gửi duplicate trong điều kiện bình thường. |
| FR-REMINDER-004 | Thứ Bảy 08:00 và 17:00 nhắc Admin nếu kế hoạch tuần kế tiếp chưa publish. | SYSTEM | MUST | Đã publish thì không gửi; timezone trường được áp dụng. |

### 3.8 Conversation

| ID | Yêu cầu | Actor | Ưu tiên | Tiêu chí chấp nhận |
|---|---|---|---|---|
| FR-CONV-001 | User mở Conversation `OPEN` với subject, category tùy chọn và message đầu. | USER | MUST | Tạo thread và message atomically; Admin nhận notification. |
| FR-CONV-002 | Chủ thread và Admin trao đổi nhiều lượt bằng polling/refetch. | ADMIN, USER | MUST | User khác không đọc/ghi được thread. |
| FR-CONV-003 | Admin đóng Conversation. | ADMIN | MUST | Status `CLOSED`, lưu `closedAt`; không nhận message mới. |

### 3.9 Audit và Excel

| ID | Yêu cầu | Actor | Ưu tiên | Tiêu chí chấp nhận |
|---|---|---|---|---|
| FR-AUDIT-001 | Ghi actor, entity, action, old/new value, time cho thao tác quản trị/nghiệp vụ quan trọng. | SYSTEM | MUST | Audit append-only; thất bại ghi audit làm rollback transaction tương ứng. |
| FR-AUDIT-002 | Admin xem AuditLog có filter/phân trang; User không được xem. | ADMIN | MUST | Payload nhạy cảm được redact. |
| FR-EXPORT-001 | Xuất WeeklyPlan thành `.xlsx` có tiêu đề, 5 phân công và bảng Thứ/ngày–Sáng–Chiều. | ADMIN | MUST | Chỉ xuất plan tồn tại; nội dung Unicode và Event nhiều ngày đúng. |
| FR-EXPORT-002 | Thiết kế export có extension point cho VBA/PDF nhưng không triển khai MVP. | SYSTEM | COULD | Formatter tách khỏi truy xuất domain. |

## 4. Yêu cầu phi chức năng

| ID | Yêu cầu | Ưu tiên | Tiêu chí kiểm chứng |
|---|---|---|---|
| NFR-SEC-001 | Mật khẩu hash bằng Argon2id hoặc bcrypt cost phù hợp; không log password/token/secret. | MUST | Security test và log inspection. |
| NFR-SEC-002 | Cookie auth `HttpOnly`, `Secure` production, `SameSite=Lax`; mọi mutation chống CSRF. | MUST | Kiểm tra header/cookie tự động. |
| NFR-SEC-003 | Chống IDOR bằng kiểm tra ownership ở service; validate input và dùng query tham số. | MUST | Negative API tests. |
| NFR-PERF-001 | p95 API đọc thông thường <= 500 ms và ghi <= 800 ms ở 100 user đồng thời, không gồm email/export. | SHOULD | Smoke test staging với dữ liệu chuẩn. |
| NFR-AVAIL-001 | Mục tiêu khả dụng production 99.5% theo tháng, loại trừ bảo trì thông báo trước. | SHOULD | Monitoring uptime. |
| NFR-DATA-001 | DB backup hằng ngày; RPO 24 giờ, RTO 4 giờ. | MUST | Diễn tập restore theo quý. |
| NFR-OBS-001 | Structured logs có correlation ID, metrics health, scheduler/outbox failures và alert. | MUST | Runbook staging. |
| NFR-UX-001 | UI responsive desktop/tablet, keyboard usable, contrast WCAG 2.1 AA cho luồng chính. | SHOULD | Accessibility checks. |
| NFR-I18N-001 | Giao diện và Excel hỗ trợ tiếng Việt/Unicode; thời gian hiển thị timezone trường. | MUST | E2E và export test. |
| NFR-MAINT-001 | Modular monolith, Flyway migration forward-only, API version `/api/v1`. | MUST | Architecture review/CI. |
| NFR-COMPAT-001 | Hỗ trợ hai phiên bản gần nhất của Chrome, Edge, Firefox. | SHOULD | Browser matrix E2E. |

## 5. Phạm vi MVP

In scope: authentication UI/API, đăng ký/duyệt User, Department, BusinessRole, SchoolClass, AcademicYear/SchoolWeek, WeeklyPlan/copy/sections/targets/sessions/Event/duty class, Task, Notification/email, Reminder, Saturday Admin Reminder, Conversation, AuditLog và Excel cơ bản.

Out of scope: mobile app, Google Calendar, WebSocket chat, personal calendar/conflict detection, full-text search, audience riêng của Event, system role ngoài `ADMIN`/`USER`, version rollback, analytics nâng cao và VBA chi tiết.

## 6. Giả định và câu hỏi mở

- **ASSUMPTION-01:** Một trường/tenant duy nhất; timezone cấu hình mặc định `Asia/Ho_Chi_Minh`.
- **ASSUMPTION-02:** Xác thực dùng session opaque trong secure cookie, không dùng JWT trong MVP.
- **ASSUMPTION-03:** `SchoolClass` thuộc một `AcademicYear`; tên lớp duy nhất trong năm học. Điều này làm rõ dữ liệu lớp thay đổi theo năm.
- **ASSUMPTION-04:** Chỉ có một tài khoản `ADMIN` active theo mô tả “Hiệu trưởng là người duy nhất”; DB vẫn dùng enum để không khóa migration tương lai.
- **ASSUMPTION-05:** Tuần mặc định bắt đầu từ ngày Admin nhập và các tuần ban đầu liên tiếp 7 ngày; sau khi sinh, từng tuần sửa độc lập.
- **ASSUMPTION-06:** Notification “publish” gửi mọi User `ACTIVE`; email khi Admin chọn cũng gửi mọi User active.
- **ASSUMPTION-07:** Reminder chung do Admin tạo được gửi tới tất cả User active; Event chưa có audience riêng.
- **DECISION-01 (2026-08-19):** Mật khẩu tối thiểu 8 ký tự, có chữ thường, chữ hoa và ký tự đặc biệt. Việc bắt buộc MFA cho Admin ở production vẫn là OPEN QUESTION.
- **OPEN QUESTION-02:** Email provider, địa chỉ gửi và yêu cầu lưu trữ/bản quyền mẫu email là gì?
- **OPEN QUESTION-03:** `category` của Conversation là free text hay danh mục Admin quản lý?
- **OPEN QUESTION-04:** Có cho User tự sửa tên hiển thị/email sau khi được duyệt không?
- **OPEN QUESTION-05:** Mẫu Excel chính thức, logo, chữ ký, lề in và quy tắc gộp ô chưa được cung cấp.
- **OPEN QUESTION-06:** Retention cho AuditLog, Notification, Conversation và file backup chưa được chốt.
- **OPEN QUESTION-07:** Quick Action “Create Notification” có cho Admin soạn broadcast tự do không, hay chỉ điều hướng tới các trigger đã xác định? MVP hiện chỉ thiết kế notification từ trigger nghiệp vụ để không tự đặt thêm audience/quy trình.

## 7. Tiêu chí chấp nhận cấp hệ thống

1. Các critical flow trong [testing-strategy.md](testing-strategy.md) chạy qua staging và không có lỗi severity cao.
2. Mọi endpoint map đúng actor trong [api-design.md](api-design.md), bao gồm kiểm thử `401`, `403` và IDOR.
3. User không thể quan sát DRAFT qua API, dashboard, notification hoặc export.
4. Publish, cập nhật published plan, notification/outbox và audit giữ tính nhất quán giao dịch như [architecture.md](architecture.md).
5. Migration chạy từ database rỗng; backup staging được restore thành công.
6. Excel mở được bằng Excel/LibreOffice, hiển thị đúng tiếng Việt và cấu trúc yêu cầu.
