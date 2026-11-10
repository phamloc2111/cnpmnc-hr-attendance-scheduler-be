# SYNC-28 — Điểm chuyên cần tháng

## Quy tắc

- Mỗi tháng bắt đầu từ 100 điểm, sàn 0. Từ 90 điểm trở lên đạt điều kiện thưởng; dưới 90 không đạt. Không tính số tiền.
- Dùng chung ân hạn app.attendance.grace-minutes (mặc định 5 phút theo SYNC-22). Chấm vào sớm hoặc đúng hết phút ân hạn: trừ 0. Sau ân hạn, độ trễ luôn tính từ giờ bắt đầu ca, không trừ thêm số phút ân hạn.
- Sau ân hạn và dưới 15 phút: trừ 5. Từ đúng 15 đến dưới 30 phút: trừ 10. Từ đúng 30 phút: trừ 20. So sánh Duration chính xác, không dùng số phút làm tròn trên màn hình để chọn mức phạt.
- Khoảng 15–30 phút dùng mức đề xuất 10 điểm vì Jira SYNC-28 chưa ghi mức cụ thể. Ân hạn dùng cùng cấu hình phân loại ca để không có trường hợp báo đúng giờ nhưng bị trừ điểm.
- Vắng, về sớm, thiếu lượt chấm và bản ghi thủ công không có CHECK_IN hợp lệ chưa tự trừ điểm. SYNC-29/30 dành cho giải trình/duyệt, SYNC-41 miễn phạt nghỉ phép được duyệt; mức phạt và hoàn điểm của các luồng đó chưa được chốt trong Jira. Không có API sửa điểm tùy ý ở task này.
- Lấy ngày bắt đầu ca (work_date), múi giờ Asia/Ho_Chi_Minh. Ca qua đêm sang tháng mới vẫn thuộc tháng ngày bắt đầu ca.

## Cách tính và cập nhật

Điểm là dữ liệu suy ra trực tiếp từ ca và CHECK_IN đã được backend xác minh/lưu thành công, qua public IdentityApi/WorkScheduleApi/AttendanceApi. Không nhân bản một bảng điểm, không cộng/trừ lũy kế dễ bị trừ hai lần, không cần cron reset tháng. Tháng mới hoặc nhân viên chưa có lượt chấm luôn bắt đầu 100.

API đọc lại phản ánh lượt chấm mới ngay sau transaction chấm công commit. Giao diện tải lại mỗi 60 giây, khi cửa sổ được focus, hoặc bấm Tải lại. Truy vấn đọc dùng REPEATABLE_READ để tổng điểm và lịch sử trong cùng response khớp nhau.

finalized=true khi tháng lịch đã kết thúc theo UTC+7; đây là kết quả tháng trước, không phải thao tác khóa sổ bất biến. Ca qua đêm cuối tháng có thể chấm vào sau 00:00 và vẫn tính cho tháng trước. Khi có luồng giải trình sau này, tích hợp dữ liệu đã duyệt qua AttendanceApi trước khi tính điểm. Không tuyên bố đã hỗ trợ duyệt giải trình trong SYNC-28.

## API (Bearer JWT, MANAGER/ADMIN)

- GET /api/v1/diligence?month=2026-11&q=&page=0&size=20
- GET /api/v1/diligence/employees/{employeeId}?month=2026-11&page=0&size=20

month bỏ trống: tháng hiện tại UTC+7. Nhận YYYY-MM từ 1900 đến tháng hiện tại. page >= 0, size 1–100, q tối đa 200 ký tự. month, timezone, finalized, asOf, policy đi cùng mỗi response. Trang danh sách trả items là summary; trang chi tiết có employee là summary và items là các lượt chấm vào (bao gồm cả 0 điểm trừ), phân trang server. Summary gồm employeeId, employeeName, accountLocked, score, deductedPoints (tổng phạt trước khi áp sàn 0), checkInCount, lateCount, rewardStatus ELIGIBLE/INELIGIBLE.

Quản lý chỉ thấy nhân viên đang được phân công cho mình. Admin thấy tất cả nhân viên, không đưa tài khoản quản lý/admin vào bảng. Tìm tên/email và phân trang nhân viên thực hiện tại database. Đọc chi tiết nhân viên ngoài phạm vi bị từ chối 403; không đăng nhập 401. Mọi response thành công dùng Cache-Control: no-store. Không có endpoint nhân viên xem điểm ở SYNC-28; phần đó thuộc SYNC-32.

## Kiểm tra

Testcontainers PostgreSQL riêng, không dùng NeonDB thật. Kiểm tra 90 vẫn đạt / 70 không đạt, ngưỡng 5/15/30 phút, không trừ lặp khi đọc hoặc chấm ra, chấm công hợp lệ cập nhật điểm ngay, upload bị từ chối không đổi điểm, reset tháng UTC+7, ca qua đêm, sàn 0, phân trang, tìm kiếm và quyền truy cập.
