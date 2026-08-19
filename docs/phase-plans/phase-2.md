# Phase 2 Plan – Organization and SchoolClass/GVCN

Ngày đối chiếu: 2026-08-19

## Baseline đã xác nhận

- Phase 1 PASS sau frontend lint/typecheck/build, backend verify, clean migration và onboarding/security E2E.
- Department/BusinessRole/Class pages vẫn dùng mock service và biểu mẫu chưa persistence.
- Flyway V1 có bảng/FK/unique indexes nhưng chưa có optimistic `version` trên ba aggregate Phase 2.
- SchoolClass bắt buộc `academic_year_id`; CRUD/generator AcademicYear vẫn thuộc Phase 3.

## Scope

1. CRUD mềm Department: list/filter/create/update/activate/deactivate, unique normalized name và audit.
2. CRUD mềm BusinessRole với cùng contract; protected seed không cho sửa/deactivate.
3. CRUD mềm SchoolClass theo AcademicYear, grade 10–12, optional GVCN.
4. Một User chỉ chủ nhiệm tối đa một class active; teacher phải là active `USER`.
5. Lookup read-only AcademicYear và teacher phục vụ form Class; tạo/generate AcademicYear không chuyển sớm khỏi Phase 3.
6. Frontend ba trang quản trị bỏ mock, hỗ trợ create/edit/status và hiển thị lỗi REST.
7. Flyway V2 thêm optimistic version/index cần thiết; không sửa V1 đã phát hành.
8. Organization API/security/constraint E2E trên PostgreSQL thật.

## Assumptions có kiểm soát

- Protected BusinessRole immutable về name/description/status trong Phase 2; mutation trả `ROLE_PROTECTED 409`.
- Deactivate Department đang được tham chiếu được phép để giữ lịch sử; không thể gán mới cho User.
- Deactivate SchoolClass giữ GVCN lịch sử. Vì unique index chỉ áp dụng class active, teacher có thể nhận class active khác.
- API management Phase 2 chỉ dành cho ADMIN. Lookup USER sẽ được expose qua projection của feature tiêu thụ sau này.
- Nếu chưa có AcademicYear, UI Class hiển thị hướng dẫn chờ/tạo dữ liệu ở Phase 3; E2E seed AcademicYear như dependency fixture.

## Definition of Done

- Department/Role/Class REST CRUD và frontend persistence hoạt động, không còn mock cho ba page.
- Duplicate normalized name, stale version, invalid grade, protected role và GVCN conflict bị chặn đúng contract.
- Mọi mutation ghi audit; không hard-delete resource nghiệp vụ.
- USER/unauthenticated không gọi management endpoint; CSRF vẫn áp dụng.
- Frontend lint/typecheck/build và backend full verify/E2E PASS; Phase 2 Report tồn tại.
