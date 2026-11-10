# SYNC-28 — Điểm chuyên cần tháng

Quản lý xem nhân viên phụ trách; quản trị xem toàn bộ nhân viên. Điểm bắt đầu 100 mỗi tháng, sàn 0. Từ 80 điểm trở lên đạt điều kiện thưởng, bao gồm đúng 80. Hệ thống không tính tiền thưởng.

## Quy tắc tính theo ngày

- Ngày làm việc là ngày bắt đầu ca trong múi giờ Asia/Ho_Chi_Minh. Ca qua đêm tính vào ngày bắt đầu.
- Đi trễ và về sớm có ân hạn 5 phút. Từ trên 5 đến hết 15 phút trừ 5; trên 15 đến hết 30 phút trừ 10; trên 30 phút trừ 20. Về sớm quá nửa ca không phép trừ 15. Về sớm đã duyệt trừ 0.
- Nghỉ được duyệt trước, nghỉ phép năm/ốm có giấy tờ: 0. Báo nghỉ muộn được duyệt: 5. Nghỉ không phép ngày đầu trong tháng: 20; từ ngày thứ hai: mức gốc 30 nhưng trần ngày là 20. Từ ngày nghỉ không phép liên tiếp thứ ba: ghi cờ chuyển xử lý kỷ luật, không trừ thêm từ ngày đó; giữ điểm trừ của hai ngày trước.
- Quên chấm cả vào và ra cùng ngày tính một lần. Có giải trình đúng hạn: hai lần đầu tháng nhắc nhở 0, từ lần thứ ba 2 điểm/lần. Không giải trình trước 48 giờ kể từ khi ca cuối của ngày kết thúc: 5 điểm/lần. Lỗi hệ thống được quản lý xác nhận: 0. Trước hạn giải trình, điểm quên chấm tạm là 0 và hiện trạng thái chờ.
- Cộng các lỗi trong ngày rồi áp trần 20 điểm/ngày, kể cả nghỉ không phép. Bảng chi tiết trả cả điểm gốc và điểm sau trần.

Điểm được tính lại khi đọc từ ca làm, lượt chấm vào/ra đã xác thực và dữ liệu xác minh. Không lưu số điểm do quản lý nhập. Ca chưa kết thúc không bị xem là vắng hay quên chấm. Ngày thiếu toàn bộ lượt vào sau khi kết thúc ca tạm ghi vắng không phép và cần quản lý xác minh đơn nghỉ; ngày chỉ thiếu một phần ca được xem là thiếu lượt chấm. Mỗi lần xác minh lưu lịch sử và revision để ngăn ghi đè đồng thời. Khi chưa có luồng xin phép/giải trình của SYNC-29/30/41, quản lý xác minh sự việc cùng căn cứ trong ghi chú.

## API

Bearer JWT, vai trò MANAGER/ADMIN; manager chỉ được thao tác nhân viên của mình. Các response dùng Cache-Control: no-store.

- GET /api/v1/diligence?month=YYYY-MM&q=&page=0&size=20: danh sách điểm, ngày vắng, quên chấm, giải trình chờ và cờ kỷ luật.
- GET /api/v1/diligence/employees/{id}?month=YYYY-MM&page=0&size=20: chi tiết theo từng ngày có ca, các thành phần điểm và trạng thái xác minh.
- GET /api/v1/diligence/employees/{id}/days/{date}/review: xác minh hiện tại và lịch sử 20 lần gần nhất.
- PUT cùng đường dẫn với JSON {absence, earlyLeaveApproved, missing, note, revision}: cập nhật sự việc đã xác nhận. absence = AUTO/APPROVED_LEAVE/LATE_APPROVED/UNEXCUSED; missing = AUTO/TIMELY_EXPLANATION/LATE_EXPLANATION/SYSTEM_CONFIRMED. Ghi chú 10–1000 ký tự, revision bắt đầu 0; xung đột trả 409.

Không có endpoint nhân viên tự xem điểm trong SYNC-28; phần đó thuộc SYNC-32. finalized=true chỉ cho biết đã qua tháng, điểm vẫn có thể tính lại khi có xác minh.
