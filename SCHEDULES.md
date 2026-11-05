# Ca làm và lịch làm việc

SS-16: https://hcmut-team-f3ubnuof.atlassian.net/browse/SS-16 — Định nghĩa ca làm.

Web: menu **Lịch làm việc** → **Mẫu ca** để tạo, sau đó **Phân ca** để chọn nhân viên, mẫu ca và ngày bắt đầu. Nhân viên xem **Lịch của tôi** trên mobile hoặc web. Lịch hiển thị theo tuần và phân trang từ máy chủ.

## Quyền

| Thao tác | Nhân viên | Quản lý | Quản trị |
| --- | --- | --- | --- |
| Xem lịch | Của mình | Nhân viên hiện thuộc quyền | Toàn bộ |
| Tạo / xem mẫu ca | Không | Có | Có |
| Phân / hủy ca | Không | Nhân viên hiện thuộc quyền | Toàn bộ |

Mẫu ca dùng chung. Chỉ phân ca cho tài khoản EMPLOYEE đang hoạt động. Khóa tài khoản không xóa lịch đã phân. Khi chuyển nhân viên sang quản lý khác, quyền xem/hủy lịch chuyển theo quản lý hiện tại.

## Quy tắc

- Ngày làm việc là ngày bắt đầu ca tại `app.timezone` (Asia/Ho_Chi_Minh). Giờ mẫu ca có độ chính xác đến phút.
- Ca mới bắt buộc có số người cần (requiredHeadcount, số nguyên >= 1), vị trí cần (requiredPositionId từ danh mục đang sử dụng, không để trống) và cờ overnight.
- Chỉ bật overnight khi giờ kết thúc nhỏ hơn giờ bắt đầu; ca kết thúc ngày hôm sau. Không bật mà nhập giờ kết thúc sớm hơn thì trả 400 INVALID_OVERNIGHT. Hai giờ bằng nhau không hợp lệ; chưa hỗ trợ ca dài 24 giờ.
- Số người và vị trí là nhu cầu của mẫu ca, hiển thị trong danh sách. Cảnh báo thiếu người/vượt giờ thuộc SS-27; kiểm tra trùng giờ hiện có vẫn hoạt động.
- Nghỉ trong ca: số phút nguyên, từ 0 và nhỏ hơn tổng thời lượng. Giờ làm = tổng thời lượng trừ phút nghỉ.
- Lưu thời điểm lịch bằng UTC, kèm ngày làm việc, múi giờ và bản sao tên ca / thời gian nghỉ. Mẫu ca ở phạm vi này chỉ tạo và xem.
- Mọi ca của một nhân viên được kiểm tra chồng giờ, bao gồm ngày trước/sau và giờ nghỉ. Hai ca nối tiếp đúng thời điểm kết thúc được phép.
- Các thao tác ghi cùng nhân viên được tuần tự hóa bằng khóa hàng tài khoản trong transaction. Hai request phân ca chồng giờ đồng thời: một thành công, một 409.
- Hủy ca cần xác nhận trên web. Đổi ca bằng cách hủy và phân lại. Chưa triển khai lịch lặp, yêu cầu đổi ca, nghỉ phép hay chấm công.
- Danh sách lọc theo workDate (bao gồm hai đầu), tối đa 31 ngày, page từ 0, size 1–100. Ca đêm Chủ nhật xuất hiện trong tuần chứa ngày bắt đầu, kể cả khi kết thúc vào thứ Hai.

## API

Tất cả endpoint cần Bearer JWT, prefix `/api/v1`.

| Method | Path | Nội dung |
| --- | --- | --- |
| GET | /shift-templates?page=0&size=20 | Mẫu ca phân trang |
| POST | /shift-templates | Tạo mẫu ca, trả 201 |
| GET | /work-schedules?from=2026-11-02&to=2026-11-08&page=0&size=20 | Lịch phân trang, employeeId tùy chọn nhưng không vượt phạm vi quyền |
| POST | /work-schedules | Phân ca, trả 201 |
| DELETE | /work-schedules/{id} | Hủy ca, trả 204 |

Tạo mẫu ca:

```json
{"name":"Ca đêm","startTime":"22:00","endTime":"06:00","breakMinutes":30,"requiredHeadcount":2,"requiredPositionId":"00000000-0000-0000-0000-000000000050","overnight":true}
```

Phân ca (thay UUID bằng ID tài khoản và mẫu ca đã tạo):

```json
{"employeeId":"00000000-0000-0000-0000-000000000003","shiftTemplateId":"00000000-0000-0000-0000-000000000004","workDate":"2026-11-05"}
```

Chọn requiredPositionId từ GET /positions; quản lý cần tạo danh mục trước. Xem [POSITIONS.md](POSITIONS.md).

Response mẫu ca thêm requiredHeadcount, requiredPosition (tên để hiển thị), requiredPositionId và overnight. Ca cũ chưa có vị trí trả requiredPosition=null; web ghi Chưa khai báo vị trí. Ca tạo mới bắt buộc chọn vị trí đang sử dụng.

Response lịch gồm id, employeeId, employeeName, shiftTemplateId, workDate, shiftName, startsAt, endsAt, breakMinutes, workingMinutes, timezone. Ví dụ ca trên lưu startsAt=2026-11-05T15:00:00Z, endsAt=2026-11-05T23:00:00Z, workingMinutes=450.

Trang trả `{items,page,size,totalElements,totalPages}`. Lỗi theo ApiError hiện có: 400 VALIDATION_ERROR/INVALID_SHIFT/INVALID_OVERNIGHT/INVALID_DATE_RANGE, 401 UNAUTHORIZED, 403 FORBIDDEN, 404 SHIFT_NOT_FOUND/SCHEDULE_NOT_FOUND, 409 SCHEDULE_OVERLAP/EMPLOYEE_LOCKED/SCHEDULE_CONFLICT.

## Migration và kiểm thử

Khởi động lại backend để Flyway áp dụng đến V6. V5 xử lý giờ ca và yêu cầu nhân sự; V6 thêm danh mục vị trí và liên kết dữ liệu cũ bằng ID. Giữ nguyên V1–V5; tests dùng DB riêng, không trỏ vào NeonDB.

V5 giữ các mẫu ca cũ, đặt số người mặc định 1 và giữ vị trí chưa biết ở NULL. Migration đọc giờ theo cách JDBC cũ rồi chuyển sang JDBC LocalTime, đồng thời xác định cờ ca đêm. Chạy nâng cấp với múi giờ JVM giống bản V4 để giữ nguyên giờ ứng dụng đang hiển thị; mã mới lưu giờ ca trực tiếp, không dịch theo múi giờ máy. Các bản sao lịch UTC đã phân không thay đổi.

Test nâng cấp tạo schema V4 riêng trong PostgreSQL Testcontainers, chèn ca ngày/đêm bằng JDBC cũ, nâng V5 rồi kiểm tra giờ, dữ liệu và ràng buộc. Test API phủ thiếu trường, số người 0/âm, vị trí trống, cờ ca qua đêm không khớp và tạo/list thành công.

```powershell
.\mvnw.cmd test
```

Test mới: `src/test/java/com/company/hr/attendance/workschedule/ScheduleIntegrationTests.java` (API, quyền, migration PostgreSQL, trùng ca và concurrency) và `ShiftWindowTest.java` (ca đêm, UTC, ranh giới tháng).
