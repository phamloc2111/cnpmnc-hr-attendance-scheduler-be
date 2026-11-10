# SYNC-25 — Đăng ký ca tuần kế tiếp

Nhân viên chọn các **mẫu ca mong muốn cho cả tuần**, lưu và chỉnh sửa trong ngày Chủ nhật trước tuần đó, theo `Asia/Ho_Chi_Minh` (UTC+7). Mở lúc 00:00 Chủ nhật, đóng lúc 00:00 thứ Hai; toàn bộ phút 23:59 Chủ nhật vẫn được lưu. Ngoài khung giờ chỉ được xem. Thời gian máy chủ quyết định, không phụ thuộc đồng hồ thiết bị.

Đăng ký là nguyện vọng, **không tự tạo lịch phân ca**. Schema V10 lưu mẫu ca theo tuần, không lưu ngày cụ thể trong tuần. Chức năng mở lại cho từng nhân viên thuộc SYNC-26, chưa bao gồm trong SYNC-25.

## API (Bearer token, chỉ EMPLOYEE)

- `GET /api/v1/registrations/me`: đăng ký tuần kế tiếp, tính theo giờ máy chủ.
- `GET /api/v1/registrations/me?weekStart=2026-11-16`: xem tuần khác; `weekStart` phải là thứ Hai.
- `GET /api/v1/registrations/shifts?page=0&size=20`: danh mục mẫu ca, tối đa 100 mỗi trang, danh sách trống trả 200.
- `PUT /api/v1/registrations/me`: lưu toàn bộ lựa chọn; mảng rỗng xóa lựa chọn.

```json
{
  "weekStart": "2026-11-16",
  "revision": "<revision trả từ GET gần nhất>",
  "shiftIds": ["<UUID mẫu ca>"]
}
```

Response tuần gồm `weekStart`, `weekEnd`, `serverNow`, `opensAt`, `closesAt`, `editable`, `revision`, `shifts`. `shifts` chứa toàn bộ ca đã chọn, kể cả ca ngoài trang danh mục hiện tại. Tối đa 100 mẫu ca, không được trùng hoặc chứa ID không tồn tại. Danh tính luôn lấy từ JWT; không nhận employeeId từ client.

`revision` là ETag nội dung SHA-256 của tập ID đã chọn. Backend khóa bản ghi nhân viên trong transaction trước khi so sánh và thay thế, kể cả lần lưu đầu tiên. Hai bản chọn khác nhau từ cùng revision: một thành công, một 409; nội dung trùng nhau không tạo lịch sử phiên bản mới.

- 400: `INVALID_REGISTRATION`, `INVALID_REGISTRATION_WEEK`, `DUPLICATE_SHIFT`, `INVALID_REGISTRATION_SHIFT`.
- 409: `REGISTRATION_CLOSED`, `STALE_REGISTRATION`, `REGISTRATION_CHANGED`. Tải lại để lấy revision mới; không tự ghi đè bản đăng ký mới hơn.
- 401: chưa đăng nhập hoặc tài khoản/phiên bị thu hồi; 403: sai vai trò.

Module registration dùng public `IdentityApi` và `ShiftCatalogApi`; không truy cập repository nội bộ của module khác. Dùng lại bảng V10, không sửa migration đã áp dụng, không cần repair cơ sở dữ liệu.

## Kiểm tra

`./mvnw -Dtest=RegistrationWindowTests,RegistrationIntegrationTests test` kiểm tra ranh giới Chủ nhật theo UTC+7, lưu/sửa/xóa, tuần cũ, phân quyền và sở hữu, mẫu ca không hợp lệ, phân trang, hai request lưu đồng thời. Integration tests dùng PostgreSQL Testcontainers cô lập, không dùng dữ liệu thật.
