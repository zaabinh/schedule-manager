# Actual Implementation Plan

Kế hoạch này dựa trên trạng thái repository ngày 2026-08-19, không sao chép nguyên roadmap định hướng.

## Nguyên tắc delivery

Mỗi phase là một vertical slice: contract → backend/domain/DB → REST adapter frontend → tests → E2E → docs → phase report. Không giữ mock cho flow đã tích hợp.

## Phase 0 – Baseline foundation

### Mục tiêu

Biến repository frontend-only thành foundation có thể phát triển end-to-end mà chưa thêm nghiệp vụ Phase 1.

### Công việc

1. Xác nhận frontend lint/typecheck/build và dependency audit.
2. Tạo Spring Boot modular-monolith skeleton, Java release 21 và Maven wrapper/build contract.
3. Tạo shared API envelope, safe exception mapping, correlation ID và health endpoint.
4. Tạo Flyway PostgreSQL baseline theo `database-design.md`.
5. Tạo unit/architecture smoke test và PostgreSQL migration integration test.
6. Tạo local environment example, Docker Compose và CI baseline.
7. Loại bỏ UI import trực tiếp mock data; page chỉ đi qua service interface.
8. Chạy clean migration; cập nhật architecture/deployment/testing docs và Phase 0 report.

### Definition of Done

- Frontend lint/typecheck/build pass.
- Backend compile/unit test/package pass.
- Migration integration test chạy trên PostgreSQL sạch.
- Module direction `shared <- feature` không bị vi phạm.
- Gap Analysis và Phase Report tồn tại.

## Dependency-driven phases sau Phase 0

| Phase | Vertical slice | Dependency bắt buộc | Exit signal |
|---|---|---|---|
| 1 | Register → PENDING → approve → ACTIVE → login | Phase 0 DB/session/security | Critical onboarding E2E pass; auth mock removed |
| 2 | Department/BusinessRole/Class/GVCN | Authenticated Admin từ P1 | Organization E2E + constraint tests |
| 3 | AcademicYear → 39 SchoolWeek → edit dates | P2 class year reference | Generator/migration/API/UI E2E |
| 4 | WeeklyPlan DRAFT/create/copy/edit | P3 SchoolWeek, P2 targets/classes | Copy/DRAFT visibility E2E |
| 5 | Event → validate → publish/update | P4 aggregate/outbox | User visibility + update notification E2E |
| 6 | Relevant To Me | P5 published plan, P2 user attributes | Deduplicated relevance tests/E2E |
| 7 | Task assign/complete/overdue | P1 user, P5 notification hook | Ownership/status E2E |
| 8 | Notification fan-out/read | P5/P7 events | Per-recipient isolation E2E |
| 9 | Reminder/email/Saturday scheduler | P5 Event, P8 outbox/email | Fake-clock/idempotency integration tests |
| 10 | Conversation | P1 auth, P8 notifications | Ownership/open/close E2E |
| 11 | AuditLog | Mutation catalog from prior phases | Redaction/append-only verification |
| 12 | Excel export | Stable WeeklyPlan read model | Openable Unicode XLSX tests |
| 13 | Full MVP E2E/hardening | P1–P12 PASS | System DoD checklist complete |

## Contract migration strategy

Frontend domain view model không được coi là API DTO. Phase 1 đã thêm HTTP envelope client và mapper DTO → view model trong service layer; cấu trúc có thể tiếp tục tách nhỏ theo feature khi số contract tăng:

```text
src/services/contracts       canonical API request/response
src/services/http            envelope/error/CSRF client
src/services/adapters        REST implementations
src/services/mappers         API DTO -> UI view model
```

Mọi feature đã chọn REST adapter mặc định; runtime `src` không còn import mock. Contract/integration test bảo vệ boundary backend/PostgreSQL và full-stack E2E bảo vệ chuỗi nghiệp vụ chính.

## OPEN QUESTION cần chốt theo phase

- Phase 1: password/session baseline đã chốt tạm thời; MFA Admin vẫn là production decision.
- Phase 2: **RESOLVED** – protected BusinessRole bất biến, không cho deactivate hay sửa nội dung.
- Phase 3: **RESOLVED** – overlap SchoolWeek được lưu nhưng trả warning `WEEK_OVERLAP`.
- Phase 7: **RESOLVED** – `dueAt` bắt buộc theo requirements/API/database baseline.
- Phase 9: Event đổi giờ có tự tính lại reminder hay giữ absolute time.
- Phase 10: Conversation category free text hay managed catalog.
- Phase 12: mẫu Excel chính thức.

## Execution status

- Phase 0: **PASS** ngày 2026-08-19. Frontend build gates, backend package/unit/architecture test và clean PostgreSQL migration đều đạt.
- Phase 1: **PASS** ngày 2026-08-19; CF-01, auth/session/approval security và frontend REST integration đã đạt.
- Phase 2: **PASS** ngày 2026-08-19; CRUD Department/BusinessRole/Class/GVCN, audit, optimistic locking và frontend REST integration đã đạt.
- Phase 3: **PASS** ngày 2026-08-19; AcademicYear/SchoolWeek, generator 39 tuần, overlap warning và frontend REST integration đã đạt.
- Phase 4: **PASS** ngày 2026-08-19; WeeklyPlan DRAFT/create/copy/full-save/editor, idempotency, audit và DRAFT visibility đã đạt.
- Phase 5: **PASS** ngày 2026-08-19; Event CRUD/multi-day, validation, publish idempotent, outbox hook, User visibility và published update đã đạt.
- Phase 6: **PASS** ngày 2026-08-19; BR-027 dedup/matchedBy và dashboard User/Admin REST projections đã đạt.
- Phase 7: **PASS** ngày 2026-08-19; Task assign/update/complete, server-derived overdue, ownership, audit/outbox đã đạt.
- Phase 8: **PASS** ngày 2026-08-19; Notification recipients, read tracking, outbox worker, retry/metrics và SMTP provider đã đạt.
- Phase 9: **PASS** ngày 2026-08-19; Reminder preset/custom, ownership, delivery worker và Saturday Admin scheduler đã đạt.
- Phase 10: **PASS** ngày 2026-08-19; Conversation nhiều lượt, ownership, close và notification phía đối diện đã đạt.
- Phase 11: **PASS** ngày 2026-08-19; Audit query/redaction/append-only và Excel Unicode/formula protection đã đạt.
- Phase 12: **READY FOR STAGING** ngày 2026-08-19; local automated gates, authenticated full-stack Playwright 11 PASS/1 intentionally skipped (gồm onboarding/approval và chuỗi plan/publish/Excel/SMTP notification+reminder/task/conversation), k6/ZAP, dependency audit, production guard/images và backup/restore rehearsal đã đạt. Dataset/write performance, active DAST, SMTP provider verification và UAT là staging gates còn mở.
- Phase 13: **EXTERNAL INPUT REQUIRED** – DNS/TLS/SMTP/secrets/monitoring và UAT sign-off.
