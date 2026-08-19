# Phase 12 Report — Testing & Hardening

Ngày đánh giá: 2026-08-19  
Trạng thái: **READY FOR STAGING** — chưa phải production go-live sign-off

## Gates đã đạt

| Gate | Kết quả |
|---|---|
| Frontend lint/typecheck/build | PASS — 23 dynamic routes với nonce CSP |
| Browser E2E + axe | PASS — 11 PASS/1 intentionally skipped; auth/onboarding chạy Chromium desktop/mobile, full chain desktop chạy plan → publish → Excel → SMTP notification/reminder → task complete → conversation; 0 serious/critical tại các checkpoint |
| SMTP sandbox delivery | PASS — Mailpit pin digest; xác nhận đúng recipient và subject cho plan update + due reminder |
| Dependency audit | PASS — 0 npm vulnerability |
| Backend full clean verify | PASS — 21 tests, 0 failure/error |
| Flyway clean migration | PASS — PostgreSQL 18, V1→V5 |
| Production images | PASS — frontend/backend build, non-root |
| Production compose smoke | PASS — PostgreSQL/backend/frontend healthy; `__Host-session`, readiness `UP`, frontend `/login` 200 |
| Release config preflight | PASS — chặn placeholder/secret ngắn/non-HTTPS và xác nhận Compose interpolation; fixture hợp lệ PASS, file example bị từ chối đúng |
| Readiness/security headers | PASS — `UP`, CSP và frame deny |
| Backup/restore rehearsal | PASS — post-restore readiness `UP` |
| Local performance smoke | PASS — re-run 100 VU/30s, 20.457 authenticated reads, p95 323,72 ms, 0% lỗi |
| OWASP ZAP passive baseline | PASS — 0 FAIL, 0 WARN, 65 rule PASS; 2 expected INFO |

## Hardening đã hoàn thành

- Production startup guard chặn cookie không Secure/không có prefix `__Host-`, CORS/bootstrap/email/secret local không an toàn.
- CSP, HSTS/backend headers, frontend security headers, Prometheus metrics và structured logs/correlation ID.
- Playwright CI, npm audit CI, Java 21/Node 22+ toolchain và cross-platform reproducible lockfile.
- Stack E2E cô lập gồm PostgreSQL 18 `tmpfs`, backend bootstrap và frontend production image; runner fail-fast và luôn dọn container/network.
- Sửa tương phản nội dung phụ và khả năng focus bàn phím của bảng người dùng theo kết quả axe authenticated.
- Hikari pool được cấu hình rõ qua environment, timeout fail-fast; k6 gate dùng 100 session độc lập và chạy tự động trong CI.
- Frontend dùng nonce CSP theo request, dynamic rendering, không còn `script-src 'unsafe-inline'`; tắt `X-Powered-By`, thêm COEP/COOP/CORP và pin ZAP image/policy trong CI.
- Auth shell dùng identity thật từ route guard context thay cho tên/avatar hard-code; Profile sửa đúng semantics `<dl>/<dt>/<dd>`.
- Container multi-stage/non-root, PostgreSQL internal network, loopback-only application ports.
- PostgreSQL, Mailpit, k6 và ZAP external images được pin digest; preflight production không làm lộ giá trị secret.
- Backup/restore/deploy/incident runbooks và scripts có target validation.
- Không còn import mock trong runtime `src`.

## Staging exit gates còn mở

- Chạy lại load test NFR trên staging với dataset chuẩn và cả API ghi; local authenticated-read baseline 100 VU đã đạt p95 323,72 ms nhưng chưa đại diện dữ liệu/topology production.
- Active/authenticated DAST trên HTTPS staging và manual IDOR/CSRF review; passive local baseline đã đạt 0 FAIL/WARN.
- Manual keyboard/screen-reader review trên staging; axe serious/critical = 0 đã PASS cho Login, Admin Users, User Profile và checkpoint cuối chuỗi nghiệp vụ, đồng thời đã sửa hai lỗi color contrast ở Conversation.
- Xác nhận credential, sender verification, STARTTLS và quota với SMTP provider của staging; application delivery path qua Mailpit đã PASS cho notification và reminder.
- Restore rehearsal trên hạ tầng staging/object storage và xác nhận RPO/RTO owner.
- UAT của Hiệu trưởng + đại diện giáo viên; không còn Sev-1/2.

Các gate này không thể được xác nhận chỉ bằng repository local; Phase 13 không nên go-live trước khi chúng được ký.
