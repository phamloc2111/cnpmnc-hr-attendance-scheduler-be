# SYNC-27 — Firebase push nhắc đăng ký ca

Dùng Firebase Cloud Messaging HTTP v1 qua Firebase Admin SDK. Không cần Firebase Authentication, Firestore hay Cloud Functions; xác thực vẫn dùng JWT của ShiftSync. FCM không tính phí, nhưng dịch vụ host/cron của bạn có thể tính phí theo gói.

## Cấu hình local

1. Firebase Console → Project settings → Service accounts → Generate new private key. Lưu file ngoài repo, ví dụ `D:/hr/secrets/firebase-service-account.json`.
2. Trong `be/.env` (không commit):

```dotenv
PUSH_ENABLED=true
FIREBASE_CREDENTIALS_PATH=D:/hr/secrets/firebase-service-account.json
```

Mặc định PUSH_ENABLED=false: backend vẫn khởi động, API config trả enabled=false và không khởi tạo SDK/gửi push. Khi bật mà thiếu file credentials, backend báo lỗi cấu hình rõ ràng. Service account cần quyền gửi FCM cho cùng project với frontend; kiểm tra Firebase Cloud Messaging API (HTTP v1) đã bật.

Docker local:

```powershell
docker compose -f docker-compose.yaml -f docker-compose.firebase.yaml up -d --build
```

Override mount file ngoài repo vào `/run/secrets/firebase-service-account.json` chỉ đọc. Nếu file ở vị trí khác, đặt FIREBASE_CREDENTIALS_HOST_PATH trong `.env`. Không thêm file private vào src/main/resources; Maven, Git và Docker đều loại tên service-account/service_account JSON khỏi bản build.

## Render

Trong backend service → Environment → Secret Files, tạo `firebase-service-account.json`, dán nội dung service account. Đặt env:

```dotenv
PUSH_ENABLED=true
FIREBASE_CREDENTIALS_PATH=/etc/secrets/firebase-service-account.json
```

Redeploy. Đường dẫn trên Windows và Render khác nhau; không đưa đường dẫn máy local hoặc JSON private vào Git. Render cung cấp secret file ở runtime cho Docker: https://render.com/docs/docker-secrets.

**Lịch nhắc cần backend hoạt động.** Render Free có thể ngủ sau 15 phút không có request; lúc ngủ scheduled task không chạy. Để nhắc đúng giờ, dùng instance luôn chạy hoặc hệ thống cron/worker riêng. Khi backend hoạt động lại trong Chủ nhật, polling có thể gửi bù giai đoạn hiện tại; không gửi nhắc cho tuần đã đóng. Xem https://render.com/docs/free.

## Luồng gửi và giới hạn

- Poll mỗi 60 giây (PUSH_POLL_DELAY_MS), theo Asia/Ho_Chi_Minh. Không phụ thuộc giờ hệ điều hành server hay đồng hồ nhân viên.
- Chủ nhật 00:00 đến trước 08:00: gửi OPEN cho thiết bị nhân viên đã đồng ý nhận push.
- Chủ nhật 08:00, 15:00 và 20:00: gửi REMINDER_0800, REMINDER_1500 và REMINDER_2000 tương ứng nếu tuần kế tiếp chưa có mẫu ca nào được chọn. Lựa chọn rỗng được xem là chưa đăng ký. Người đã chọn ít nhất một ca không nhận lời nhắc này.
- Chỉ gửi bù mốc hiện tại khi backend thức lại; bỏ job của mốc cũ để tránh dồn nhiều thông báo. Nhân viên đăng ký sau 08:00 sẽ không nhận nhắc 15:00/20:00.
- Mỗi thiết bị/tuần/loại có một delivery unique. PostgreSQL SKIP LOCKED và lease ngăn nhiều instance cùng nhận một job. Retry tối đa 5 lần với backoff; token UNREGISTERED bị xóa. Không ghi token hoặc private key vào log, chỉ ghi mã lỗi FCM.
- Push là best-effort: mạng/OS/Focus có thể trì hoãn. TTL kết thúc ở thứ Hai 00:00; worker cũng bỏ thông báo hết hạn. Provider có thể đã nhận trước khi ứng dụng crash; retry không đảm bảo exactly-once. Tag ổn định giúp gộp thông báo lặp trên trình duyệt.
- Tài khoản khóa/sai role hoặc session đã bị thu hồi không được gửi. Consent push có thể tồn tại lâu hơn JWT 8 giờ để nhắc khi app đóng; logout/đổi mật khẩu thu hồi session thì không gửi nữa. Frontend xóa binding trước logout và bỏ thông báo muộn của tài khoản khác.
- Tối đa 10 token mỗi nhân viên. Rebind cùng token sang tài khoản/phiên khác xóa delivery cũ; không tự bật consent cho người dùng mới trên trình duyệt chung.
- Không gửi push thật trong kiểm thử hoặc gọi API thật của nhân viên để test. UI có nút Gửi thử cho chính thiết bị đã đăng ký.

