# Danh mục quy tắc nghiệp vụ

Phiên bản 1.0. Thuật ngữ chuẩn dùng PascalCase cho entity, chữ hoa cho enum. Requirement liên quan nằm trong [requirements.md](requirements.md).

## Identity và tổ chức

| ID | Quy tắc | Rationale / edge case |
|---|---|---|
| BR-001 | `SystemRole` chỉ gồm `ADMIN`, `USER`; `BusinessRole` không cấp quyền hệ thống. | Ngăn nhầm vai trò nghiệp vụ với authorization. |
| BR-002 | Đăng ký luôn tạo `USER/PENDING`; chỉ Admin được chuyển sang `ACTIVE` sau khi gán Department. | User pending không đăng nhập được. |
| BR-003 | Mỗi User có đúng một Department khi `ACTIVE`. | User `PENDING` có thể tạm chưa có Department. |
| BR-004 | User có 0..n BusinessRole; mỗi cặp User–BusinessRole là duy nhất. | Role inactive vẫn giữ lịch sử nhưng không được gán mới/target mới. |
| BR-005 | User có dữ liệu nghiệp vụ không bị xóa vật lý; dùng `INACTIVE`. | FK và audit phải còn nguyên. |
| BR-006 | Department, BusinessRole, SchoolClass được deactivate thay vì xóa khi đã được tham chiếu. | Tên cũ vẫn hiển thị trong lịch sử. |
| BR-007 | BusinessRole mặc định được đánh dấu `isProtected`; không được xóa vật lý. | Có thể đổi tên/deactivate chỉ theo policy Admin và audit. |
| BR-008 | Một User chủ nhiệm tối đa một SchoolClass active; một SchoolClass tối đa một GVCN. | Input: user/class active. Output: assignment hoặc conflict `409`. |
| BR-009 | Chỉ một Admin active trong MVP. | **ASSUMPTION-04**; kích hoạt Admin thứ hai trả conflict. |

## Năm và tuần học

| ID | Quy tắc | Rationale / edge case |
|---|---|---|
| BR-010 | AcademicYear có tên duy nhất và sinh mặc định 39 SchoolWeek. | Hai orientation + 37 study. |
| BR-011 | `sequenceNumber` là thứ tự nội bộ duy nhất trong AcademicYear; `displayNumber` không phải identifier. | Cho phép trùng display number giữa loại tuần. |
| BR-012 | Mặc định sequence 1–2 là `ORIENTATION` display 1–2; sequence 3–39 là `STUDY` display 1–37. | Mapping cố định khi generate. |
| BR-013 | Mỗi SchoolWeek phải có `startDate <= endDate`; ngày từng tuần chỉnh độc lập. | Tuần có thể không dài 7 ngày. Overlap được cảnh báo theo ASSUMPTION, không tự sửa. |
| BR-014 | Mỗi SchoolWeek có tối đa một WeeklyPlan. | Unique FK ở DB; race trả `409`. |

## WeeklyPlan

| ID | Quy tắc | Rationale / edge case |
|---|---|---|
| BR-015 | WeeklyPlan mới có status `DRAFT`; User không được thấy DRAFT. | Với User, trả `404` thay vì `403` để không lộ resource. |
| BR-016 | WeeklyPlan luôn có đúng 5 PlanSection với `SectionType`: `ACADEMIC_AFFAIRS`, `FACILITIES_OFFICE`, `YOUTH_UNION`, `HOMEROOM_TEACHERS`, `TEACHERS`. | Unique plan/type và displayOrder 1..5. |
| BR-017 | PlanSection có 0..n target loại `ALL`, `ROLE`, `DEPARTMENT`, `USER`; target phải khớp đúng một tham chiếu theo loại. | `ALL` không có targetId. Target trùng bị chặn. |
| BR-018 | Mỗi plan/date có đúng hai DaySession: `MORNING`, `AFTERNOON`; date nằm trong SchoolWeek. | Khi đổi khoảng tuần, đồng bộ session là thao tác nghiệp vụ riêng, cần cảnh báo dữ liệu bị loại. |
| BR-019 | Event chỉ bắt buộc `content`; nếu có cả start/end date hoặc time thì start không sau end. | Event thiếu thời gian được phép nhưng tạo publish warning. |
| BR-020 | Event nhiều ngày xuất hiện trên mọi ngày từ `startDate` đến `endDate` inclusive. | Không nhân bản row Event. |
| BR-021 | Một WeeklyPlan có tối đa một duty class cho `MORNING` và một cho `AFTERNOON`; lớp phải active. | Lưu trực tiếp hai FK nullable trong plan. |
| BR-022 | Copy plan chỉ copy 5 section (content/targets) và DaySession.baseContent. | Không copy Event, Reminder, Notification, Task hay duty classes. Task không được yêu cầu rõ trong danh sách “không copy”, nhưng là phân công cá nhân nên không copy để tránh giao nhầm; đánh dấu **ASSUMPTION**. |

**DECISION Phase 4:** xác nhận Task không được copy; `ALL` là target độc quyền trong section; copy retry theo cùng actor/Idempotency-Key trả cùng plan và warnings.
| BR-023 | Publish bị chặn bởi: week không tồn tại, startDate > endDate, hoặc cấu trúc WeeklyPlan invalid. | Validation trả errors và warnings tách biệt. |
| BR-024 | Thiếu duty class, section trống, ngày không nội dung, Event thiếu thời gian là warning; Admin có thể publish with warnings. | Xác nhận explicit trong request. |
| BR-025 | Publish chuyển sang `PUBLISHED`, ghi `publishedAt/by` và notification mọi User active. | Thực hiện sau commit qua outbox. |
| BR-026 | Admin được sửa plan `PUBLISHED`; gửi website/email chỉ theo lựa chọn explicit. | Cả hai false vẫn lưu và audit, không spam. |

