# Điểm danh bằng ảnh Timemark và phân loại kết quả

Phạm vi: SYNC-19 (US09) và SYNC-22 (US12), trên backend và web. Không thay đổi ứng dụng mobile.

## Sử dụng trên web

- Nhân viên mở **Chấm công**, chọn ca trong tuần, chọn ảnh Timemark JPEG/PNG từ thư viện hoặc chụp mới, rồi bấm **Điểm danh vào** hoặc **Điểm danh ra**. Lượt ra chỉ xuất hiện sau lượt vào. Trình duyệt lấy tọa độ mới tại lúc bấm gửi (`maximumAge: 0`); nếu từ chối quyền, không gọi API và hiển thị lý do.
- Quản lý mở **Bảng công** để xem các ca của nhân viên hiện thuộc quyền quản lý. Quản trị viên xem toàn bộ. Bảng tự tải lại mỗi phút và có nút tải lại.
- Trình duyệt cần HTTPS hoặc `localhost` để dùng Geolocation. Chọn ảnh có sẵn được phép; vị trí ghi nhận là vị trí của thiết bị **lúc gửi**, không phải vị trí nằm trong ảnh.

## Quy tắc ghi nhận

- Một ca tối đa một lượt `CHECK_IN` và một lượt `CHECK_OUT`. Lượt ra cần lượt vào; không nhận lượt vào sau khi ca kết thúc. Backend kiểm tra tài khoản nhân viên đang hoạt động và ca thuộc chính nhân viên đó. Gửi trùng trả `409`.
- Ảnh JPEG/PNG tối đa 5 MB, lưu riêng tư trong PostgreSQL `BYTEA`. API ảnh yêu cầu JWT; nhân viên chỉ xem ảnh của mình, quản lý chỉ xem nhân viên hiện thuộc quyền, quản trị viên xem toàn bộ. Không cache ảnh.
- Request bắt buộc có `latitude`, `longitude`, `accuracyMeters`, `locatedAt` từ vị trí mới. Vị trí quá 2 phút hoặc ở tương lai quá 30 giây bị từ chối. Thời điểm dùng để phân loại là `receivedAt` do máy chủ ghi, không tin giờ trong ảnh hoặc đồng hồ trình duyệt.
- Ca có điểm danh không thể bị hủy (`409 SCHEDULE_HAS_ATTENDANCE`) để giữ lịch sử chứng cứ.

## Phân loại

Ngưỡng mặc định `ATTENDANCE_GRACE_MINUTES=5`, áp dụng cho cả trễ và về sớm. Đúng tại mốc 5 phút vẫn được coi là đúng giờ. Phút vi phạm được làm tròn lên.

| Điều kiện | Kết quả |
| --- | --- |
| Chưa có lượt vào, ca chưa kết thúc | Chờ điểm danh |
| Chưa có lượt vào, ca đã kết thúc | Vắng |
| Vào không quá 5 phút sau giờ bắt đầu | Đúng giờ ở cột vào |
| Vào quá 5 phút sau giờ bắt đầu | Trễ, kèm số phút |
| Ra sớm hơn giờ kết thúc quá 5 phút | Về sớm, kèm số phút |
| Có lượt vào nhưng chưa có lượt ra sau khi ca kết thúc | Thiếu lượt ra; cột vào vẫn giữ đúng giờ hoặc trễ |
| Vừa trễ vừa về sớm | Trễ và về sớm |

Kết quả được tính lại khi đọc, vì vậy trạng thái **Vắng** xuất hiện ngay khi ca kết thúc dù không có tác vụ nền. Ca qua đêm dùng `startsAt`/`endsAt` UTC đã lưu, không so giờ theo ngày trong trình duyệt.

## API

Tất cả endpoint dưới `/api/v1`, yêu cầu Bearer JWT.

| Method | Path | Vai trò |
| --- | --- | --- |
| `POST` | `/attendance/events` | Nhân viên; `multipart/form-data` gồm `scheduleId`, `type` (`CHECK_IN`/`CHECK_OUT`), `photo`, `latitude`, `longitude`, `accuracyMeters`, `locatedAt`; trả `201` |
| `GET` | `/attendance/events?from=YYYY-MM-DD&to=YYYY-MM-DD` | Nhân viên; lượt của mình theo ngày bắt đầu ca |
| `GET` | `/attendance/events/{id}/photo` | Người có quyền xem ảnh |
| `GET` | `/attendance/results?from=YYYY-MM-DD&to=YYYY-MM-DD&page=0&size=20` | Quản lý/quản trị; có thể lọc `employeeId` trong phạm vi quyền |

Khoảng ngày tối đa 31 ngày, bao gồm hai đầu. Kết quả bảng công phân trang và trả `arrival`, `departure`, `overall`, `lateMinutes`, `earlyMinutes` cùng giờ vào/ra.

## Tích hợp SYNC-24

`GET /api/v1/attendance-records` và `GET /api/v1/attendance-records/{id}` giữ hợp đồng xem từng lượt của SYNC-24. Khi nhân viên gửi ảnh qua `/attendance/events`, cùng transaction sẽ tạo bản ghi xem kết quả với cùng ID, ảnh riêng tư và tọa độ GPS. Trạng thái lượt vào/ra lấy từ giờ máy chủ và ngưỡng `ATTENDANCE_GRACE_MINUTES`; vị trí/giờ trên ảnh để trống hoặc ghi rõ chưa xác minh cho đến SYNC-20/21. `POST /attendance-records` chỉ dành cho quản lý/quản trị để nhập bản ghi thủ công, không thay thế luồng tải ảnh của nhân viên.

Flyway V8 tạo `attendance_records` và nhập các lượt đã có trong `attendance_events`. Phần nhập dữ liệu cũ dùng ngưỡng mặc định 5 phút. V7 của nhánh `dev` (`attendance_events`) được giữ nguyên. Nếu một môi trường đã chạy V7 khác từ `main` (`attendance_records`), cần kiểm tra lịch sử Flyway và kế hoạch di chuyển dữ liệu trước khi triển khai bản hòa giải; không chạy `repair` tự động.

`photoUrl` của bản ghi tự động trỏ tới API ảnh yêu cầu Bearer JWT. Web cần tải ảnh với token rồi dùng blob URL; thẻ `<img>` gọi trực tiếp URL sẽ không mang token.

## Giới hạn hiện tại

Ảnh được lưu và gắn vị trí lúc gửi, nhưng chưa đọc giờ/vị trí từ nội dung ảnh và chưa so khớp với GPS. Đó là phạm vi SYNC-20 và SYNC-21. GPS của trình duyệt là dữ liệu do client gửi, chưa có cơ chế chứng thực độc lập chống giả mạo. Trạng thái được tính từ thời điểm máy chủ nhận ảnh nên chỉ nên dùng làm kết quả sơ bộ trước bước xác minh ảnh.

Flyway V7 tạo bảng `attendance_events`. Kiểm thử chạy với PostgreSQL Testcontainers qua `mvn test`; web dùng `npm test` và `npm run build`.
