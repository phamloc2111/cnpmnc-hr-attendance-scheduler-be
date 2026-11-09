# Tìm địa chỉ điểm làm việc với Goong

Quản lý/quản trị nhập địa chỉ (3–200 ký tự), bấm **Tìm kiếm**, rồi chọn một trong
tối đa 5 kết quả. Backend gọi Goong V2 Autocomplete để lấy gợi ý và V2 Place Detail
để lấy tọa độ. Frontend cập nhật ghim/vĩ độ/kinh độ trong bản nháp; chỉ **Lưu cấu
hình** mới ghi điểm làm việc. Tìm kiếm không tạo hay sửa dữ liệu trong database.

## Cấu hình backend

Tạo **REST API key** trong [Goong Account](https://account.goong.io/) và đặt vào
`be/.env` hoặc biến môi trường server:

```dotenv
GOONG_API_KEY=your-rest-api-key
```

Khởi động lại backend sau khi thêm key. Nếu web gọi backend cloud, phải đặt biến
trên server cloud và triển khai backend mới; key trong `.env` máy phát triển không
tự truyền lên cloud. Docker Compose hiện có đã đọc `be/.env`.

Key này khác `VITE_GOONG_MAPTILES_KEY` hiển thị bản đồ. REST key chỉ ở backend,
không đặt trong biến `VITE_*`, source hay Git. Giới hạn REST key theo IP server
trong Goong Console khi môi trường có IP cố định. Không dùng giới hạn domain của
Maptiles key thay cho cấu hình truy cập server-to-server.

## API nội bộ

Chỉ tài khoản MANAGER/ADMIN đang hoạt động được gọi:

- `GET /api/v1/workplace-settings/places?q=<địa chỉ>` → mảng `{placeId,label}`.
- `GET /api/v1/workplace-settings/places/detail?placeId=<id>` →
  `{placeId,label,latitude,longitude}`. ID là chuỗi opaque, truyền qua query được
  URL encode để giữ nguyên các ký tự `/`, `+`, `=`, `&`.

Không có kết quả trả `200 []`. Key chưa cấu hình trả `503
PLACE_SEARCH_NOT_CONFIGURED`. Lỗi provider, hết hạn mức, key sai, timeout hoặc
tọa độ provider không hợp lệ trả thông báo đã chuẩn hóa, không lộ key/URL/body
provider. Request đầu vào không hợp lệ trả `400 INVALID_PLACE_QUERY`.
Proxy dùng host cố định, timeout connect 2 giây/read 5 giây và không chủ động retry.
API không cho client truyền URL hoặc API key. Các response dùng `no-store`.

Frontend chỉ gọi tìm khi bấm nút/Enter, không gọi mỗi lần gõ. Đổi từ khóa, chọn
điểm khác hoặc rời trang hủy/bỏ qua response cũ. Enter trong ô địa chỉ không submit
form lưu cấu hình. Khi tìm lỗi, có thể thử lại hoặc nhập tọa độ/chọn trên bản đồ.
Địa chỉ chỉ dùng để hỗ trợ chọn vị trí, chưa lưu tên địa chỉ vào database.

Autocomplete và Place Detail có cách tính phí riêng, ngoài lượt tải bản đồ.
Xem [bảng giá Goong](https://goong.io/bang-gia/) và hạn mức tài khoản.

Tài liệu: [Autocomplete V2](https://help.goong.io/kb/rest-api-v2/autocomplete-rest-api-v2/autocomplete-v2/),
[Place Detail V2](https://help.goong.io/kb/rest-api-v2/place-detail-rest-api-v2/place-detail-place-detail-v2/).
