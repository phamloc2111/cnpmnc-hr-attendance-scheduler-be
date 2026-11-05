# SS-15 — Quản lý tài khoản nhân viên

## Sử dụng trên web

Khởi động lại backend để Flyway chạy migration V3, sau đó chạy frontend bằng `npm run dev`.

- Đăng nhập **Quản trị** → **Tài khoản**: xem toàn bộ tài khoản, tạo nhân viên/quản lý/quản trị, sửa thông tin, đổi role, phân công quản lý, khóa/mở khóa.
- Đăng nhập **Quản lý** → **Nhân viên**: chỉ xem và quản lý nhân viên được phân công cho mình. Nhân viên do quản lý tạo được tự động gán cho người quản lý đó.
- Các nhân viên được tạo bằng bootstrap trước SS-15 chưa có người quản lý. Quản trị cần mở tài khoản, chọn **Sửa**, rồi gán **Người quản lý**.
- Khi tạo tài khoản, nhập email, họ tên, mật khẩu ban đầu. Chỉ role Nhân viên có vị trí, kỹ năng và người quản lý. Role Quản lý/Quản trị không hiển thị hoặc lưu vị trí/kỹ năng; khi đổi sang các role này, thông tin xếp ca được xóa. Mật khẩu ít nhất 12 ký tự, tối đa 72 byte UTF-8.
- Dùng bộ lọc theo tên/email, vai trò (quản trị) hoặc trạng thái. Danh sách phân trang ở server.
- Khi lưu lỗi trùng email hoặc dữ liệu đã thay đổi, form giữ nguyên để sửa. Nếu dữ liệu bị người khác cập nhật, đóng form và tải lại trang trước khi sửa tiếp.
- Khóa/mở khóa cần xác nhận. Khóa tài khoản thu hồi toàn bộ phiên; mở khóa yêu cầu người dùng đăng nhập lại, không khôi phục token cũ.

Không cần thêm nhân viên mới vào `.env`. Bootstrap chỉ dành cho tài khoản ban đầu. SS-15 không thêm đổi/reset mật khẩu hoặc xóa tài khoản.

## Quyền và giới hạn

| Thao tác | Nhân viên | Quản lý | Quản trị |
|---|---|---|---|
| Danh sách / xem chi tiết | Không | Nhân viên được giao | Toàn bộ |
| Tạo tài khoản | Không | EMPLOYEE, tự gán cho mình | Cả 3 role |
| Sửa họ tên/email/vị trí/kỹ năng | Không | Nhân viên được giao | Toàn bộ |
| Đổi role / gán quản lý | Không | Không | Có |
| Khóa / mở khóa | Không | Nhân viên được giao | Tài khoản khác |

Không thể tự khóa hoặc tự đổi role. Quản lý đang có nhân viên phải được chuyển hết nhân viên sang quản lý khác trước khi đổi role. Người được gán làm quản lý phải là MANAGER đang hoạt động; không được gán tài khoản làm quản lý của chính nó.

Email được chuẩn hóa chữ thường và không trùng. Kỹ năng phân tách bằng dấu phẩy, tối đa 20 kỹ năng, mỗi kỹ năng tối đa 80 ký tự; không trùng kể cả khác chữ hoa/chữ thường. Vị trí tối đa 120 ký tự.

Backend kiểm tra cả role lẫn người quản lý, không dựa vào việc ẩn menu trên web. UUID ngoài phạm vi của quản lý trả 404. Thay email hoặc role thu hồi các phiên hiện tại. Đổi quyền/khóa tài khoản được kiểm tra lại từ DB cho mỗi request.

## API

Prefix `/api/v1`, header `Authorization: Bearer <JWT>`.

| Method | Endpoint | Nội dung |
|---|---|---|
| GET | `/accounts?q=&role=&locked=&page=0&size=20` | Danh sách có tìm kiếm/lọc/phân trang |
| GET | `/accounts/{id}` | Chi tiết trong phạm vi được phép |
| POST | `/accounts` | Tạo tài khoản, trả 201 và Location |
| PUT | `/accounts/{id}` | Sửa thông tin, role, phân công |
| PATCH | `/accounts/{id}/lock` | Khóa/mở khóa |

`size` từ 1 đến 100; `page` bắt đầu từ 0. `role`: EMPLOYEE/MANAGER/ADMIN; `locked`: true/false. Quản lý không được truy vấn role khác EMPLOYEE.

Request tạo (dữ liệu minh họa; tự đặt mật khẩu):

```json
{
  "email": "employee@example.com",
  "password": "<mat-khau-it-nhat-12-ky-tu>",
  "displayName": "Nguyễn An",
  "role": "EMPLOYEE",
  "position": "Thu ngân",
  "skills": ["Thu ngân", "Phục vụ"],
  "managerId": null
}
```

Quản lý gửi `managerId: null` hoặc ID của chính mình; backend tự gán cho họ. Quản trị có thể để nhân viên chưa phân công hoặc chọn ID quản lý đang hoạt động.

Response tài khoản: `id, email, displayName, role, locked, position, skills, managerId, version`. Không trả mật khẩu hoặc hash.

Request PUT dùng các trường trên request tạo, **bỏ password** và thêm `version` lấy từ response hiện tại. Request khóa:

```json
{"locked": true, "version": 0}
```

Response danh sách:

```json
{"items": [], "page": 0, "size": 20, "totalElements": 0, "totalPages": 0}
```

`version` giúp ngăn ghi đè khi có nhiều người sửa. Version cũ trả 409 `STALE_ACCOUNT`. Các lỗi khác: 400 dữ liệu không hợp lệ; 401 chưa/hết phiên; 403 thiếu quyền; 404 không tìm thấy/trái phạm vi; 409 email trùng hoặc xung đột nghiệp vụ. Payload lỗi theo `ApiError` của SS-12.

## Database và kiểm thử

V3 bổ sung `position`, `manager_id`, `version` cho `identity_accounts` và bảng kỹ năng `identity_account_skills`. Tài khoản cũ được giữ nguyên và có version 0. Chưa có tự động phân công quản lý cho tài khoản cũ.

```powershell
cd D:\hr\be
.\mvnw.cmd test
cd ../fe
npm test
npm run build
```

Docker phải đang chạy để chạy `AccountManagementIntegrationTests`. Testcontainers khởi tạo PostgreSQL 16 Alpine riêng, chạy Flyway V1/V2/V3 và Hibernate validate, sau đó tự dọn container. Kết nối được cấp bằng `@ServiceConnection`, không dùng DB trong `.env` hay NeonDB thật. Surefire cấu hình Docker API 1.44 cho Docker Desktop hiện tại, chỉ áp dụng JVM kiểm thử.

Các test SS-12 cũ vẫn dùng H2 như trước. Kiểm thử SS-15 dùng PostgreSQL thật, kiểm tra phân quyền/phạm vi, tạo và đăng nhập tài khoản, BCrypt, khóa/mở khóa và thu hồi JWT, cập nhật role, kỹ năng, email trùng, validation, phân trang và xung đột version. Web dùng MSW để kiểm tra luồng qua HTTP client thật với API giả, không gọi backend thật. Không build hoặc thay đổi mobile trong SS-15.
