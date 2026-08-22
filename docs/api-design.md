# REST API Design

Base path: `/api/v1`. JSON UTF-8, field camelCase, timestamp ISO-8601 có offset/UTC, date `YYYY-MM-DD`, UUID string. Cookie session được gửi tự động; mutation yêu cầu header `X-CSRF-Token`. Collection dùng `page`, `size<=100`, `sort` allowlist.

## 1. Contract chung

Success:

```json
{"success":true,"data":{"id":"8dd2..."},"message":null,"meta":{"correlationId":"..."}}
```

Page thêm `meta.page`, `size`, `totalElements`, `totalPages`. Error:

```json
{
  "success": false,
  "error": {
    "code": "VALIDATION_FAILED",
    "message": "Dữ liệu không hợp lệ.",
    "details": [{"field":"startDate","reason":"must_not_be_after_end_date"}]
  },
  "meta": {"correlationId":"7d1b..."}
}
```

Status: `200` read/update/action idempotent; `201` create; `204` delete/logout; `400` malformed JSON; `401` unauthenticated; `403` role/CSRF; `404` missing/hidden; `409` duplicate/version/state; `422` semantic validation; `429` rate limit; `500` unexpected. Mutation create/action nhận optional `Idempotency-Key` (bắt buộc cho publish/copy); update gửi `version` trong body.

## 2. Authentication và User

| Method/path | Actor | Request -> response | Validation | Errors/status |
|---|---|---|---|---|
| `POST /auth/register` | Guest | `{email,password,displayName}` -> `Registration{id,status}` | email RFC-like/max320; password 8+ ký tự, có lower/upper/special, tối đa 72 byte UTF-8; name 1..150 | `201`; EMAIL_EXISTS `409`, `422`, `429` |
| `POST /auth/login` | Guest | `{email,password}` -> `CurrentUser` + cookies | non-empty; ACTIVE only | `200`; INVALID_CREDENTIALS `401`, `429` |
| `POST /auth/logout` | A/U | none -> none | valid CSRF | `204`; `401/403` |
| `GET /auth/me` | A/U | none -> `CurrentUser` | session valid | `200/401` |
| `POST /auth/password-reset-requests` | Guest | `{email}` -> generic message | email valid | `202`; `429` |
| `POST /auth/password-resets` | Guest | `{token,newPassword}` -> message | token one-use/not expired | `200`; RESET_TOKEN_INVALID `422`, `429` |
| `GET /users?status=PENDING` | A | query page/filter -> `Page<UserSummary>` | enum/allowlist sort | `200`; `422` |
| `GET /users/approval-options` | A | -> active departments, roles, available classes | read-only lookup | `200` |
| `GET /users/{id}` | A | -> `UserDetail` | UUID | `200`; USER_NOT_FOUND `404` |
| `PATCH /users/{id}/approval` | A | `{departmentId,businessRoleIds,homeroomClassId?,version}` -> UserDetail | refs active, BR-003/4/8 | `200`; `404`, USER_CONFIG_CONFLICT `409`, `422` |
| `PATCH /users/{id}` | A | `{displayName,departmentId,businessRoleIds,homeroomClassId?,version}` -> UserDetail | same + name | `200`; `404/409/422` |
| `PATCH /users/{id}/status` | A | `{status:"ACTIVE|INACTIVE",version}` -> UserDetail | transitions; tối đa hai Admin active | `200`; INVALID_STATE `409`, `422` |

`CurrentUser = {id,email,displayName,systemRole,status,department,businessRoles,homeroomClass}`. Không endpoint nào trả `passwordHash`.

**Phase 1 implementation note:** bốn auth endpoint register/login/logout/me và ba user-admin endpoint list/approval-options/approval/status đã được triển khai. Hai password-reset endpoint và user detail/profile update được giữ trong contract mục tiêu, chưa implement ở Phase 1.

## 3. Organization và SchoolClass

