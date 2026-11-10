# SYNC-25 — Đăng ký ca tuần kế tiếp

Nhân viên chọn các **mẫu ca mong muốn cho cả tuần**, lưu và chỉnh sửa trong ngày Chủ nhật trước tuần đó, theo `Asia/Ho_Chi_Minh` (UTC+7). Mở lúc 00:00 Chủ nhật, đóng lúc 00:00 thứ Hai; toàn bộ phút 23:59 Chủ nhật vẫn được lưu. Ngoài khung giờ chỉ được xem. Thời gian máy chủ quyết định, không phụ thuộc đồng hồ thiết bị.

Đăng ký là nguyện vọng, **không tự tạo lịch phân ca**. Schema V10 lưu mẫu ca theo tuần, không lưu ngày cụ thể trong tuần. Chức năng mở lại cho từng nhân viên được bổ sung ở SYNC-26 (xem bên dưới).

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

## SYNC-26 — Tự khóa và mở lại theo nhân viên

Cổng tự khóa sau toàn bộ phút 23:59 Chủ nhật, tức thứ Hai 00:00 giờ Việt Nam. Backend kiểm tra thời gian ở mỗi lần lưu, không cần cron hay thay đổi cấu hình môi trường.

Quản lý đang hoạt động chỉ được xem và mở/khóa lại cho nhân viên mình phụ trách. EMPLOYEE và ADMIN không gọi được API quản lý này. Không cho mở sớm trước khi cửa sổ tự động đã đóng hoặc mở tài khoản đang bị khóa.

- `GET /api/v1/registrations/management?weekStart=2026-11-16&q=&page=0&size=20`: danh sách nhân viên mình phụ trách, phân trang ở DB; tìm theo tên/email, escape ký tự `%` và `_`. Không có nhân viên trả 200 với `items: []`. Bỏ weekStart mặc định tuần đang diễn ra theo giờ máy chủ (để xử lý tuần vừa bị khóa), khác mặc định tuần kế tiếp của màn nhân viên.
- Response: `weekStart`, `weekEnd`, `serverNow`, `opensAt`, `closesAt`, `items`, `page`, `size`, `totalElements`, `totalPages`.
- Mỗi item gồm `employeeId`, `displayName`, `accountLocked`, `selectionCount`, `state` (OPEN/CLOSED/REOPENED/NOT_OPEN/ACCOUNT_LOCKED), `reopenedAt`, `accessRevision`, `canReopen`, `canClose`.
- `PUT /api/v1/registrations/management/{employeeId}/access`, body:

```json
{
  "weekStart": "2026-11-16",
  "reopened": true,
  "accessRevision": "closed"
}
```

Dùng accessRevision từ GET gần nhất; giá trị là `closed` hoặc UUID lần mở hiện tại. Đặt reopened=false để khóa lại. Trả item mới; dữ liệu ca đã chọn được giữ nguyên. Mỗi lần mở sau một lần khóa nhận revision mới. Request stale trả 409 `STALE_REGISTRATION_ACCESS`; tải lại rồi quyết định, không tự ghi đè.

Quyền mở lại áp dụng **riêng nhân viên + tuần**, có hiệu lực sau cửa sổ tự động đến khi quản lý khóa lại. Có thể chọn một tuần cũ đã đóng; không tự mở tuần khác. Lưu người mở và thời điểm trong bảng V10 `registration_reopens`; không thay đổi migration đã triển khai. Quyền cũ không được dùng khi nhân viên đang thuộc quản lý khác với người đã mở.

Employee GET/PUT thêm `reopened: boolean`. `editable` true ngoài cửa sổ tự động chỉ khi đúng nhân viên có quyền mở lại của quản lý hiện tại. Khi quản lý khóa lại, request lưu tiếp theo trả 409 `REGISTRATION_CLOSED`. Mở/khóa và lưu đều khóa bản ghi nhân viên trong transaction: thao tác lưu đã lấy khóa trước có thể hoàn tất trước khi khóa lại; sau khi khóa lại hoàn tất không còn lưu được.

Lỗi bổ sung: 403 `REGISTRATION_EMPLOYEE_FORBIDDEN`; 409 `REGISTRATION_NOT_CLOSED`, `REGISTRATION_EMPLOYEE_LOCKED`, `STALE_REGISTRATION_ACCESS`. Tài khoản/phiên bị thu hồi trả 401 qua security chung.

Kiểm thử SYNC-26 bao gồm cách ly theo nhân viên và tuần, phân quyền quản lý, boundary thứ Hai, stale revision, tài khoản khóa/chuyển quản lý, danh sách phân trang, mở lại đồng thời và race khóa/lưu.
