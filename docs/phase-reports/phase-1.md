# PHASE 1 – AUTHENTICATION, SESSION & USER APPROVAL

**Status:** PASS  
**Completed:** 2026-08-19

## Implemented

- Register tạo duy nhất `USER/PENDING`, normalize email, kiểm password policy (8+ ký tự, lower/upper/special) và lưu bcrypt cost 12.
- Login trả lỗi chung cho credential/PENDING/INACTIVE; tạo opaque session và CSRF token ngẫu nhiên, DB chỉ lưu hash có pepper.
- Session idle 8 giờ/absolute 24 giờ, `/auth/me` rotate CSRF, logout và inactive revoke session.
- Origin/Referer allowlist cho guest auth, exact CORS credential, custom CSRF và security headers.
- Rate limit login/register 5/phút và 30/giờ theo IP + email hash.
- Admin list/filter users, lấy approval options, approve bằng Department/BusinessRole/Class và đổi trạng thái có optimistic version/audit.
- Authorization chỉ theo SystemRole; protected BusinessRole không thể biến USER thành ADMIN.
- Admin bootstrap một lần qua environment, tắt mặc định và không seed credential trong migration.
- Frontend login/register/admin users chuyển từ mock sang REST envelope client, cookie credentials, CSRF và API error handling.
- Sửa Phase 0 foundation để Spring Boot 4.1 thực thi Flyway lúc startup bằng `spring-boot-starter-flyway`.

## Documents reviewed and aligned

- Requirements, business rules, use cases, API/security/database design, testing/deployment plan, roadmap và current repository.
- Updated: architecture, API/security/testing/deployment design, gap analysis, implementation plan, roadmap và Phase 1 plan.

## Verification evidence

| Gate | Result | Evidence |
|---|---|---|
| Frontend lint | PASS | ESLint, 0 warning/error |
| Frontend typecheck | PASS | TypeScript `--noEmit` |
| Frontend production build | PASS | Next.js 16.3.1, 22 routes generated |
| Backend unit + architecture | PASS | 5 tests, 0 failure/error |
| Clean PostgreSQL migration E2E | PASS | PostgreSQL 18 Testcontainers, Flyway V1 applied at application startup |
| Phase 1 onboarding/security E2E | PASS | 2 scenarios, 0 failure/error |
| Full backend verify/package | PASS | 8 total tests, executable JAR, 0 failure/error |

## Security evidence

- Raw password/session/CSRF không được trả API hoặc lưu DB; auth request `toString` redacts password và request/JDBC debug logging bị tắt.
- Missing CSRF bị `403`; invalid/missing origin bị `403`; unauthenticated Admin bị `401`; USER gọi Admin endpoint bị `403`.
- PENDING/INACTIVE không login; inactive session bị revoke; duplicate registration trả contract `EMAIL_EXISTS`.
- Approval luôn giữ `systemRole=USER`, kể cả khi gán BusinessRole “Ban giám hiệu”.

## Decisions, deferrals and debt

- Password reset (SHOULD) deferred vì không thuộc Phase 1 exit signal đã thống nhất.
- CRUD Department/BusinessRole/Class/GVCN thuộc Phase 2; Phase 1 chỉ cung cấp lookup read-only cho approval.
- Rate limiter in-memory chỉ dùng single instance; phải chuyển distributed store trước horizontal scaling và bổ sung precise `Retry-After`.
- Browser Playwright chưa có harness; critical onboarding được E2E tại HTTP/security/PostgreSQL boundary. Browser E2E nằm trong hardening backlog.
- Local Maven dùng JDK 26 nên ArchUnit 1.4.1 cảnh báo khi đọc JDK class major 70; rules vẫn PASS, CI chuẩn hóa JDK 21.

## Ready for next phase

**YES.** Phase 1 đáp ứng Definition of Done. Phase tiếp theo: Phase 2 – Department, BusinessRole, SchoolClass và GVCN.
