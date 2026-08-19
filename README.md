# School Weekly Plan Manager

Ứng dụng quản lý kế hoạch tuần cho trường THPT. Repository gồm frontend Next.js và backend Spring Boot modular monolith, sử dụng PostgreSQL/Flyway. Phase 0–12 đã hoàn thành toàn bộ MVP và hardening ở mức repository; release candidate hiện sẵn sàng triển khai staging để hoàn tất các gate môi trường thật trước production.

## Công nghệ

- Frontend: Next.js 16, React 19, TypeScript, Tailwind CSS
- Backend: Java 21, Spring Boot 4.1, Spring Security
- Database: PostgreSQL 18, Flyway
- Tests: JUnit, ArchUnit, MockMvc và Testcontainers

## Yêu cầu môi trường

- Node.js 22+ và npm
- JDK 21
- Maven 3.9+ hoặc Maven Wrapper trong `backend/`
- Docker Desktop hoặc Docker Engine có Compose

Docker phải đang chạy nếu sử dụng PostgreSQL local hoặc chạy integration/E2E tests.

## Chạy project local

### 1. Cài frontend dependencies

Từ thư mục gốc:

```powershell
npm ci
Copy-Item .env.example .env.local
```

Trên macOS/Linux:

```bash
npm ci
cp .env.example .env.local
```

Frontend chỉ sử dụng biến public sau từ file này:

```dotenv
NEXT_PUBLIC_API_BASE_URL=http://localhost:8080/api/v1
```

### 2. Khởi động PostgreSQL

```bash
docker compose up -d postgres
docker compose ps
```

PostgreSQL được mở tại `localhost:5433` với cấu hình local:

```text
Database: schedule_manager
Username: schedule_app
Password: schedule_local_password
```

Volume `schedule-postgres-data` giữ dữ liệu giữa các lần restart container.