| Method/path | Actor | Request -> response | Validation | Errors/status |
|---|---|---|---|---|
| `GET /departments` | A; U dùng lookup giới hạn | filters -> Page/array Department | `active` boolean | `200/422` |
| `POST /departments` | A | `{name,description?}` -> Department | unique normalized, max lengths | `201`; `409/422` |
| `PATCH /departments/{id}` | A | `{name?,description?,isActive?,version?}` -> Department | referenced deactivate allowed, not delete | `200`; `404/409/422` |
| `GET /business-roles` | A; U lookup | filters -> roles | active boolean | `200/422` |
| `POST /business-roles` | A | `{name,description?}` -> BusinessRole | unique | `201`; `409/422` |
| `PATCH /business-roles/{id}` | A | `{name?,description?,isActive?}` -> role | protected policy | `200`; ROLE_PROTECTED `409`, `422` |
| `GET /classes` | A/U | `academicYearId,active,page` -> Page SchoolClass | year required for admin list | `200/422` |
| `POST /classes` | A | `{academicYearId,name,grade,homeroomTeacherId?}` -> SchoolClass | grade 10..12, unique/GVCN | `201`; `404/409/422` |
| `PATCH /classes/{id}` | A | mutable fields + `version` -> SchoolClass | BR-008 | `200`; `404/409/422` |

U chỉ được dùng lookup trong dữ liệu published/relevant; backend không trả email/dữ liệu quản trị của User khác.

**Phase 2 implementation note:** toàn bộ endpoint Department, BusinessRole và SchoolClass ở mục này đã được triển khai với quyền Admin. `GET /organization/options` trả các AcademicYear hiện có và User ACTIVE chưa được phân công GVCN để phục vụ form lớp học. Các response quản trị có `version`; mọi update bắt buộc gửi `version`. Protected BusinessRole bất biến về tên, mô tả và trạng thái, vi phạm trả `ROLE_PROTECTED 409`.

## 4. AcademicYear và SchoolWeek

| Method/path | Actor | Request -> response | Validation | Errors/status |
|---|---|---|---|---|
| `GET /academic-years` | A/U | filters -> list | — | `200` |
| `POST /academic-years` | A | `{name,startDate,isActive?,generateWeeks}` -> AcademicYear | name unique/date, one active | `201`; `409/422` |
| `PATCH /academic-years/{id}` | A | `{name,startDate,isActive,version}` -> AcademicYear | one active; active class guard | `200`; `404/409/422` |
| `POST /academic-years/{id}/weeks/generate` | A | `{count:39}` -> list SchoolWeek | year empty; count MVP=39 | `201`; WEEKS_EXIST `409`, `422` |
| `GET /academic-years/{id}/weeks` | A/U | -> list SchoolWeek | U projection only | `200/404` |
| `PATCH /weeks/{id}` | A | `{displayNumber,weekType,startDate,endDate,version}` -> SchoolWeek | start<=end; sequence immutable | `200`; `404/409/422` |

SchoolWeek response luôn chứa `id,academicYearId,sequenceNumber,displayNumber,weekType,startDate,endDate,version`.

**Phase 3 implementation note:** các endpoint mục này đã được triển khai. AcademicYear response thêm `isActive,weekCount,version`; SchoolWeek response thêm `warnings`, hiện có `WEEK_OVERLAP`. GET cho mọi tài khoản đã xác thực, mutation chỉ dành cho Admin và yêu cầu CSRF. Generator chạy nguyên tử, chỉ nhận 39 và không chạy lại khi đã có tuần.

## 5. WeeklyPlan

