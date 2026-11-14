# Điểm danh bằng ảnh Timemark và phân loại kết quả

Phạm vi: SYNC-19 (US09) và SYNC-22 (US12), trên backend và web. Không thay đổi ứng dụng mobile.

## Sử dụng trên web

- Nhân viên bấm **Điểm danh** tại trang chủ để chọn ảnh gốc đã xuất từ ứng dụng Timemark (định dạng JPEG, PNG hoặc WEBP, dung lượng tối đa 5 MB). Không chỉnh sửa, vẽ đè hoặc đóng dấu lại ảnh trên trình duyệt.
- Backend tiếp nhận tệp ảnh gốc cùng ca làm việc (`scheduleId`) và loại lượt (`type`: `CHECK_IN`/`CHECK_OUT`), nhận diện nhân viên qua JWT.
- Backend gọi trực tiếp `TimemarkReader` sử dụng Gemini AI để phân tích ảnh, trích xuất thời gian in trên ảnh và tọa độ GPS (kinh độ, vĩ độ).
- Backend tự động đối chiếu tọa độ và thời gian đọc từ ảnh với điểm làm việc đã cấu hình qua `AttendanceVerificationService`. Chỉ khi toàn bộ kiểm tra thành công, lượt điểm danh và ảnh mới được lưu trữ.

## Quy tắc ghi nhận

- Một ca tối đa một lượt `CHECK_IN` và một lượt `CHECK_OUT`. Lượt ra cần lượt vào; không nhận lượt vào sau khi ca kết thúc. Backend kiểm tra tài khoản nhân viên đang hoạt động và ca thuộc chính nhân viên đó. Gửi trùng trả `409`.
- Ảnh JPEG/PNG/WEBP tối đa 5 MB, lưu riêng tư trong PostgreSQL `BYTEA`. API ảnh yêu cầu JWT; nhân viên chỉ xem ảnh của mình, quản lý chỉ xem nhân viên hiện thuộc quyền, quản trị viên xem toàn bộ. Không cache ảnh.
- Không nhận hoặc dựa vào các trường GPS/thời gian (`locatedAt`, `latitude`, `longitude`, `accuracyMeters`, `timemarkCapturedAt`) từ client. Toàn bộ thông tin vị trí và thời điểm điểm danh được trích xuất trực tiếp từ ảnh Timemark trên máy chủ.
- Ảnh thiếu thời gian hoặc thiếu kinh độ/vĩ độ sẽ bị từ chối với lỗi rõ ràng. Nếu dịch vụ Gemini OCR gặp lỗi, timeout hoặc chưa cấu hình `GEMINI_API_KEY`, request sẽ trả lỗi 502/503 và tuyệt đối không tạo bản ghi điểm danh hay lưu ảnh.
- Ca có điểm danh không thể bị hủy (`409 SCHEDULE_HAS_ATTENDANCE`) để giữ lịch sử chứng cứ.

## Cấu hình Gemini AI

Trong file `.env`:
- `GEMINI_API_KEY`: API key của Google AI Studio/Gemini, bắt buộc cấu hình để phân tích ảnh Timemark.
- `GEMINI_MODEL`: Model sử dụng (mặc định: `gemini-2.5-flash-lite`).
- `GEMINI_TIMEOUT_MS`: Thời gian chờ tối đa khi gọi Gemini (mặc định: `4000` ms).
- `TIMEMARK_MAX_TIME_DIFFERENCE_SECONDS`: Chênh lệch tối đa cho phép giữa thời gian trên ảnh Timemark và giờ máy chủ (mặc định: `300` giây / 5 phút).

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
