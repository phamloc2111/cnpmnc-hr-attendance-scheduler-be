# Danh mục vị trí dùng chung

Danh mục dùng cho tài khoản nhân viên và mẫu ca trên toàn hệ thống.

- Quản lý: tạo vị trí, ngừng sử dụng hoặc sử dụng lại. Mọi quản lý cùng quản lý một danh mục chung.
- Quản trị: xem danh mục và chọn vị trí trong form tài khoản / mẫu ca; không tạo hoặc thay đổi danh mục.
- Nhân viên: không truy cập API danh mục.
- Không có thao tác xóa hoặc đổi tên. Ngừng sử dụng giữ nguyên nhân viên và mẫu ca đã có; không được gán mới.
- Tên không trùng kể cả khác hoa/thường, khoảng trắng thừa hay cách mã hóa Unicode tương đương. Vị trí ngừng sử dụng vẫn giữ tên; bật lại thay vì tạo trùng.

## Web

Quản lý mở **Vị trí** → nhập **Tên vị trí** → **Tạo vị trí**. Danh sách hỗ trợ tìm kiếm, lọc trạng thái và phân trang.

Quản trị hoặc quản lý tạo/sửa nhân viên: chọn **Vị trí** từ danh mục. Vị trí nhân viên vẫn có thể để trống. Mẫu ca bắt buộc chọn **Vị trí cần**. Khi chưa có vị trí, liên hệ quản lý tạo trước. Dropdown chỉ chứa vị trí đang dùng; khi sửa nhân viên, vị trí cũ đã ngừng sử dụng vẫn được giữ lại.

## API

Bearer JWT, prefix /api/v1.

| Method | Endpoint | Quyền |
| --- | --- | --- |
| GET | /positions?q=&active=true&page=0&size=20 | MANAGER, ADMIN |
| GET | /positions/{id} | MANAGER, ADMIN, kể cả vị trí ngừng sử dụng |
| POST | /positions | MANAGER |
| PATCH | /positions/{id}/status | MANAGER |

POST: `{"name":"Thu ngân"}`, trả 201 và `{id,name,active,version}`.

PATCH: `{"active":false,"version":0}`. Dùng version mới nhất để tránh ghi đè. Bật lại bằng active=true.

Danh sách trả `{items,page,size,totalElements,totalPages}`, page từ 0, size 1–100; bỏ active để xem mọi trạng thái.

Tài khoản POST/PUT gửi `positionId: UUID | null`. Không gửi chuỗi position; tên chỉ là trường đọc trong response. Mẫu ca POST gửi `requiredPositionId: UUID` thay cho requiredPosition. Server kiểm tra ID và trạng thái, lấy tên chính thức từ danh mục. Response giữ tên cũ để hiển thị và thêm ID.

Vị trí đang ngừng sử dụng chỉ được giữ trên chính tài khoản đã có vị trí đó. Gán vị trí đó cho tài khoản mới/khác hoặc mẫu ca mới trả 409 POSITION_INACTIVE. ID không tồn tại trả 404 POSITION_NOT_FOUND. Duplicate name trả 409 POSITION_EXISTS/POSITION_CONFLICT; version cũ trả 409 STALE_POSITION. Backend dùng khóa hàng để tuần tự hóa chọn vị trí với thay đổi trạng thái.

## Dữ liệu và kiểm thử

Khởi động lại backend để chạy migration Java V6. V1–V5 giữ nguyên. V6 gom tên vị trí cũ trong identity_accounts và work_shift_templates vào danh mục, liên kết bằng ID, giữ nhãn cũ và giữ vị trí chưa biết ở null. Vị trí nhập từ dữ liệu cũ có createdBy=null. Tên tương đương được dùng chung một ID.

`PositionsIntegrationTests.java` kiểm tra quyền, chuẩn hóa tên, trạng thái, dùng chung giữa quản lý/quản trị, liên kết tài khoản/mẫu ca và nâng cấp V5 → V6 trên PostgreSQL Testcontainers riêng.

```powershell
.\mvnw.cmd test
```
