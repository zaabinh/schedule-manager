# Render + Vercel Deployment Runbook

Mục tiêu là chạy frontend Next.js trên Vercel, API Spring Boot và PostgreSQL managed trên Render. Cấu hình hạ tầng Render nằm trong `render.yaml`; Vercel nhận diện Next.js trực tiếp từ thư mục gốc repository.

## 1. Điều kiện bắt buộc

- Có quyền quản trị repository GitHub, tài khoản Render và Vercel.
- Có một domain do bạn quản lý. Dùng hai subdomain cùng gốc, ví dụ `app.school.edu.vn` và `api.school.edu.vn`.
- Có SMTP provider, sender đã xác minh và credential dùng STARTTLS cổng 587.
- Đồng ý chi phí hiển thị trong dashboard trước khi tạo tài nguyên. Blueprint đề xuất Render web plan `starter`, PostgreSQL `basic-256mb`, region Singapore; hãy kiểm tra giá hiện hành ngay trên Render trước khi xác nhận.

Ưu tiên sibling custom domains cho production. Nếu dùng cặp domain mặc định `*.vercel.app` và `*.onrender.com`, bắt buộc bật same-origin API proxy theo mục Vercel bên dưới; gọi Render trực tiếp từ browser sẽ không mang cookie `SameSite=Lax` trong `/auth/me`.

## 2. Phần bạn thực hiện

### Render

1. Vào Render Dashboard, chọn **New > Blueprint**, kết nối repository và chọn branch `main`.
2. Render đọc `render.yaml` và đề xuất một web service, một PostgreSQL database và persistent disk `task-attachments` gắn tại `/var/lib/schedule-manager`. Xem lại region/plan/dung lượng disk/chi phí rồi mới Apply; không bỏ disk nếu dùng Task attachment.
3. Điền các biến được Render yêu cầu. Không gửi các giá trị bí mật qua chat và không commit chúng:

| Biến | Giá trị |
|---|---|
| `CORS_ALLOWED_ORIGINS` | `https://app.<domain-cua-ban>` |
| `BOOTSTRAP_ADMIN_EMAIL` | Email Admin đầu tiên |
| `BOOTSTRAP_ADMIN_PASSWORD` | Ít nhất 8 ký tự, có chữ thường, chữ hoa, ký tự đặc biệt; dùng passphrase mạnh riêng cho production |
| `BOOTSTRAP_ADMIN_DISPLAY_NAME` | Tên hiển thị Admin |
| `BOOTSTRAP_ADMIN_DEPARTMENT_NAME` | Phòng ban ban đầu |
| `EMAIL_FROM` | Sender đã xác minh |
| `SMTP_HOST` | Host SMTP của provider |
| `SMTP_USERNAME` | Tài khoản SMTP |
| `SMTP_PASSWORD` | Secret SMTP |

`FILE_STORAGE_TYPE=local` và `FILE_STORAGE_LOCAL_ROOT=/var/lib/schedule-manager/uploads` đã nằm trong Blueprint. Sau deploy, upload một tệp kiểm tra, restart service và xác nhận vẫn download được để chứng minh disk thực sự persistent. Backup/restore production phải ghép đúng snapshot PostgreSQL metadata với snapshot disk attachment.

4. Blueprint tự sinh `SESSION_PEPPER`, nhận `DATABASE_URL` từ database và chạy initial deploy hook để tạo Admin đúng một lần. Chờ web service có trạng thái Live và `/actuator/health/readiness` trả `UP`.
5. Trong Render, thêm custom domain `api.<domain-cua-ban>` và tạo DNS record đúng theo hướng dẫn Render hiển thị. Chờ TLS được cấp thành công.
6. Đăng nhập thành công một lần, kiểm tra đúng Admin, rồi xóa `BOOTSTRAP_ADMIN_PASSWORD` và các biến `BOOTSTRAP_ADMIN_*` không còn cần thiết khỏi Render. Giữ `BOOTSTRAP_ADMIN_ENABLED=false` và `APP_PROVISIONING_MODE=false`.

