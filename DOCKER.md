# Chạy riêng backend bằng Docker Compose

Compose chỉ khai báo service backend. Database dùng kết nối có sẵn từ be/.env; không tạo thêm container database.

## Chạy

Mở Docker Desktop. Trong thư mục be:

```powershell
docker compose -f docker-compose.yaml up -d --build
docker compose -f docker-compose.yaml logs -f backend
```

API mặc định: http://localhost:8080. Kiểm tra ứng dụng: http://localhost:8080/actuator/health.

Nếu chưa có .env, sao chép .env.example và điền DB_URL, DB_USERNAME, DB_PASSWORD, JWT_SECRET cùng các biến cần dùng. File .env không được đưa vào image hoặc Git. Biến được truyền lúc chạy container; không cần chép .env vào /app.

Nếu DB chạy trên Windows host, dùng host.docker.internal thay localhost trong DB_URL, ví dụ jdbc:postgresql://host.docker.internal:5432/hr_attendance. Nếu dùng NeonDB, giữ URL từ xa và sslmode=require.

Compose dùng env_file format raw để giữ nguyên ký tự đặc biệt trong giá trị, cần Docker Compose từ 2.30. Viết .env theo dạng KEY=value, không bọc giá trị trong dấu nháy. Xem [tài liệu Docker](https://docs.docker.com/reference/compose-file/services/#format).

## Cổng

Trong container, SERVER_PORT cố định 8080. Để đổi cổng trên máy, dùng BACKEND_PORT:

```powershell
$env:BACKEND_PORT = "8081"
docker compose -f docker-compose.yaml up -d --build
```

Sau đó gọi http://localhost:8081. Có thể đặt BACKEND_PORT trong .env. Nếu dùng cổng mặc định 8080, dừng backend đang chạy trực tiếp từ IDE trước.

## Dừng và kiểm tra cấu hình

```powershell
docker compose -f docker-compose.yaml down
docker compose -f docker-compose.yaml config --quiet
docker compose -f docker-compose.yaml config --services
```

Không in toàn bộ docker compose config khi chia sẻ log vì kết quả có thể chứa giá trị từ .env.

Dockerfile build JAR bằng Maven/Java 21 và chạy bằng JRE 21 với user không phải root. Build image bỏ qua test; kiểm thử backend vẫn chạy riêng bằng .\mvnw.cmd test với Docker/Testcontainers. Không cần Maven hoặc Java trên host để build qua Compose.

Múi giờ JVM giữ Asia/Ho_Chi_Minh để phù hợp cách chạy hiện tại và nâng cấp dữ liệu ca cũ ở V5. Flyway tự áp dụng các migration còn thiếu vào database được cấu hình khi ứng dụng khởi động.
