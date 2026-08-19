# Implementation Gap Analysis

Ngày đánh giá: 2026-08-19  
Baseline: repository hiện tại sau Phase 12 và bộ tài liệu phiên bản 1.0.

## Kết luận điều hành

Toàn bộ vertical slice nghiệp vụ Phase 0–11 đã tích hợp thật từ frontend qua REST/backend tới PostgreSQL; không còn runtime mock trong luồng sản phẩm. Phase 12 hiện **READY FOR STAGING**. Repository đã có kiểm thử hồi quy, E2E production-like, đóng gói container, backup/restore rehearsal, baseline hiệu năng và passive DAST cục bộ.

## Ma trận hiện tại

| Hạng mục | Frontend | Backend/DB | Bằng chứng tự động | Trạng thái |
|---|---|---|---|---|
| Authentication/session/approval | REST integrated | Opaque session, CSRF, rate limit, audit | Integration + E2E | Complete |
| Organization/classes/academic years | REST integrated | Flyway V1–V3, constraints, optimistic locking | Integration + E2E | Complete |
| WeeklyPlan/Event/publish/dashboard | REST integrated | Flyway V4, transactional aggregate/publish | Integration + E2E | Complete |
| Task/Notification/Reminder | REST integrated | Flyway V5, scheduler/outbox/idempotency | Integration + UI E2E + SMTP Mailpit exact recipient/subject | Complete |
| Conversation | REST integrated | Authorization, unread state, polling contract | Integration + E2E | Complete |
| Audit/Excel | REST/download integrated | Append-only audit, Unicode/formula-safe export | Integration + E2E | Complete |
| Packaging/operations | Production images and Compose | Health checks, migrations, backup/restore scripts | Container E2E + restore rehearsal | Locally complete |
| Performance/security | Production-like browser path | PostgreSQL 18 and backend containers | k6 100 VU baseline + strict nonce CSP + ZAP passive baseline | Local baseline complete |

## Khoảng trống cần môi trường bên ngoài

1. Chạy staging với topology, dữ liệu và write load đại diện production.
2. Chạy active authenticated DAST trên HTTPS và kiểm tra thủ công IDOR/CSRF theo các vai trò.
3. Xác minh SMTP provider thật: credentials, sender/domain, STARTTLS và quota. Đường gửi của ứng dụng đã PASS cục bộ bằng Mailpit.
4. Cấp DNS/TLS, managed PostgreSQL, secret manager và immutable/off-site backup storage.
5. Kết nối Prometheus/alerting, centralized logs, on-call và diễn tập restore trên staging.
6. Hoàn tất UAT, phê duyệt file Excel mẫu, change window và ghi nhận immutable release digest.
7. Chốt hoặc risk-accept MFA Admin, retention dữ liệu/log/backup và data residency.

## Technical debt không chặn staging

- Rate limiter đang lưu trong memory; phải chuyển sang distributed store trước khi chạy nhiều backend replica.
- Conversation dùng polling 15 giây; WebSocket/SSE là cải tiến sau MVP.
- Password reset nằm ngoài MVP hiện tại.
- JDK 26 cục bộ có thể phát cảnh báo parser ArchUnit; CI và container dùng Java 21 theo baseline.

## Quyết định

- **Go staging:** Có.
- **Go production:** Chưa; chỉ được phê duyệt sau khi các bằng chứng hạ tầng, bảo mật chủ động, SMTP thật, restore staging và UAT ở trên hoàn tất.