### Vercel

1. Import cùng repository GitHub vào Vercel; chọn branch production `main`, framework Next.js và Root Directory là `.`.
2. Nếu đang dùng custom sibling domains, thêm Production Environment Variable:

```text
NEXT_PUBLIC_API_BASE_URL=https://api.<domain-cua-ban>/api/v1
```

Nếu chưa có custom domain và đang dùng `*.vercel.app` + `*.onrender.com`, cấu hình proxy cùng-origin để cookie không bị chặn:

```text
NEXT_PUBLIC_API_BASE_URL=/api/v1
BACKEND_ORIGIN=https://<render-service>.onrender.com
```

Trong cả hai trường hợp, `CORS_ALLOWED_ORIGINS` trên Render phải bằng chính xác origin production của frontend Vercel, ví dụ `https://<vercel-project>.vercel.app` (không có path hoặc dấu `/` cuối). Khi dùng proxy, browser chỉ gọi Vercel và rewrite chuyển tiếp `/api/v1/*` sang Render, nên session cookie trở thành first-party.

3. Deploy, sau đó thêm custom domain `app.<domain-cua-ban>` và cấu hình DNS theo hướng dẫn Vercel.
4. Sau khi biến môi trường hoặc domain thay đổi, redeploy frontend để giá trị public được đóng vào build.
5. Không cho Vercel Preview dùng API/database production. Nếu cần preview đầy đủ, tạo backend/database/CORS riêng.

## 3. Phần Codex thực hiện

- Duy trì `render.yaml`, Docker image, health check, adapter `DATABASE_URL`, one-shot Admin provisioning và cấu hình frontend.
- Chạy lint, typecheck, build, backend verify, E2E/rehearsal và theo dõi GitHub Actions.
- Khi bạn cung cấp hai URL công khai (không cung cấp secret), kiểm tra DNS/TLS, readiness, security headers và cấu hình CORS/cookie.
- Khi credential kiểm thử staging được đặt trong environment trên máy bạn, chạy `npm run release:verify-staging`; script đăng nhập, kiểm tra session/CSRF rồi logout mà không in mật khẩu.
- Ghi Phase Report/evidence và chỉ đề xuất go-live sau khi SMTP thật, backup/restore, performance/write load, active DAST và UAT hoàn tất.

Codex không thể thay bạn mở tài khoản, chấp nhận thanh toán, sở hữu/chỉnh DNS hoặc nhập/đọc production secret. Các thao tác đó cần người có thẩm quyền thực hiện trong dashboard.

## 4. Thông tin gửi lại sau khi deploy

Chỉ gửi hai URL không bí mật:

```text
WEB_ORIGIN=https://app.<domain-cua-ban>
API_ORIGIN=https://api.<domain-cua-ban>
```

Không gửi database URL, session pepper, SMTP password hoặc Admin password. Đặt credential staging trong PowerShell rồi báo Codex tiếp tục:

```powershell
$env:STAGING_WEB_ORIGIN="https://app.<domain-cua-ban>"
$env:STAGING_API_ORIGIN="https://api.<domain-cua-ban>"
$env:STAGING_ADMIN_EMAIL="<email>"
$env:STAGING_ADMIN_PASSWORD="<read-from-password-manager>"
npm.cmd run release:verify-staging
Remove-Item Env:STAGING_ADMIN_PASSWORD
```

## 5. Tài liệu chính thức

- [Render Blueprints](https://render.com/docs/infrastructure-as-code)
- [Render Blueprint specification](https://render.com/docs/blueprint-spec)
- [Render Docker services](https://render.com/docs/docker)
- [Render custom domains](https://render.com/docs/custom-domains)
- [Vercel Git deployments](https://vercel.com/docs/git)
- [Vercel Next.js deployments](https://vercel.com/docs/frameworks/full-stack/nextjs)
- [Vercel environment variables](https://vercel.com/docs/environment-variables)
- [Vercel custom domains](https://vercel.com/docs/domains/working-with-domains)