| Method/path | Actor | Request -> response | Validation | Errors/status |
|---|---|---|---|---|
| `GET /weekly-plans?academicYearId` | A | -> list week + plan status | year exists | `200/404` |
| `GET /weeks/{weekId}/plan` | A/U | -> WeeklyPlanDetail | U predicate PUBLISHED | `200`; PLAN_NOT_FOUND `404` |
| `POST /weeks/{weekId}/plan` | A | `{}` -> WeeklyPlanDetail | one/Week | `201`; PLAN_EXISTS `409`, `404` |
| `POST /weeks/{weekId}/plan/copy` | A | `{sourceWeekId}` -> `{plan,warnings}` | target empty/source exists; idempotency | `201`; `404/409/422` |
| `PATCH /weekly-plans/{id}` | A | WeeklyPlanUpdate -> detail | exactly 5; target/session/duty rules; version | `200`; `404/409/422` |
| `GET /weekly-plans/{id}/options` | A | -> active duty classes/targets | plan exists | `200/404` |
| `POST /weekly-plans/{id}/validation` | A | optional candidate -> ValidationResult | structural/business | `200`; `404/422` malformed candidate |
| `POST /weekly-plans/{id}/publish` | A | `{version,publishWithWarnings,idempotencyKey}` -> detail | errors none; warnings confirmed | `200`; PUBLISH_BLOCKED `422`, `409` |
| `PATCH /weekly-plans/{id}/published-content` | A | `{changes,notifyWebsite,notifyEmail,version}` -> detail | plan PUBLISHED; booleans required | `200`; INVALID_STATE `409`, `422` |
| `GET /weekly-plans/{id}/export.xlsx` | A | -> XLSX binary | plan exists | `200`; `404/500` |

`WeeklyPlanUpdate`:

```json
{
  "version": 3,
  "sections": [{
    "sectionType": "HOMEROOM_TEACHERS",
    "content": "Hoàn thành xếp loại rèn luyện.",
    "displayOrder": 4,
    "targets": [{"targetType":"ROLE","businessRoleId":"..."}]
  }],
  "dutyClasses": {"morningClassId":"...","afternoonClassId":"..."},
  "daySessions": [{"date":"2026-11-09","session":"MORNING","baseContent":"Học theo TKB số 11"}]
}
```

`ValidationResult = {valid, errors:[{code,path,message}], warnings:[...]}` với codes `WEEK_INVALID`, `PLAN_STRUCTURE_INVALID`, `DUTY_CLASS_MISSING`, `SECTION_EMPTY`, `DAY_EMPTY`, `EVENT_TIME_MISSING`.

**Phase 5 implementation note:** management list/get/create/copy/options, DRAFT/full-save, Event CRUD, validation, publish và published-content đã được triển khai. Copy/publish yêu cầu header `Idempotency-Key`; published mutation bắt buộc lựa chọn website/email rõ ràng. `GET /weekly-plans/current` trả kế hoạch published hiện tại/gần nhất cho actor đã đăng nhập.

### Event

| Method/path | Actor | Request -> response | Validation | Errors/status |
|---|---|---|---|---|
| `POST /weekly-plans/{id}/events` | A | EventWrite -> Event | content required/date-time rules | `201`; `404/409/422` |
| `PATCH /events/{id}` | A | EventWrite + version -> Event | same; parent plan version check | `200`; `404/409/422` |
| `DELETE /events/{id}` | A | version query/header -> none | ownership/version; audit | `204`; `404/409` |

`EventWrite = {content,startDate?,endDate?,session?,startTime?,endTime?,location?,note?}`. Event trong published plan phải đi qua confirmation contract: client gửi `notifyWebsite`, `notifyEmail` trên mutation (hoặc dùng endpoint published-content); backend từ chối nếu thiếu.

## 6. Dashboard và Relevant To Me

| Method/path | Actor | Response | Errors/status |
|---|---|---|---|
| `GET /dashboard/me?weekId?` | U | `{currentWeek,relevantToMe,today,weeklyPlan,notificationSummary}` | `200`; `404` nếu week explicit không published |
| `GET /dashboard/admin` | A | `{currentPlan,needsAttention,currentWeek,quickActions}` | `200` |

Relevant item trả `{kind,entityId,title,matchedBy:[ALL|ROLE|DEPARTMENT|USER|TASK|HOMEROOM_CLASS],deepLink}` và được deduplicate.

## 7. Task

