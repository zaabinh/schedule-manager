# Staging Validation Runbook

Chạy các gate này trên staging HTTPS có topology, dữ liệu và integrations gần production. Không đưa credential vào command line hoặc commit vào repository.

## 1. Public endpoint và authenticated session smoke

```powershell
$env:STAGING_WEB_ORIGIN="https://schedule-staging.example.edu.vn"
$env:STAGING_API_ORIGIN="https://api.schedule-staging.example.edu.vn"
$env:STAGING_ADMIN_EMAIL="release-admin@example.edu.vn"
$env:STAGING_ADMIN_PASSWORD="<read-from-secret-manager>"
npm.cmd run release:verify-staging
Remove-Item Env:STAGING_ADMIN_PASSWORD
```

Trên Linux dùng cùng bốn environment variables và chạy `npm run release:verify-staging`, sau đó `unset STAGING_ADMIN_PASSWORD`.

Verifier chỉ thực hiện readiness, tải trang login, đăng nhập, `/auth/me` và logout để thu hồi session; không mutate dữ liệu nghiệp vụ và không in credential. Gate PASS yêu cầu:

- HTTPS readiness `UP` và frontend 200;
- nonce CSP, anti-frame/nosniff, HSTS và không có `X-Powered-By`;
- session cookie `__Host-`, `Secure`, `HttpOnly`, `SameSite=Lax`;
- CSRF token và identity trả về đúng tài khoản kiểm thử.

Lưu JSON output cùng release evidence. Script tự logout; tài khoản release vẫn phải được disable/xóa sau đợt xác minh theo chính sách nhà trường.

## 2. Các gate bắt buộc tiếp theo

1. Seed dataset đã ẩn danh với quy mô lớp, giáo viên, kế hoạch, task, notification và conversation đại diện thực tế.
2. Chạy k6 read/write scenario với tài nguyên production-like; lưu thresholds, percentile và error breakdown.
3. Chạy active authenticated DAST qua HTTPS; kiểm tra thủ công IDOR giữa Admin/User, CSRF thiếu/sai token và CORS origin lạ.
4. Gửi plan notification và due reminder qua SMTP provider thật; lưu message ID/delivery log, không lưu nội dung nhạy cảm.
5. Backup vào object storage, restore sang database staging rỗng và ký RPO/RTO thực đo.
6. UAT bằng tài khoản Hiệu trưởng và giáo viên; duyệt file Excel mẫu và xác nhận không còn Sev-1/2.
7. Security/data owners ký quyết định MFA Admin, retention theo loại dữ liệu và data residency hoặc ghi risk acceptance có thời hạn.

Mọi evidence phải ghi release tag/image digest, timestamp, environment, người thực hiện và kết quả PASS/FAIL. Không go-live nếu bất kỳ gate nào thiếu bằng chứng hoặc chưa có owner ký.
