# Cấu hình điểm làm việc

Quản lý hoặc quản trị mở **Điểm làm việc** trong menu web. Cấu hình dùng chung cho toàn hệ thống hiện tại. Chỉ lấy vị trí hiện tại khi đang ở nơi nhân viên làm việc; cũng có thể nhập vĩ độ/kinh độ và mở bản đồ kiểm tra trước khi lưu.

- Bán kính từ **10–5000m**, mặc định 100m.
- Dung sai GPS từ **20–50m**, mặc định **30m**.
- Khoảng cách điện thoại và tọa độ trên ảnh tới tâm điểm làm việc phải không vượt **bán kính + dung sai**. Ví dụ 100 + 30 = 130m.
- Sai số vị trí do trình duyệt báo phải không vượt dung sai đã cấu hình. GPS ±80m không được dùng để nới vùng điểm danh; người dùng được yêu cầu bật vị trí chính xác và lấy lại GPS.
- Vị trí phải mới như quy tắc điểm danh hiện có. Dung sai khoảng cách không thay thế việc kiểm tra thời gian ảnh, quyền truy cập hoặc chống gửi trùng.

`GET /api/v1/workplace-settings` và `PUT /api/v1/workplace-settings` chỉ dành cho MANAGER/ADMIN. Khi chưa cấu hình, GET trả 200 với `configured:false`, tọa độ null, bán kính/dung sai mặc định và version 0. Nhân viên không được đọc/ghi cấu hình qua API này.

PUT nhận `latitude`, `longitude`, `radiusMeters`, `gpsToleranceMeters`, `version`. Lần đầu version 0; các lần sau dùng version nhận từ server. Cập nhật cũ trả 409 `STALE_WORKPLACE`, giữ nguyên cấu hình mới hơn. Giao diện giữ dữ liệu đang nhập và cho tải lại.

Flyway **V10** tạo bảng `organization_workplace_settings`, tối đa một cấu hình. Cấu hình được lưu trong PostgreSQL cùng người cập nhật và giờ UTC; áp dụng ngay cho lượt vào/ra tiếp theo, không cần khởi động lại backend. Migration không tự tạo tọa độ giả và không sửa dữ liệu điểm danh cũ.

Ưu tiên cấu hình đã lưu trên web. Nếu chưa có bản ghi, backend vẫn hỗ trợ cấu hình môi trường hợp lệ `WORKPLACE_LATITUDE`, `WORKPLACE_LONGITUDE`, `WORKPLACE_RADIUS_METERS`, `WORKPLACE_GPS_TOLERANCE_METERS` (mặc định 30). Nếu cả hai nguồn đều chưa có tọa độ, điểm danh vẫn trả `WORKPLACE_NOT_CONFIGURED` cho tới khi quản lý thiết lập.

Triển khai backend mới để Flyway chạy V10, triển khai frontend, sau đó quản lý nhập tọa độ thật và bấm **Lưu cấu hình**. Không tự thay `.env` hoặc tự suy đoán vị trí nơi làm việc.

Camera và Geolocation cần HTTPS hoặc localhost cùng quyền trình duyệt. Tọa độ và độ chính xác vẫn là dữ liệu do thiết bị gửi; cấu hình bán kính không phải cơ chế chứng thực độc lập chống giả GPS.
