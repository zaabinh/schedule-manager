# Production Readiness Report

Ngày: 2026-08-19  
Kết luận: **Application artifact sẵn sàng đưa lên staging; production deployment chưa được phê duyệt.**

## Scope hoàn thành

Phase 0–11 đã PASS toàn bộ MVP: auth/approval, organization, academic calendar, WeeklyPlan/Event/publish, relevant dashboards, Task, Notification/SMTP outbox, Reminder/Saturday scheduler, Conversation, Audit append-only và Excel. Frontend không còn runtime mock.

## Release evidence

- Frontend: lint/typecheck/build PASS, 23 dynamic routes với nonce CSP; Playwright + axe 11 PASS/1 intentionally skipped (full business chain chạy desktop một lần, auth/onboarding/accessibility chạy desktop/mobile); npm audit 0.
- Authenticated browser baseline: production frontend image + backend + PostgreSQL 18 + Mailpit cô lập; bootstrap Admin/session/route guard, onboarding/approval và chuỗi publish plan → Excel → SMTP notification/reminder → task complete → conversation PASS. Mailpit assertion kiểm tra subject và recipient thật; axe 0 serious/critical tại các checkpoint sau khi sửa contrast Conversation.
- Backend: full clean verify 21 tests PASS; PostgreSQL 18 Testcontainers; Flyway V1–V5; production guard cưỡng chế cookie `__Host-`.
- Artifact: hai Docker images multi-stage/non-root build PASS.
- Supply-chain/config preflight: PostgreSQL, Mailpit, k6 và ZAP được pin digest; validator production chặn placeholder, secret ngắn, origin không HTTPS và Compose interpolation lỗi mà không in secret.
- Runtime smoke: production profile + safe test secrets + `__Host-session`, database migration, backend readiness `UP`, frontend 200/CSP/frame deny; rehearsal stack/volume đã được dọn.
- Data safety: pg_dump custom format, restore rehearsal và readiness sau restore PASS.
- Performance local baseline re-run: k6 100 VU/30 giây, 100 session độc lập, 20.457 authenticated reads, p95 323,72 ms và 0% lỗi; staging dataset/API write vẫn là release gate.
- Security local baseline: strict per-request nonce CSP, COEP/COOP/CORP, không lộ framework header; OWASP ZAP passive scan 0 FAIL/0 WARN/65 PASS. Active/authenticated HTTPS DAST vẫn là staging gate.
- Rehearsal resources đã được xóa: stack/volume `schedule-release-check` và backup tạm `release-rehearsal.dump`; không thể khôi phục chúng, dữ liệu chỉ là smoke data.

## Go-live blockers / required external inputs

1. Cấp hostname/DNS và TLS certificate cho web/API.
2. Cấp PostgreSQL production, secret manager và backup object storage/retention.
3. Cấp SMTP production credential, sender verification, TLS policy và quota; SMTP application path đã PASS với Mailpit local.
4. Cấp Prometheus/alerting/log retention/on-call ownership.
5. Dựng staging tương đương production để chạy dataset/write load, active authenticated DAST và xác nhận SMTP provider thực tế; chuỗi browser plan/publish/Excel/SMTP notification+reminder/task/conversation và accessibility đã PASS local production-like.
6. UAT/sign-off của Hiệu trưởng + đại diện giáo viên; duyệt mẫu Excel chính thức.
7. Chốt change window, rollback owner và release tag/image digest.
8. Nhà trường phê duyệt hoặc risk-accept các quyết định production còn mở: MFA cho Admin, retention AuditLog/Notification/Conversation/log/backup và data residency.

## Decision

- **Go staging:** YES.
- **Go production ngay:** NO, cho tới khi tám mục trên có evidence/sign-off.
- Runbook triển khai: `docs/runbooks/production-deployment.md`.
- Runbook kiểm chứng staging: `docs/runbooks/staging-validation.md`; script `npm run release:verify-staging` đã sẵn sàng nhưng cần URL/credential staging thật.
