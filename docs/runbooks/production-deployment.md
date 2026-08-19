# Production Deployment Runbook

## Preconditions

- Phase 12 release gates PASS; release SHA/tag đã được duyệt.
- Staging validation đã PASS theo `docs/runbooks/staging-validation.md` và evidence gắn đúng image digest.
- Linux host có Docker Engine + Compose, Node.js 22+, PowerShell 7 (cho backup/restore), reverse proxy TLS, DNS cho web/API và outbound SMTP.
- Secret được lưu trong secret manager hoặc file `.env.production` quyền `0600`; không commit file này.
- Backup gần nhất đã kiểm tra và rollback owner đang trực.

## Deploy

1. Sao chép `.env.production.example` thành `.env.production`, thay toàn bộ placeholder bằng secret ngẫu nhiên và hostname HTTPS thật.
2. Chạy preflight: `npm run release:validate-config`. Script kiểm tra required values, placeholder, độ dài secret, cookie `__Host-`, HTTPS origins, địa chỉ email, SMTP port và Compose interpolation nhưng không in secret.
3. Build images: `docker compose --env-file .env.production -f compose.production.yaml build --pull`; CI phải push registry, ghi digest và production phải triển khai đúng digest đã được duyệt.
4. Backup trước migration: `pwsh scripts/backup-production.ps1 -Name pre-release-<version>`.
5. Khởi động database/backend trước; Flyway tự chạy forward-only. Kiểm tra `/actuator/health/readiness` trả `UP`.
6. Khởi động frontend, cấu hình reverse proxy HTTPS tới `127.0.0.1:3000` và API tới `127.0.0.1:8080`.
7. Smoke: login Admin/User, current week, create/complete Task, notification, email thử, reminder worker và tải Excel.
8. Kiểm tra Prometheus `/actuator/prometheus`, outbox/reminder failed counters, log correlation ID và backup job.

Production profile chủ động từ chối khởi động nếu cookie không Secure, CORS không HTTPS, bootstrap còn bật, email không SMTP, pepper/DB password dùng mẫu local.

## Rollback

- Nếu migration chưa chạy: hạ phiên bản mới và chạy image trước đó.
- Nếu migration additive đã chạy: ứng dụng cũ chỉ được rollback khi compatibility matrix xác nhận; không tự sửa Flyway history.
- Nếu cần restore dữ liệu, dừng mọi writer, xác nhận đúng database rồi chạy `pwsh scripts/restore-production.ps1 -BackupName <file.dump> -ConfirmDatabase <db>`.
- Sau rollback: readiness, login, dữ liệu tuần hiện tại, email/outbox và audit phải smoke lại.

## Ownership

- Release owner quyết định go/no-go.
- Database owner thực hiện backup/restore.
- Security owner quản lý TLS/secrets.
- On-call theo dõi ít nhất một chu kỳ scheduler sau deploy.