## API (EMPLOYEE, Bearer JWT)

- `GET /api/v1/notifications/config`: `{enabled, reminderTimes: ["08:00", "15:00", "20:00"], timezone}`.
- `PUT /api/v1/notifications/devices`, body `{ "token": "FCM registration token" }`: đăng ký/rebind cho JWT subject + session hiện tại, 204.
- `DELETE /api/v1/notifications/devices`, cùng body: chỉ xóa token thuộc JWT subject, 204 idempotent; không thể xóa thiết bị người khác.
- `POST /api/v1/notifications/test`, cùng body: chỉ gửi thử token đã thuộc chính nhân viên, 204 là FCM đã nhận yêu cầu, không đảm bảo thiết bị đã hiển thị.
- 503 PUSH_NOT_CONFIGURED; 502 PUSH_DELIVERY_FAILED; 409 PUSH_DEVICE_LIMIT; 400 body không hợp lệ; 401/403 theo quyền chung.

Migration V13 bổ sung ba loại lời nhắc, không sửa V12 đã áp dụng. Migration V12 tạo notification_devices và notification_deliveries. Không thay đổi V10/V11. RegistrationApi/IdentityApi là ranh giới công khai để kiểm tra điều kiện gửi, không truy cập repository module khác.

## Xác minh

Backend: `./mvnw test`; test Firebase dùng mock gateway + Postgres Testcontainers. Kiểm tra mốc UTC+7, người đã/chưa đăng ký, retry, token hết hạn, revoke, đổi tài khoản, queue đồng thời và lease cũ. Cấu hình Firebase thật và quyền nhận thông báo trên Chrome Android/Safari iOS cần kiểm tra riêng bằng nút Gửi thử.

Nguồn tích hợp: https://firebase.google.com/docs/admin/setup và https://firebase.google.com/docs/cloud-messaging/web/get-started.

## Đánh thức Render Free bằng GitHub Actions

Đã có workflow .github/workflows/registration-reminders.yml. Workflow gọi POST /api/v1/notification-jobs/registration-reminders; endpoint chỉ nhận header X-Reminder-Cron-Key khớp PUSH_CRON_SECRET (ít nhất 32 ký tự), JWT nhân viên không thay thế được khóa này. Trả 202 ngay và xử lý async; gọi trùng khi đang chạy được gộp. Không cung cấp API chọn nhân viên/nội dung tùy ý.

Thiết lập sau khi review/deploy:

1. Render Environment: PUSH_CRON_SECRET = một chuỗi random dài ít nhất 32 ký tự. Local .env đã có khóa random riêng; có thể dùng khóa đó hoặc tạo khóa production khác.
2. GitHub repo backend → Settings → Secrets and variables → Actions → Secrets:
   - PUSH_CRON_SECRET: cùng giá trị với Render.
   - PUSH_CRON_URL: https://<backend>.onrender.com/api/v1/notification-jobs/registration-reminders
3. Trong tab Variables, đặt PUSH_CRON_ENABLED=true khi backend/Firebase đã sẵn sàng. Mặc định không chạy job khi chưa bật biến này.
4. Workflow schedule chỉ chạy từ default branch (thường main). Hoàn thành PR feat → dev, kiểm tra dev, rồi PR dev → main theo workflow dự án. Khi file đã có trên default branch, có thể chọn Run workflow để thử gọi cron. Ngoài Chủ nhật, trigger không gửi nhắc đăng ký.

Lịch UTC: Saturday 16:55 làm nóng lúc 23:55 thứ Bảy Việt Nam; sau đó mỗi 5 phút trong suốt Chủ nhật Việt Nam. Backend tự polling 60 giây lúc đã thức, gồm ba mốc nhắc 08:00, 15:00, 20:00. Không phụ thuộc người mở website; các lần gọi có retry cho cold start. GitHub schedules có thể bị trễ/bỏ khi hệ thống bận, không có SLA đúng từng phút: https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows#schedule.

Nếu cần kích hoạt trước khi release lên default branch, có thể dùng cron HTTP bên ngoài với cùng POST URL và header khóa. Không mở endpoint thành công khai hoặc đặt khóa trong query string/frontend. Không dùng API này để gửi thử người khác; nút Gửi thử chỉ dành cho chính thiết bị đã bật.
