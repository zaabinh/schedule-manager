# Phase 8 Report — Notifications & Email

Ngày hoàn thành: 2026-08-19  
Trạng thái: **PASS**  
Phase kế tiếp: **Phase 9 — Reminders**

## Đã hoàn thành

- Notification và recipient được tách riêng; feed, unread count, mark-one và mark-all chỉ thao tác trên recipient hiện tại.
- Worker claim outbox bằng `FOR UPDATE SKIP LOCKED`, có lease recovery, deduplication key và retry giới hạn; lỗi xử lý không rollback nghiệp vụ đã commit.
- Publish/update WeeklyPlan và Task assign/update được fan-out tới đúng tập recipient; retry không tạo notification trùng.
- Email có provider `log` cho local/test và SMTP thật cho staging/production, chọn bằng `EMAIL_PROVIDER`.
- Metrics `schedule.outbox.processed` và `schedule.outbox.failed` có tag loại event.
- Notification Center của Admin/User và popover đã chuyển từ mock sang REST.

## Verification

| Gate | Kết quả |
|---|---|
| Frontend lint | PASS — 0 warning/error |
| Frontend typecheck | PASS |
| Frontend production build | PASS — 23 routes |
| Backend focused clean verify | PASS — 10 tests, 0 failure/error |
| PostgreSQL integration | PASS — PostgreSQL 18 Testcontainers |

Integration coverage gồm fan-out, unread, read one/all idempotent, notification IDOR trả 404, outbox dedup, bounded retry tới `FAILED`, và xác nhận Task đã commit không bị rollback khi message lỗi.

## Cấu hình vận hành

- Local/test: `EMAIL_PROVIDER=log`.
- Staging/production: `EMAIL_PROVIDER=smtp`, cùng `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `SMTP_AUTH`, `SMTP_STARTTLS`, `EMAIL_FROM`.
- Production phải giám sát counter failed và cảnh báo khi outbox có bản ghi `FAILED` hoặc `PROCESSING` quá lease.

## Technical debt chuyển Phase 12

- Bổ sung dashboard/alert backend cho outbox backlog và kiểm thử provider timeout trên staging.
- Kiểm thử tải feed với dataset production-sized và hoàn thiện cursor pagination theo hợp đồng API.
