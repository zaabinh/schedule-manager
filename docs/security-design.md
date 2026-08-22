# Security Design

## 1. Security objectives và threat model

Tài sản chính: credential/session, DRAFT WeeklyPlan, hồ sơ User, Conversation, Task, reminder/email quota, AuditLog và backup. Trust boundaries: browser↔frontend/backend, backend↔PostgreSQL, worker↔email provider, CI/CD↔production.

| Threat | Ví dụ | Control chính |
|---|---|---|
| Spoofing | đoán password/session | Argon2id, rate limit, random session, optional MFA Admin |
| Tampering | User sửa plan/task khác | server authorization, ownership, optimistic lock, audit |
| Repudiation | Admin phủ nhận publish | immutable AuditLog + correlation/actor/time |
| Information disclosure | User thấy DRAFT, IDOR conversation | query predicate + service ownership; hidden `404` |
| Denial of service | spam login/message/reminder/export | layered rate/size/concurrency limits |
| Elevation | BusinessRole “Ban giám hiệu” thành Admin | tách SystemRole, deny-by-default policy |

## 2. Authentication

MVP dùng opaque session server-side:

1. Login nhận email/password qua HTTPS, response lỗi chung.
2. Password verify bằng Argon2id (ưu tiên) với tham số benchmark khoảng 100–300 ms trên production; Phase 1 hiện dùng bcrypt cost 12. Theo policy được chốt ngày 2026-08-19, input phải có ít nhất 8 ký tự, gồm chữ thường, chữ hoa, ký tự đặc biệt không phải khoảng trắng và tối đa 72 byte UTF-8 do giới hạn bcrypt.
3. Tạo 256-bit random session ID; DB chỉ lưu SHA-256 hash; rotate sau login/password reset/privilege change.
4. Cookie `__Host-session`: `HttpOnly`, `Secure` production, `SameSite=Lax`, `Path=/`, không Domain; idle timeout 8 giờ, absolute 24 giờ (**ASSUMPTION**, cần chốt).
5. Logout/inactivate/password reset revoke sessions. Cleanup session hết hạn theo job.

Không dùng JWT vì revoke/approval/inactive cần hiệu lực tức thời và hệ thống là monolith. Nếu sau này dùng JWT, phải có access token ngắn hạn, refresh token rotation/reuse detection, revocation và không lưu token trong localStorage.

Password reset token 256-bit, lưu hash, TTL 30 phút, dùng một lần; thay password revoke mọi session. Response request reset không tiết lộ email tồn tại.

## 3. Authorization

- Filter xác thực session; method policy deny by default và allow `ADMIN`/`USER` explicit.
- SystemRole là authority duy nhất. BusinessRole chỉ tham gia Relevant To Me/target.
- Service kiểm ownership cho Task, Reminder, NotificationRecipient, Conversation; repository query có actor predicate, không fetch rồi chỉ ẩn ở UI.
- User query WeeklyPlan luôn gồm `status='PUBLISHED'`; missing/DRAFT/không sở hữu trả `404` khi cần chống enumeration.
- Admin endpoint không dựa vào route name; kiểm `SystemRole.ADMIN` và account ACTIVE ở mỗi request.
- Mutation dùng version để tránh lost update; audit actor lấy từ security context, không nhận từ request.

## 4. Web controls

### CSRF và CORS

Vì cookie tự gửi, mọi POST/PATCH/DELETE yêu cầu synchronizer CSRF token gắn session, gửi trong `X-CSRF-Token`; login/register/reset dùng SameSite + kiểm Origin/Referer và rate limit. Token rotate khi login. CORS allowlist chính xác origin frontend theo environment, `allowCredentials=true`, không dùng `*`.

### XSS và content

Nội dung plan/Event/message lưu plain text. React escaping mặc định; cấm `dangerouslySetInnerHTML` trừ sanitizer review. Validation độ dài, loại control characters nguy hiểm. CSP không `unsafe-eval`; nếu framework cần nonce cho script thì cấp per response.

Headers: HSTS (production), `Content-Security-Policy`, `X-Content-Type-Options: nosniff`, `Referrer-Policy: strict-origin-when-cross-origin`, `Permissions-Policy`, `frame-ancestors 'none'`. XLSX filename dùng RFC 5987/sanitized, không phản chiếu input vào header.

### SQL injection và validation

JPA/JdbcTemplate parameter binding; cấm nối raw query với sort/filter. Sort allowlist. Bean Validation ở DTO và invariant domain; giới hạn JSON/body/message/content, page size, date range. Task attachment là upload duy nhất trong MVP và áp dụng kiểm soát riêng dưới đây.

### Task attachment security

- Allowlist extension + declared MIME + signature/magic bytes; OOXML phải là ZIP package chứa namespace `word/`, `xl/` hoặc `ppt/`. Executable/script bị chặn.
- Tên gốc được normalize, loại path/control characters và chỉ dùng display/download. Storage key UUID do server sinh; local adapter normalize đường dẫn và bắt buộc target nằm dưới root cấu hình.
- Backend authorization mọi list/download: `ADMIN` hoặc `task.assignee_user_id=currentActor.id`; User khác nhận `403`. Không expose bucket URL và không dựa vào UUID khó đoán.
- File tối đa 20 MiB, 10 file/Task, tổng 100 MiB; servlet có hard ceiling riêng và service là enforcement point. Count/total được kiểm tra khi lock Task để tránh race.
- Download ép `attachment`, filename UTF-8 và `nosniff`. Không log content/binary. Audit chỉ metadata/checksum.
- Local production storage cần persistent volume/disk và backup cùng DB metadata. Malware scanning chưa triển khai; phase sau thêm quarantine → scan → available trước khi cân nhắc signed URL.