| Method/path | Actor | Request -> response | Validation | Errors/status |
|---|---|---|---|---|
| `POST /tasks` | A | `{weeklyPlanId,assigneeUserId,title,description?,dueAt}` -> Task | refs active/title/due | `201`; `404/422` |
| `GET /tasks` | A | plan/assignee/status/overdue/page -> Page Task | overdue boolean derived | `200/422` |
| `GET /tasks/summary` | A | filters -> `{total,completed,incomplete,overdue}` | date range | `200/422` |
| `PATCH /tasks/{id}` | A | mutable fields + version -> Task | state/ref/version | `200`; `404/409/422` |
| `GET /tasks/me` | U | filters/page -> Page Task incl. `displayStatus` | own only | `200/422` |
| `PATCH /tasks/{id}/complete` | U | `{version}` -> Task | owner; idempotent | `200`; hidden `404`, `409` |

Task response có persisted `status` và derived `displayStatus` (`TODO|COMPLETED|OVERDUE`), tránh client tự lệch clock.

## 8. Notification và Reminder

Không expose `POST /notifications` broadcast tự do trong hợp đồng hiện tại; ý nghĩa Quick Action “Create Notification” là **OPEN QUESTION-07**. Các notification MVP được tạo từ publish/update plan, Task, duty class, Conversation và reminder đã định nghĩa.

| Method/path | Actor | Request -> response | Validation | Errors/status |
|---|---|---|---|---|
| `GET /notifications` | A/U | `read?,cursor?,size?` -> cursor page | own recipient | `200/422` |
| `GET /notifications/unread-count` | A/U | -> `{count}` | own | `200` |
| `PATCH /notifications/{id}/read` | A/U | `{}` -> Recipient | own/idempotent | `200`; hidden `404` |
| `PATCH /notifications/read-all` | A/U | `{before?}` -> `{updatedCount}` | before valid | `200/422` |
| `POST /events/{id}/reminders` | A/U | `{preset?,remindAt?,recipientUserIds?}` -> list Reminder | U recipient=self; A default all active; exactly preset/absolute | `201`; `403/404/422` |
| `GET /reminders/me` | A/U | status/page -> Page Reminder | own | `200/422` |
| `DELETE /reminders/{id}` | A/U | -> none | pending; owner; U cannot ADMIN source | `204`; `403/404/409` |

Preset enum: `MINUTES_15`, `MINUTES_30`, `HOUR_1`, `DAY_1`, `CUSTOM`; DB chỉ lưu calculated `remindAt` và source.

## 9. Conversation và AuditLog

| Method/path | Actor | Request -> response | Validation | Errors/status |
|---|---|---|---|---|
| `POST /conversations` | U | `{subject,category?,message}` -> ConversationDetail | subject/message limits | `201`; `422/429` |
| `GET /conversations` | A/U | status/page -> Page ConversationSummary | U own only | `200/422` |
| `GET /conversations/{id}` | A/U | -> detail | creator/Admin | `200`; hidden `404` |
| `GET /conversations/{id}/messages` | A/U | cursor/size -> cursor page | creator/Admin | `200`; `404/422` |
| `POST /conversations/{id}/messages` | A/U | `{content}` -> Message | OPEN/ownership/limit | `201`; `404`, CONVERSATION_CLOSED `409`, `422/429` |
| `PATCH /conversations/{id}/close` | A | `{version}` -> Conversation | OPEN; idempotent | `200`; `404/409` |
| `GET /audit-logs` | A | filters actor/entity/action/from/to/page -> Page AuditLog | bounded range/sort | `200/422` |
| `GET /audit-logs/{id}` | A | -> redacted AuditLog | UUID | `200/404` |

## 10. Security/operational endpoints

`GET /actuator/health/liveness` và `/readiness` được hạ tầng gọi, response tối thiểu, không public chi tiết dependency. Không đặt dưới `/api/v1`. API docs chỉ bật staging hoặc bảo vệ Admin. CORS chỉ allow origin cấu hình; `OPTIONS` theo policy.

## 11. Coverage và versioning

UC-01..23 đều map ít nhất một endpoint ở các mục trên; UC-18/19 là internal scheduled application command, không expose public trigger. Nếu cần manual retry, endpoint operational phải protected riêng và audit. Breaking change tạo `/api/v2`; additive field không breaking. Enum mới có thể làm client cũ lỗi nên phải feature negotiation/release đồng bộ.
