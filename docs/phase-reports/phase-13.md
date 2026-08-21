# Phase 13 Report — Production Deployment

Ngày: 2026-08-20  
Trạng thái: **IN PROGRESS — RENDER/VERCEL REPOSITORY PREPARATION PASS, EXTERNAL DEPLOYMENT REQUIRED**

## Kết quả

- Chốt topology Vercel frontend + Render Docker API + Render managed PostgreSQL, region Singapore.
- Thêm `render.yaml` với paid baseline `starter`/`basic-256mb`, readiness probe, generated session pepper, SMTP/CORS secrets và one-shot initial Admin provisioning.
- Backend hỗ trợ Render `DATABASE_URL`, biến `PORT` và vẫn ưu tiên cấu hình JDBC explicit ở môi trường hiện có.
- Production/staging validators ưu tiên sibling custom domains như `app.school.edu.vn` và `api.school.edu.vn`; deployment dùng domain mặc định Vercel/Render có same-origin API proxy riêng để tránh mất cookie phiên.
- Thêm runbook phân rõ phần người vận hành và Codex; production secret không được đưa vào repository, command output hoặc chat.

## Verification evidence

| Gate | Kết quả |
|---|---|
| Frontend lint | PASS |
| Frontend TypeScript | PASS |
| Frontend production build | PASS — 23 routes |
| Vercel same-origin proxy build | PASS — `/api/v1/*` rewrite sang Render, standalone tắt trên Vercel |
| npm audit high | PASS — 0 vulnerability |
| Backend `clean verify` | PASS, gồm unit/integration/Testcontainers và `ExternalDatabaseUrlTest` |
| Valid production fixture | PASS |
| Cross-site Vercel/Render fixture | PASS — bị từ chối như thiết kế |
| Docker backend build + Render-style `DATABASE_URL` provisioning rehearsal | PASS — 1 Admin, 1 audit, idempotent |
| `git diff --check` | PASS |

GitHub Actions evidence sẽ được bổ sung sau khi commit được push và sáu CI jobs hoàn tất.

## Việc bên ngoài còn bắt buộc

1. Người có thẩm quyền tạo/duyệt Render Blueprint và chi phí trong dashboard.
2. Nhập CORS, Admin bootstrap và SMTP secrets trực tiếp vào Render.
3. Gắn `api.<domain>` vào Render, `app.<domain>` vào Vercel và hoàn tất DNS/TLS.
4. Deploy Vercel với `NEXT_PUBLIC_API_BASE_URL=https://api.<domain>/api/v1`.
5. Xóa bootstrap password sau lần đăng nhập đầu; giữ cả hai provisioning flags ở `false`.
6. Chạy staging verifier, SMTP provider test, dataset/write load, active authenticated DAST, backup/restore và UAT.

Phase 13 chưa thể đánh dấu PASS hoặc cho phép production go-live trước khi các mục trên có evidence và owner sign-off.
