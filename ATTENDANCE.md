# Điểm danh bằng ảnh Timemark và phân loại kết quả

Phạm vi: SYNC-19 (US09) và SYNC-22 (US12), trên backend và web. Không thay đổi ứng dụng mobile.

## Sử dụng trên web

- Nhân viên bấm **Điểm danh** tại trang chủ để mở camera trực tiếp, rồi bấm **Chụp và điểm danh vào/ra**. Không có lựa chọn ảnh từ thư viện. GPS được lấy mới tại lần gửi (maximumAge: 0); từ chối quyền camera/GPS sẽ không ghi nhận.
- Ảnh chụp mới từ video camera được đóng dấu ShiftSync, giờ Việt Nam, tọa độ và độ chính xác GPS. Đây là ảnh tạo trong web, không phải ảnh do ứng dụng Timemark riêng tạo. Trình duyệt không có API điều khiển ứng dụng Timemark.
- Sau lượt vào, nút chuyển sang lượt ra. Cho phép ra ca đã kết thúc nếu đã vào mà chưa ra. Mỗi lần thử lại đều chụp ảnh và lấy GPS mới; giao diện khóa gửi khi đang xử lý.
- Quản lý/quản trị mở **Phân loại ca làm** (#/attendance-results) để xem trạng thái vào, ra, tổng hợp và số phút vi phạm. Trang tải lại mỗi phút, phân trang phía server và bao gồm ca vắng không có lượt điểm danh. **Bảng công** hiện có vẫn hiển thị từng ảnh/lượt.
- Camera và Geolocation cần HTTPS hoặc localhost, cùng quyền trình duyệt tương ứng. Hai thao tác chính là mở Điểm danh và Chụp/gửi; hộp thoại cấp quyền do hệ điều hành/trình duyệt quản lý.
- Backend vẫn kiểm tra tọa độ điểm làm việc đã cấu hình, thời gian bằng chứng và GPS. Không bật mặc định bỏ qua xác minh; thiếu cấu hình điểm làm việc trả lỗi rõ ràng.

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

Luồng chụp mới trên web gửi thời gian/tọa độ đã đóng trên ảnh qua các trường timemarkCapturedAt, timemarkLatitude, timemarkLongitude để tương thích endpoint hiện có. Endpoint phân tích ảnh Timemark riêng vẫn giữ nguyên. Việc bắt buộc chụp mới được thực hiện trong giao diện; dữ liệu từ trình duyệt chưa phải bằng chứng độc lập chống client tùy chỉnh/giả mạo. Trạng thái được tính từ thời điểm máy chủ nhận ảnh nên chỉ nên dùng làm kết quả sơ bộ trước bước xác minh ảnh.

Flyway V7 tạo bảng `attendance_events`. Kiểm thử chạy với PostgreSQL Testcontainers qua `mvn test`; web dùng `npm test` và `npm run build`.
