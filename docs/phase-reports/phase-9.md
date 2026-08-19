# Phase 9 Report — Reminders

Ngày hoàn thành: 2026-08-19  
Trạng thái: **PASS**  
Phase kế tiếp: **Phase 10 — Conversations**

## Đã hoàn thành

- User tạo reminder cá nhân cho Event bằng preset 15/30 phút, 1 giờ, 1 ngày hoặc thời điểm custom; Event thiếu datetime bắt buộc custom.
- Admin tạo reminder chung cho toàn bộ User active hoặc tập recipient chỉ định; source `ADMIN` và `USER` là các row độc lập.
- User chỉ xem reminder của mình và không thể hủy reminder source Admin; cancel có state guard và audit.
- Worker claim bằng `FOR UPDATE SKIP LOCKED`, lease recovery, bounded retry, trạng thái `SENT/FAILED` và metrics sent/failed.
- Saturday scheduler chạy 08:00/17:00 theo `Asia/Ho_Chi_Minh`, dùng `scheduler_job_runs` chống lặp và bỏ qua khi tuần kế tiếp đã publish.
- Dialog Event và trang Reminder đã chuyển từ mock sang REST thật.

## Verification

| Gate | Kết quả |
|---|---|
| Frontend lint/typecheck/build | PASS — 23 routes |
| Backend clean verify | PASS — 11 tests, 0 failure/error |
| PostgreSQL integration | PASS — PostgreSQL 18 Testcontainers |
| Fixed Clock | PASS — không phụ thuộc wall clock |

CF-10/11 kiểm chứng preset/custom, ownership/IDOR, ADMIN reminder immutable với User, delivery không lặp ở lượt kế, Saturday job idempotent và nhánh `SKIPPED/SUCCEEDED`.

## Cấu hình vận hành

- `SCHOOL_TIME_ZONE=Asia/Ho_Chi_Minh`
- `REMINDER_POLL_DELAY_MS=60000`
- `REMINDER_MAX_ATTEMPTS=3`
- SMTP dùng cấu hình Phase 8.

## Technical debt chuyển Phase 12

- Chạy concurrency/load test nhiều worker với dataset production-sized.
- Cảnh báo reminder `FAILED` và lease quá hạn trên monitoring backend.
