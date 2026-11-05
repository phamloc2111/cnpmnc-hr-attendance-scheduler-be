# SS-12 / SS-13 — Đăng nhập và phân quyền

Module `identity`, dùng chung cho web và mobile. Ba role duy nhất: `EMPLOYEE`, `MANAGER`, `ADMIN`.

## Khởi chạy và cấp tài khoản lần đầu

1. Cấu hình PostgreSQL qua `be/.env` như README của dự án.
2. Đặt `JWT_SECRET` thành chuỗi ngẫu nhiên ít nhất 32 byte. Có thể sinh bằng PowerShell:

   ```powershell
   $taskJwtBytes = New-Object byte[] 48
   $taskJwtRng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
   try { $taskJwtRng.GetBytes($taskJwtBytes) } finally { $taskJwtRng.Dispose() }
   [Convert]::ToBase64String($taskJwtBytes)
   ```

   Lưu kết quả vào `JWT_SECRET` trong `.env`, không commit. Khi chưa đặt, ứng dụng dùng khóa tạm ngẫu nhiên và JWT mất hiệu lực sau mỗi lần khởi động lại. Môi trường triển khai phải đặt khóa cố định, bảo mật.

3. Để cấp tài khoản ban đầu, thêm các biến sau vào `.env` bằng thông tin do bạn chọn:

   ```dotenv
   BOOTSTRAP_ENABLED=true
   BOOTSTRAP_ADMIN_EMAIL=<email-quan-tri>
   BOOTSTRAP_ADMIN_PASSWORD=<mat-khau-do-ban-chon>
   ```

   Mật khẩu phải có ít nhất 12 ký tự, tối đa 72 byte UTF-8. Tài khoản được băm BCrypt trước khi lưu. Không có tài khoản/mật khẩu mặc định.

   Có thể cấp thêm tài khoản kiểm thử bằng `BOOTSTRAP_MANAGER_EMAIL`, `BOOTSTRAP_MANAGER_PASSWORD`, `BOOTSTRAP_EMPLOYEE_EMAIL`, `BOOTSTRAP_EMPLOYEE_PASSWORD`. Mỗi role dùng email khác nhau. Các cặp trống được bỏ qua, email đã tồn tại giữ nguyên tài khoản, mật khẩu và role hiện tại.

4. Chạy `./mvnw.cmd spring-boot:run` từ `be/`. Flyway tự thêm hai bảng `identity_accounts`, `identity_sessions` qua V2.
5. Sau khi cấp xong, đặt `BOOTSTRAP_ENABLED=false` và xóa các biến mật khẩu bootstrap khỏi `.env`.

Đây là cơ chế cấp tài khoản ban đầu. Để tạo/sửa/khóa tài khoản trực tiếp trên web, xem [SS-15 — Quản lý tài khoản](ACCOUNTS.md).

## API

Prefix `/api/v1`. Gửi `Content-Type: application/json` khi đăng nhập.

| Method | Path | Quyền | Kết quả |
|---|---|---|---|
| POST | `/auth/login` | Công khai | JWT và người dùng |
| GET | `/auth/me` | Đã đăng nhập | Người dùng hiện tại từ DB |
| POST | `/auth/logout` | Đã đăng nhập | 204, thu hồi phiên hiện tại |
| GET | `/workspaces/employee` | EMPLOYEE | Nội dung trang đầu nhân viên |
| GET | `/workspaces/management` | MANAGER, ADMIN | Nội dung trang đầu quản lý |
| GET | `/workspaces/administration` | ADMIN | Nội dung trang đầu quản trị |

Request đăng nhập:

```json
{"email":"user@example.com","password":"your-password"}
```

Response 200 (giá trị minh họa):

```json
{
  "accessToken": "<signed-jwt>",
  "tokenType": "Bearer",
  "expiresAt": "2026-11-05T00:00:00Z",
  "user": {
    "id": "<uuid>",
    "email": "user@example.com",
    "displayName": "Quản lý",
    "role": "MANAGER"
  }
}
```

API bảo vệ yêu cầu header `Authorization: Bearer <accessToken>`. JWT ký HS256, chứa `sub` là UUID tài khoản, `jti` là UUID phiên, `role`, `iss`, `iat`, `exp`. Hạn mặc định 8 giờ, cấu hình bằng `app.auth.token-ttl`; hết hạn phải đăng nhập lại, chưa có refresh token.

Sai email hoặc mật khẩu trả 401 cùng thông báo `Email hoặc mật khẩu không đúng`. Tài khoản bị khóa, sau khi xác minh đúng mật khẩu, trả 403 và `Tài khoản đã bị khóa`. Thiếu/sai/hết hạn token trả 401. Có phiên hợp lệ nhưng thiếu quyền trả 403. Payload lỗi:

```json
{"code":"FORBIDDEN","message":"Bạn không có quyền truy cập chức năng này","timestamp":"2026-11-04T13:00:00Z","path":"/api/v1/workspaces/administration"}
```

Mỗi request xác minh chữ ký, hạn JWT, phiên còn hiệu lực, tài khoản chưa bị khóa và role trong JWT vẫn khớp DB. Đăng xuất thu hồi riêng phiên hiện tại; các phiên khác không bị ảnh hưởng. Khóa tài khoản hoặc đổi role làm token cũ không còn dùng được. Các API nghiệp vụ bổ sung sau này phải khai báo quyền ở backend, và giới hạn dữ liệu theo phạm vi phụ trách khi nghiệp vụ đó được triển khai.

## Web và mobile

- Web: `cd fe`, `npm install`, `npm run dev`. `VITE_API_BASE_URL` mặc định `http://localhost:8080` (không có `/api/v1`). Token lưu `sessionStorage`, khôi phục sau reload trong cùng tab và xác minh lại bằng `/auth/me`. Đăng xuất xóa token và về đăng nhập.
- Mobile: `cd mobile`, `flutter pub get`, `flutter run --dart-define=API_BASE_URL=http://10.0.2.2:8080/api/v1` cho emulator Android. iOS simulator dùng `http://localhost:8080/api/v1`; thiết bị thật dùng địa chỉ backend mà thiết bị truy cập được. Token lưu bằng `flutter_secure_storage`. Bản Android debug cho phép HTTP local; bản triển khai dùng HTTPS. iOS local HTTP cần cấu hình ATS phù hợp trong môi trường phát triển hoặc dùng HTTPS.
- Mỗi role vào trang đầu riêng; ADMIN được mở thêm không gian quản lý. Menu tài khoản/cài đặt chỉ hiện cho ADMIN. Các chức năng chưa triển khai được đánh dấu `Sắp có` và vô hiệu hóa.
- Khi backend trả 401, ứng dụng xóa phiên và về đăng nhập. Khi mất mạng lúc khôi phục phiên, ứng dụng giữ token để thử lại. Khi đăng xuất lúc mất mạng, ứng dụng xóa token cục bộ và thông báo chưa xác nhận thu hồi trên máy chủ; JWT còn hiệu lực ở máy chủ cho đến khi hết hạn.

## Kiểm thử

```powershell
cd be
.\mvnw.cmd test
cd ../fe
npm test
npm run build
cd ../mobile
flutter analyze
flutter test
```

Backend test dùng H2 ở chế độ PostgreSQL, chạy Flyway V1/V2 và validate entity; không kết nối PostgreSQL/NeonDB thật. Kiểm thử bao gồm ma trận quyền ba role, sai mật khẩu, tài khoản bị khóa, token hết hạn/bị sửa/bị thu hồi, thay đổi role, CORS và đăng xuất từng phiên. H2 chưa thay thế kiểm thử migration trên PostgreSQL thật.