PostgreSQL 18+ phải mount volume tại `/var/lib/postgresql`, không phải `/var/lib/postgresql/data`. Nếu Docker báo có dữ liệu tại đường dẫn cũ, xem mục [Khắc phục PostgreSQL 18 volume](#khắc-phục-postgresql-18-volume).

#### Nếu dùng PostgreSQL cài riêng

Bạn chỉ cần tự tạo database khi không dùng service PostgreSQL trong `compose.yaml`. Chạy script bootstrap bằng tài khoản PostgreSQL có quyền tạo role/database:

```powershell
psql -U postgres -d postgres `
  -v app_db=schedule_manager `
  -v app_user=schedule_app `
  -v app_password=schedule_local_password `
  -f scripts/init-local-database.sql
```

macOS/Linux:

```bash
psql -U postgres -d postgres \
  -v app_db=schedule_manager \
  -v app_user=schedule_app \
  -v app_password=schedule_local_password \
  -f scripts/init-local-database.sql
```

Script [scripts/init-local-database.sql](scripts/init-local-database.sql) có thể chạy lại: nó tạo hoặc đồng bộ application role, tạo database nếu chưa có và cấp quyền schema. Không chạy trực tiếp `V1__baseline.sql`; khi backend khởi động, Flyway sẽ tự tạo `flyway_schema_history` và áp dụng toàn bộ schema đúng thứ tự.

### 3. Khởi động backend và tạo Admin lần đầu

Spring Boot không tự đọc file `.env.local`; cần truyền backend environment variables vào process.

PowerShell:

```powershell
$env:DB_URL="jdbc:postgresql://localhost:5433/schedule_manager"
$env:DB_USER="schedule_app"
$env:DB_PASSWORD="schedule_local_password"
$env:CORS_ALLOWED_ORIGINS="http://localhost:3000"
$env:SESSION_COOKIE_NAME="session"
$env:SESSION_COOKIE_SECURE="false"
$env:SESSION_PEPPER="replace-with-a-random-secret-at-least-32-characters"
$env:BOOTSTRAP_ADMIN_ENABLED="true"
$env:BOOTSTRAP_ADMIN_EMAIL="admin@example.edu.vn"
$env:BOOTSTRAP_ADMIN_PASSWORD="LocalAdmin@2026"
$env:BOOTSTRAP_ADMIN_DISPLAY_NAME="Hiệu trưởng"
$env:BOOTSTRAP_ADMIN_DEPARTMENT_NAME="Văn phòng"

Set-Location backend
mvn.cmd spring-boot:run
```

macOS/Linux:

```bash
export DB_URL='jdbc:postgresql://localhost:5433/schedule_manager'
export DB_USER='schedule_app'
export DB_PASSWORD='schedule_local_password'
export CORS_ALLOWED_ORIGINS='http://localhost:3000'
export SESSION_COOKIE_NAME='session'
export SESSION_COOKIE_SECURE='false'
export SESSION_PEPPER='replace-with-a-random-secret-at-least-32-characters'
export BOOTSTRAP_ADMIN_ENABLED='true'
export BOOTSTRAP_ADMIN_EMAIL='admin@example.edu.vn'
export BOOTSTRAP_ADMIN_PASSWORD='LocalAdmin@2026'
export BOOTSTRAP_ADMIN_DISPLAY_NAME='Hiệu trưởng'
export BOOTSTRAP_ADMIN_DEPARTMENT_NAME='Văn phòng'

cd backend
./mvnw spring-boot:run
```

Flyway tự áp dụng migration khi application khởi động. Backend chạy tại `http://localhost:8080`; health check:

```text
http://localhost:8080/actuator/health
```

Bootstrap chỉ dùng một lần. Sau khi Admin được tạo thành công, dừng backend, đặt `BOOTSTRAP_ADMIN_ENABLED=false`, xóa `BOOTSTRAP_ADMIN_PASSWORD` khỏi environment rồi khởi động lại. Bootstrap không thay đổi mật khẩu của Admin đã tồn tại.

Mật khẩu đăng ký và bootstrap phải có ít nhất 8 ký tự, ít nhất một chữ thường, một chữ hoa và một ký tự đặc biệt không phải khoảng trắng; giới hạn tối đa là 72 byte UTF-8.

### 4. Khởi động frontend

Mở terminal khác tại thư mục gốc:

```bash
npm run dev
```

Truy cập:

- Frontend: http://localhost:3000
- Đăng nhập: http://localhost:3000/login
- Đăng ký: http://localhost:3000/register
- Quản lý người dùng: http://localhost:3000/admin/users
- Quản lý phòng ban: http://localhost:3000/admin/departments
- Quản lý vai trò: http://localhost:3000/admin/roles
- Quản lý lớp học/GVCN: http://localhost:3000/admin/classes
- Quản lý năm học/tuần: http://localhost:3000/admin/academic-years

Tài khoản mới luôn có trạng thái `PENDING`. Đăng nhập bằng Admin bootstrap để cấu hình phòng ban, vai trò nghiệp vụ và phê duyệt trước khi tài khoản đó có thể đăng nhập.

## Chạy kiểm tra

### Frontend

```bash
npm run lint
npm run typecheck
npm run build
```

Chạy production build local:

```bash
npm run build
npm run start
```

### Backend

Unit và architecture tests:

```powershell
Set-Location backend
mvn.cmd test
```

Trên macOS/Linux có thể dùng `./mvnw test`.

Full verification, bao gồm package, clean PostgreSQL migration và onboarding/security E2E bằng Testcontainers:

```powershell
Set-Location backend
mvn.cmd verify
```

Docker phải đang chạy. Build thành công tạo executable JAR tại:

```text
backend/target/schedule-manager-backend-0.1.0-SNAPSHOT.jar
```

## Cấu hình quan trọng

| Variable | Mặc định local | Mục đích |
|---|---|---|
| `NEXT_PUBLIC_API_BASE_URL` | `http://localhost:8080/api/v1` | Base URL frontend gọi backend |
| `DB_URL` | `jdbc:postgresql://localhost:5433/schedule_manager` | JDBC URL |
| `DB_USER` | `schedule_app` | Database user |
| `DB_PASSWORD` | `schedule_local_password` | Database password local |
| `DB_POOL_MAX_SIZE` | `20` | Số connection tối đa của Hikari; cân đối với giới hạn PostgreSQL |
| `DB_POOL_MIN_IDLE` | `5` | Số connection idle tối thiểu |
| `DB_CONNECTION_TIMEOUT_MS` | `5000` | Thời gian chờ connection trước khi fail-fast |
| `AUTH_RATE_LIMIT_PER_MINUTE` | `5` | Giới hạn đăng nhập/đăng ký theo client trong một phút |
| `AUTH_RATE_LIMIT_PER_HOUR` | `30` | Giới hạn đăng nhập/đăng ký theo client trong một giờ |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000` | Exact frontend origins, phân cách bằng dấu phẩy |
| `SESSION_COOKIE_NAME` | `session` | Tên cookie local |
| `SESSION_COOKIE_SECURE` | `false` | Phải là `true` trên HTTPS production |
| `SESSION_PEPPER` | local fallback | Secret dùng hash session/CSRF; production phải thay |
| `BOOTSTRAP_ADMIN_ENABLED` | `false` | Bật đúng một lần để tạo Admin đầu tiên |

Production nên dùng cookie `__Host-session`, HTTPS và secret manager; không sử dụng credential local trong môi trường thật.

## Dừng local services

```bash
docker compose down
```

Lệnh trên giữ PostgreSQL volume. Nếu cần xóa toàn bộ dữ liệu local, chủ động chạy `docker compose down --volumes`; thao tác này không thể khôi phục dữ liệu trong volume.

### Khắc phục PostgreSQL 18 volume

Image PostgreSQL 18 đổi `PGDATA` sang thư mục có major version và yêu cầu mount parent `/var/lib/postgresql`. `compose.yaml` hiện đã dùng layout mới.

Nếu volume chỉ chứa dữ liệu development có thể tạo lại, chạy:

```bash
docker compose down --volumes
docker compose up -d postgres
docker compose logs -f postgres
```

`docker compose down --volumes` xóa vĩnh viễn database local. Không chạy nếu cần giữ dữ liệu.

Nếu volume có dữ liệu cần giữ, dừng tại đây: backup volume, xác định PostgreSQL major version đã tạo dữ liệu và thực hiện `pg_upgrade` bằng cả binary phiên bản cũ/mới. Không di chuyển hoặc đổi tên file cluster tùy ý, và không dùng volume đó để khởi tạo database rỗng mới. Với dữ liệu quan trọng, ưu tiên dựng lại container phiên bản cũ, `pg_dump`, sau đó restore vào PostgreSQL 18.

## Cấu trúc chính

```text
src/                         Next.js frontend
backend/src/main/java/       Spring Boot application
backend/src/main/resources/  Config và Flyway migrations
backend/src/test/            Unit, architecture và E2E tests
docs/                        Requirements, design, plans và phase reports
compose.yaml                 PostgreSQL local
```

Tài liệu tiến độ gần nhất: [Phase 12 Report](docs/phase-reports/phase-12.md). Xem thêm [Production Readiness Report](docs/production-readiness-report.md), [Project Roadmap](docs/project-roadmap.md) và [Production Deployment Runbook](docs/runbooks/production-deployment.md).

## Production build và triển khai

```powershell
Copy-Item .env.production.example .env.production
# Thay toàn bộ placeholder bằng secret/hostname thật trước khi tiếp tục.
npm.cmd run release:validate-config
docker compose --env-file .env.production -f compose.production.yaml build --pull
docker compose --env-file .env.production -f compose.production.yaml up -d --wait
```

Preflight đa nền tảng từ chối thiếu biến, placeholder, secret ngắn, cookie không có prefix `__Host-`, origin không phải HTTPS, email/SMTP port sai hoặc Compose không nội suy được. Stack production chạy PostgreSQL trên network nội bộ; frontend/backend chỉ bind loopback để reverse proxy TLS sử dụng. PostgreSQL cùng các dependency kiểm thử được pin digest; backend profile `prod` sẽ từ chối startup nếu cookie/CORS/email/secret còn cấu hình không an toàn. Xem [Production Deployment Runbook](docs/runbooks/production-deployment.md), [Staging Validation](docs/runbooks/staging-validation.md), [Backup & Restore](docs/runbooks/backup-restore.md) và `.env.production.example`.

Release gates local:

```powershell
npm.cmd run lint
npm.cmd run typecheck
npm.cmd run build
npm.cmd run test:e2e
npm.cmd audit --audit-level=high
Set-Location backend
mvn.cmd clean verify
```

Full-stack browser E2E trên Windows dựng riêng PostgreSQL 18 bằng `tmpfs`, Mailpit SMTP sandbox được pin digest, backend bootstrap và frontend production image. Frontend dùng cổng `3100`, Mailpit API/UI dùng `18025` và SMTP loopback dùng `18026`; script luôn dọn stack khi hoàn tất hoặc thất bại:

```powershell
npm.cmd run test:e2e:full
```

Test này không sử dụng database local tại cổng `5433`. Docker phải đang chạy và các cổng `18080`, `3100`, `18025`, `18026` phải còn trống. Cấu hình nằm trong `compose.e2e.yaml`; credential `admin.e2e@example.edu.vn` và toàn bộ email Mailpit chỉ tồn tại trong stack E2E tạm thời.

Performance smoke cục bộ dùng k6 với 100 session độc lập, 100 VU trong 30 giây và ba API đọc có xác thực. Gate yêu cầu p95 `<500 ms`, lỗi `<1%` và check thành công `>99%`:

```powershell
npm.cmd run test:performance
```

Đây là baseline trên dữ liệu tối thiểu. Trước go-live vẫn phải chạy lại trên staging với dataset chuẩn và tài nguyên PostgreSQL/JVM tương đương production.

Passive security baseline dùng OWASP ZAP image pin theo digest. Policy bắt buộc không lộ `X-Powered-By`, không có `script-src 'unsafe-inline'` và có COEP hợp lệ:

```powershell
npm.cmd run test:security
```

Gate local này không thay thế active/authenticated DAST và kiểm tra IDOR/CSRF thủ công trên HTTPS staging.

## Trạng thái hiện tại

Phase 0–11 đã PASS. Phase 12 hiện READY FOR STAGING với full-stack browser E2E 11 PASS/1 intentionally skipped: desktop chạy trọn chuỗi đăng ký/phê duyệt → phát hành kế hoạch → Excel → notification/reminder qua SMTP Mailpit → hoàn thành task → trao đổi; mobile chạy các smoke auth/onboarding/accessibility riêng. Phase 13 vẫn cần staging dataset/write load, active DAST, SMTP provider credential/sender/TLS/quota, DNS/TLS/secret và UAT/sign-off trước production go-live.