### Logic “Relevant To Me”

**BR-027.** Một item thuộc “Dành cho bạn” khi ít nhất một điều kiện đúng:

- PlanSection target `ALL`;
- target `USER` trùng User;
- target `ROLE` thuộc BusinessRole active của User;
- target `DEPARTMENT` trùng Department của User;
- Task.assigneeUserId trùng User;
- duty class là SchoolClass mà User đang làm GVCN.

Input: User active và WeeklyPlan published. Output: danh sách deduplicate, kèm `matchedBy`; một section khớp nhiều điều kiện chỉ hiện một lần. Event chung có thể hiển thị trong Today/Weekly Plan, không tự tính là assignment “Dành cho bạn”.

## Task, Notification và Reminder

| ID | Quy tắc | Rationale / edge case |
|---|---|---|
| BR-028 | Task persisted status chỉ `TODO`, `COMPLETED`. | `OVERDUE = status != COMPLETED AND dueAt < now`. |
| BR-029 | Chỉ assignee được complete Task; thao tác lặp lại idempotent và giữ `completedAt` đầu tiên. | Admin cập nhật task qua endpoint quản trị khác. |
| BR-030 | Giao hoặc thay đổi Task có ý nghĩa tạo Notification cho assignee. | Retry dùng idempotency key. |
| BR-031 | Notification và NotificationRecipient tách riêng; read/unread là `readAt` nullable trên recipient. | `sentAt` ghi khi record được tạo/đưa vào center; delivery email theo outbox riêng. |
| BR-032 | User chỉ đọc/mark recipient của chính mình. | Chống IDOR. |
| BR-033 | Reminder source `ADMIN` và `USER` độc lập; User không sửa/xóa reminder source ADMIN. | Hai reminder cùng thời điểm vẫn là hai bản ghi hợp lệ. |
| BR-034 | Nếu Event đủ startDate/startTime, preset reminder tính từ thời điểm bắt đầu; nếu thiếu thì bắt buộc `remindAt`. | `remindAt` phải ở tương lai khi tạo. |
| BR-035 | Scheduler claim reminder atomically; trạng thái `PENDING`, `PROCESSING`, `SENT`, `FAILED`, retry giới hạn. | Không giữ DB transaction trong lúc gọi email provider. |
| BR-036 | Thứ Bảy 08:00 và 17:00 theo timezone trường, gửi Admin reminder chỉ nếu plan của tuần kế tiếp chưa `PUBLISHED`. | Mỗi slot tối đa một lần nhờ unique job key; không có next week thì ghi skip. |

## Conversation, audit và export

| ID | Quy tắc | Rationale / edge case |
|---|---|---|
| BR-037 | Conversation do User tạo; chỉ creator và Admin được đọc/gửi message. | Không broadcast cho User khác. |
| BR-038 | Chỉ Admin đóng Conversation; CLOSED không nhận message mới. | Close lặp lại idempotent. |
| BR-039 | Message mới tạo Notification cho phía còn lại; không cần WebSocket. | Frontend polling/refetch. |
| BR-040 | AuditLog append-only và chỉ Admin đọc; old/new value phải redact secret/credential. | Không rollback version trong MVP. |
| BR-041 | Các mutation Admin, publish, complete task, reminder/conversation state change đều phải audit theo catalog action. | Actor SYSTEM dùng khi scheduler thực hiện. |
| BR-042 | Excel phản ánh snapshot WeeklyPlan tại thời điểm request, gồm 5 sections và bảng ngày/buổi. | Export không thay đổi domain; User không có quyền export trong MVP. |

## Ma trận trạng thái

| Entity | Chuyển hợp lệ |
|---|---|
| User | `PENDING -> ACTIVE`; `ACTIVE <-> INACTIVE`; `PENDING -> INACTIVE` |
| WeeklyPlan | `DRAFT -> PUBLISHED`; `PUBLISHED` được cập nhật nhưng không quay về DRAFT |
| Task | `TODO -> COMPLETED` |
| Conversation | `OPEN -> CLOSED` |
| Reminder | `PENDING -> PROCESSING -> SENT`; `PROCESSING -> PENDING/FAILED` theo retry; `PENDING -> CANCELLED` khi actor có quyền hủy |

## Điểm chưa chốt

- **OPEN QUESTION:** Có cho re-open Conversation hay uncomplete Task không? MVP hiện không cho.
- **DECISION Phase 3:** Overlap giữa SchoolWeek là warning quản trị `WEEK_OVERLAP`, không chặn lưu; UI phải hiển thị rõ để Admin xử lý.
- **DECISION Phase 2:** Protected BusinessRole không được đổi tên, mô tả hoặc trạng thái; API trả `ROLE_PROTECTED 409`. Thay đổi chính sách này cần migration/ADR riêng.
- **OPEN QUESTION:** Quick Action “Create Notification” là thông báo tự do hay chỉ là lối tắt tới Event/reminder/update plan? Chưa expose API broadcast tự do để tránh tự đặt audience/quy trình duyệt.
