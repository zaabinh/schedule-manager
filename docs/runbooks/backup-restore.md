# Backup & Restore Runbook

- Daily backup dùng PostgreSQL custom format qua `scripts/backup-production.ps1`; chuyển bản sao sang object storage mã hóa với retention theo chính sách trường.
- Mỗi file phải có checksum, timestamp, release/schema version và kết quả job.
- RPO mục tiêu 24 giờ, RTO mục tiêu 4 giờ.
- Diễn tập restore ít nhất hàng quý trên database cô lập, chạy Flyway validate và smoke CF-01/05/09 sau restore.
- Script restore yêu cầu tên file an toàn và xác nhận chính xác database đang chạy vì `pg_restore --clean` là thao tác phá hủy dữ liệu hiện tại.
