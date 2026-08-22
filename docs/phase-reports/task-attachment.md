# TASK ATTACHMENT IMPLEMENTATION REPORT

Ngày đánh giá: 2026-08-22

## Status

**PASS**

## Implemented

- Admin có thể đính kèm nhiều tệp khi tạo Task, thêm tệp vào Task đã có, tải xuống và xóa tệp.
- Người được giao Task có thể xem và tải tệp; User khác bị từ chối kể cả khi biết trực tiếp attachment ID.
- Upload từng tệp độc lập sau khi tạo Task; lỗi một tệp không rollback Task và UI hỗ trợ thử lại riêng tệp lỗi.
- Giới hạn mặc định có thể cấu hình: 10 tệp/Task, 20 MiB/tệp và 100 MiB/Task.

## Database

- Flyway V7 tạo `task_attachments`, khóa ngoại tới `tasks`/`users`, storage key duy nhất, checksum SHA-256, timestamp soft-delete và partial index cho tệp còn hiệu lực.
- Binary không được lưu trong PostgreSQL.
- Migration sạch V1→V7 đã PASS trên PostgreSQL 18 qua Testcontainers và full E2E.

## Storage

- Thêm abstraction `FileStorage` và implementation `LocalFileStorage`; business service không phụ thuộc filesystem hay SDK object storage cụ thể.
- Storage key do server tạo theo `tasks/{taskId}/{attachmentId}.{extension}`; original filename chỉ dùng làm metadata hiển thị/download.
- Upload dùng temporary file và atomic move; metadata failure gọi best-effort cleanup để tránh orphan.
- Docker production chạy non-root, có thư mục upload và persistent volume/disk; E2E dùng storage tạm cô lập.

## API

- `POST /api/v1/tasks/{taskId}/attachments`
- `GET /api/v1/tasks/{taskId}/attachments`
- `GET /api/v1/task-attachments/{attachmentId}/download`
- `DELETE /api/v1/task-attachments/{attachmentId}`
- Download trả Content-Type, Content-Length, `Content-Disposition` UTF-8 và `X-Content-Type-Options: nosniff`.

## Frontend

- Form giao nhiệm vụ có picker/kéo-thả, kiểm tra sớm số lượng/kích thước/extension và cho phép bỏ tệp trước upload.
- Mỗi tệp có trạng thái `PENDING`, `UPLOADING`, `SUCCESS`, `ERROR`; hỗ trợ partial success và retry không tạo lại Task.
- Task card tải danh sách attachment theo nhu cầu để tránh N+1; User chỉ có download, Admin có upload/delete.
- Production image đã bổ sung thư mục `public`, bảo đảm logo trường có mặt khi chạy standalone.

## Authorization

- Upload/delete: chỉ Admin.
- List/download: Admin hoặc đúng Task assignee.
- Unauthenticated bị 401; authenticated User không sở hữu Task bị 403; inactive session tiếp tục tuân theo session validation hiện tại.

## Security

- Whitelist extension, declared MIME và magic bytes cho PDF, Office legacy/OOXML, TXT/CSV, JPEG, PNG và ZIP.
- Chặn executable/script, MIME mismatch, file rỗng, path traversal, separator bất thường và filename điều khiển; filename được normalize Unicode NFC.
- Không expose storage URL/key ra API, không dùng original filename làm path, không log binary.
- Audit `TASK_ATTACHMENT_ADDED` và `TASK_ATTACHMENT_REMOVED` chỉ lưu metadata.
- Không phát notification theo từng lần upload/xóa nên không tạo spam.

## Tests

- Unit: **PASS — 20 tests**, gồm validator, local storage, cleanup khi metadata thất bại và các regression test hiện hữu.
- Integration/security: **PASS — 15 tests**, gồm migration, valid DOCX/Unicode, size/count/total limits, CSRF, unauthorized upload/delete, assignee/Admin download, IDOR 403, audit và orphan cleanup.
- Frontend: **PASS — 4 tests** cho validation file phía client.
- E2E: **PASS — 12 passed, 2 intentionally skipped** trên stack Docker production-like; critical attachment chain chạy một lần trên Chromium desktop, hai ca business-chain mobile được skip có chủ đích để tránh lặp dữ liệu.

## Build

- Backend: **PASS** — `mvn clean verify`.
- Frontend: **PASS** — lint, typecheck và Next.js production build (23 routes).
- Docker: **PASS** — backend/frontend images build và health checks thành công trong full E2E.

## Documentation updated

- `requirements.md`
- `use-cases.md`
- `business-rules.md`
- `architecture.md`
- `database-design.md`
- `api-design.md`
- `security-design.md`
- `testing-strategy.md`
- `README.md`, environment examples và Render/Vercel deployment runbook

## Known limitations

- Local filesystem là implementation production hiện tại; Render cần persistent disk. Chưa có S3-compatible implementation.
- Task notification được tạo ngay khi Task được giao, trước các upload độc lập, nên chưa kèm tổng số attachment. Việc thêm/xóa tệp sau đó không phát notification tổng hợp.
- Chưa có malware scanner; kiểm tra hiện tại là extension + MIME + magic bytes và integrity checksum.
- Xóa storage trước rồi soft-delete metadata; không có distributed transaction giữa storage và PostgreSQL. Storage failure trả lỗi rõ ràng và không đánh dấu metadata đã xóa.

## Future

- TaskSubmission
- Malware scanning/quarantine
- Signed URLs
- File versioning
- S3/R2/MinIO/B2 storage adapter

## Ready for next phase

**YES**
