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
5. Khởi động database. Nếu chưa có Admin ACTIVE, thực hiện one-shot provisioning ở mục bên dưới; container không publish port và tự thoát sau khi transaction commit.
6. Khởi động backend bình thường; Flyway chạy forward-only và production guard xác nhận provisioning đã tắt. Kiểm tra `/actuator/health/readiness` trả `UP`.
7. Khởi động frontend, cấu hình reverse proxy HTTPS tới `127.0.0.1:3000` và API tới `127.0.0.1:8080`.
8. Smoke: login Admin/User, current week, create/complete Task, notification, email thử, reminder worker và tải Excel.
9. Kiểm tra Prometheus `/actuator/prometheus`, outbox/reminder failed counters, log correlation ID và backup job.

### One-shot provisioning Admin đầu tiên

Nạp bốn biến thông tin `BOOTSTRAP_ADMIN_*` từ secret manager vào environment của operator; không ghi chúng vào `.env.production` và không truyền giá trị secret trực tiếp trên command line. Sau đó chạy:

```powershell
docker compose --env-file .env.production -f compose.production.yaml up -d --wait postgres
docker compose --env-file .env.production -f compose.production.yaml run --rm --no-deps `
  -e APP_PROVISIONING_MODE=true `
  -e BOOTSTRAP_ADMIN_ENABLED=true `
  -e BOOTSTRAP_ADMIN_EMAIL `
  -e BOOTSTRAP_ADMIN_PASSWORD `
  -e BOOTSTRAP_ADMIN_DISPLAY_NAME `
  -e BOOTSTRAP_ADMIN_DEPARTMENT_NAME `
  backend
Remove-Item Env:BOOTSTRAP_ADMIN_PASSWORD
```

Trên Linux thay backtick bằng `\` và chạy `unset BOOTSTRAP_ADMIN_PASSWORD` ngay sau lệnh. Exit code phải là `0`; xác nhận audit `ADMIN_BOOTSTRAPPED` và đăng nhập Admin ở bước smoke. Lệnh có thể chạy lại an toàn khi đã có Admin ACTIVE: nó không đổi mật khẩu và vẫn tự thoát. Không dùng hai cờ provisioning trong startup backend thông thường.

CI và local release rehearsal kiểm tra cả lần chạy đầu lẫn chạy lặp bằng `pwsh scripts/run-production-provisioning-rehearsal.ps1`; script chỉ dùng credential tổng hợp, project/volume riêng và luôn dọn stack.

Production profile chủ động từ chối khởi động nếu cookie không Secure, CORS không HTTPS, bootstrap bật ngoài one-shot provisioning, email không SMTP, pepper/DB password dùng mẫu local.

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