## 5. IDOR và approval security

Mọi resource ID là UUID nhưng không coi UUID là authorization. Test matrix actor A/User owner/User khác/pending/inactive. Approval transaction validate Department/roles/class active, giới hạn tối đa hai Admin active, audit old/new. Không cho self-elevate hoặc request truyền `systemRole`; bootstrap Admin qua secret one-time migration/runbook, sau đó tắt bootstrap.

Khi User bị inactive: revoke session, ngăn login, không xóa data; queued personal email có thể được worker skip nếu recipient không active (trừ email thông báo trạng thái theo policy).

## 6. Abuse prevention và rate limit

| Route/action | Baseline (**ASSUMPTION**) | Key |
|---|---|---|
| login/reset/register | 5/min và 30/hour | IP + normalized email hash |
| conversation message | 20/min | user |
| personal reminder create | 30/hour, tối đa 100 pending | user |
| Admin broadcast reminder/update email | 10/hour + recipient cap | Admin/action |
| export | 5/min, max 2 concurrent | Admin |
| general API | 120/min | session/IP |

Trả `429` + `Retry-After`. Provider email có domain/account quota, bounded retry, circuit breaker. Admin broadcast cần confirm recipient count. Idempotency key chống double click/retry.

**Phase 1 implementation note:** login/register dùng limiter in-memory theo action + IP + SHA-256 của normalized email, 5/phút và 30/giờ. Đây chỉ phù hợp một instance; trước horizontal scaling phải chuyển sang store dùng chung và bổ sung `Retry-After` chính xác.

## 7. Email security

SPF, DKIM, DMARC cho domain gửi; TLS provider; template escape mọi nội dung; link dùng HTTPS canonical host và không chứa session/token ngoài reset token một lần. Không đưa Conversation/Task nhạy cảm đầy đủ vào subject; email log chỉ provider message ID/error code, không body. Unsubscribe không áp dụng cho email vận hành bắt buộc nhưng cần chính sách nhà trường.

Reminder/Event thay đổi có thể làm remindAt lỗi thời: update Event phải hiển thị/đánh giá reminder liên quan; **OPEN QUESTION** xác định tự tính lại hay giữ absolute time. MVP giữ absolute `remindAt`, Admin/User phải xác nhận khi sửa Event time.

## 8. Audit, logging và sensitive data

Audit append-only, DB account ứng dụng không UPDATE/DELETE; log approval, status/role/config, CRUD/publish plan/Event/task, reminder, close conversation, export và security event. Redact password/hash/session/CSRF/reset token/secrets và email body. Old/new JSON allowlist, giới hạn size.

Application logs không ghi request body mặc định, Cookie, Authorization, token query, password, secret, raw provider payload. User ID pseudonymous được phép; email chỉ khi cần xử lý với access-controlled logs. Correlation ID validate/replace để chống log injection.

Sensitive data at rest: managed DB encryption; backup encrypted; least-privileged DB users riêng migration/runtime/read-only monitoring. Production access qua MFA/SSO provider và audit trail.

## 9. Secrets và supply chain

Secret chỉ qua platform secret manager/environment injection: DB credentials, session pepper, CSRF secret, provider API key. Không commit `.env`; rotate định kỳ và khi incident. CI dùng short-lived credential nếu có, protected branch/environment approvals.

Pin dependency lockfiles; Dependabot/Renovate tương đương; SCA, secret scan, SAST và container scan trong CI. Base image minimal, non-root, digest-pinned cho production. Tạo SBOM/release provenance nếu hạ tầng hỗ trợ.

## 10. Backup và operational security

Backup encrypted, tách quyền khỏi runtime, retention theo OPEN QUESTION-06; restore test hàng quý. Health endpoint không lộ version/schema/error. Actuator/admin docs không public. DB/worker alert không chứa payload. Incident runbook gồm revoke sessions/secrets, disable email worker, preserve audit, restore và thông báo.

## 11. OWASP verification checklist

- Broken access control: role + ownership + published predicate + IDOR tests.
- Cryptographic failures: TLS, modern password hashing, encrypted backup/secrets.
- Injection: parameterized persistence, allowlist sort, escaped templates.
- Insecure design: threat model, confirm broadcast, transaction/outbox/idempotency.
- Misconfiguration: hardened headers/CORS/Actuator/profile.
- Vulnerable components: scans/update SLA.
- Authentication failures: generic errors, rate limit, rotation/revocation, MFA decision.
- Integrity failures: protected CI, signed/digest image, Flyway checksum.
- Logging failures: security events/alert without secrets.
- SSRF: email provider URL/config fixed; không nhận arbitrary callback URL.

## 12. Security gates và câu hỏi mở

Trước production: ASVS-inspired review, automated authorization matrix, dependency/secret scan sạch mức high/critical, TLS/header scan, restore drill, Admin bootstrap removal và penetration test tập trung IDOR/publish/reminder abuse.

- **OPEN QUESTION:** MFA Admin có bắt buộc? Khuyến nghị có trước production.
- **OPEN QUESTION:** MFA Admin và timeout/rate baseline cần nhà trường phê duyệt; password policy đã được chốt ngày 2026-08-19.
- **OPEN QUESTION:** Retention và người được phép truy cập AuditLog/log/backup.
- **OPEN QUESTION:** Chính sách xử lý reminder khi Event đổi thời gian.
