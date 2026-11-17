# RMI Student Database Synchronization

Hệ thống quản lý sinh viên và đồng bộ một chiều từ PostgreSQL trên **Server 2** sang PostgreSQL trên **Server 1** bằng Java RMI. Client JavaFX chạy trên máy ảo, ghi dữ liệu vào Server 2 và đọc cả hai server để kiểm tra.

## Kiến trúc

```text
Máy ảo Client JavaFX
   ├── ghi, sửa, xóa, tìm kiếm ──> Server 2 ──> PostgreSQL 2
   ├── kiểm tra ─────────────────> Server 2
   └── kiểm tra ─────────────────> Server 1 ──> PostgreSQL 1
                                      ▲
                                      └── RMI SyncEvent từ Server 2
```

- Server 2 lưu `student` và `sync_outbox` trong cùng transaction.
- Server 2 thử gửi sự kiện sang Server 1 ngay lập tức.
- Khi Server 1 ngừng hoạt động, sự kiện giữ `delivered=false`; worker thử lại mỗi giây, tối đa 100 sự kiện mỗi lượt.
- Server 1 lưu `event_id` vào `sync_inbox` để một sự kiện không được áp dụng hai lần.
- Hai server ghi kết quả vào `sync_log`.
- Xóa là xóa mềm (`deleted=true`) để thao tác xóa cũng được đồng bộ.

## Cấu trúc Maven

```text
RMI/
├── pom.xml
├── common/        Model, DTO và interface RMI dùng chung
├── server-1/      Dịch vụ Replica và PostgreSQL trên Máy thật 1
├── server-2/      Dịch vụ Primary, outbox và worker trên Máy thật 2
├── client/        JavaFX/FXML/CSS chạy trên máy ảo
└── database/      Script PostgreSQL cho hai server
```

`Student`, `SyncEvent` và các interface RMI chỉ tồn tại trong `common`, bảo đảm ba ứng dụng dùng cùng kiểu serialization.

## Yêu cầu

- JDK 17.
- Maven 3.9 trở lên để build.
- PostgreSQL trên hai máy thật.
- TCP `1099` và `1100` mở trên hai máy thật.
- Máy ảo ping và kết nối được hai IP server.

Các dependency được Maven quản lý:

- PostgreSQL JDBC `42.7.14`.
- JavaFX `17.0.21`.
- JUnit 5.

## 1. Tạo database

Trên cả hai máy, đăng nhập bằng tài khoản quản trị PostgreSQL và chạy nội dung `database/create-database.sql`. Thay `CHANGE_ME` bằng mật khẩu thật.

Sau đó kết nối vào database `rmi_student` bằng `rmi_user`:

- Máy thật 1 chạy `database/server1.sql`.
- Máy thật 2 chạy `database/server2.sql`.

Ví dụ với `psql`:

```powershell
psql -U postgres -f database\create-database.sql
psql -U rmi_user -d rmi_student -f database\server1.sql
```

Trên Máy thật 2 thay script cuối bằng `server2.sql`.

Nếu role `rmi_user` đã tồn tại nhưng bạn không nhớ mật khẩu đã đặt, mở Query Tool của pgAdmin bằng tài khoản quản trị và chạy:

```sql
ALTER ROLE rmi_user WITH LOGIN PASSWORD 'MAT_KHAU_MOI';
```

Sau đó dùng đúng `MAT_KHAU_MOI` cho thuộc tính `db.password` trên server tương ứng.

## 2. Build

Tại thư mục gốc:

```powershell
mvn clean package
```

Kết quả:

```text
server-1/target/server-1.jar
server-2/target/server-2.jar
client/target/client.jar
```

Các JAR đã đóng gói dependency cần thiết. Client JAR được build cho hệ điều hành đang thực hiện Maven build; nếu máy ảo dùng Windows thì build client trên Windows.

## 3. Triển khai Server 1

Chép `server-1.jar` sang Máy thật 1 và tạo file `server1.properties` cạnh JAR:

```properties
db.url=jdbc:postgresql://127.0.0.1:5432/rmi_student_server1
db.user=rmi_user
db.password=MAT_KHAU_THAT
rmi.host=192.168.1.10
rmi.port=1099
rmi.servicePort=1100
```

Chạy:

```powershell
java -jar server-1.jar server1.properties
```

Lệnh trên vừa khởi động dịch vụ RMI vừa mở dashboard JavaFX của Server 1. Dashboard tìm kiếm, thêm, sửa và xóa trực tiếp dữ liệu cục bộ trong Database 1, đồng thời hiển thị lịch sử đồng bộ.

Trước khi dashboard mở, Server 1 hiển thị hộp cấu hình để nhập IP/hostname, cổng Registry và cổng Service. Giá trị được lưu vào `server1.properties` ở thư mục chạy.

`rmi.host` phải là IP LAN mà Server 2 và máy ảo truy cập được, không dùng `127.0.0.1`.

Khi chạy trực tiếp `Server1Main.java` trong IDE với thư mục làm việc là thư mục gốc dự án, hãy tạo `server1.properties` tại `D:\RMI\server1.properties`. Chương trình ưu tiên file này thay cho cấu hình mẫu trong `resources`.

Bạn cũng có thể đặt giá trị nhạy cảm bằng biến môi trường, không cần ghi mật khẩu vào file:

```powershell
$env:RMI_DB_PASSWORD="MAT_KHAU_THAT"
$env:RMI_HOST="192.168.1.10"
```

### Chạy thử cả hai server trên cùng một máy

Hai file `server1.properties` và `server2.properties` ở thư mục gốc đã được cấu hình dùng các cổng khác nhau để không xung đột:

- Server 1: registry `1099`, service `1100`.
- Server 2: registry `2099`, service `2100`.

Thay `NHAP_MAT_KHAU_POSTGRESQL` trong cả hai file bằng mật khẩu thật của role `rmi_user`. Trên giao diện Client nhập Server 1 là `192.168.1.14:1099` và Server 2 là `192.168.1.14:2099`.

Cấu hình này chỉ dùng để kiểm tra RMI trên một máy. Khi triển khai trên hai máy thật, mỗi máy dùng database PostgreSQL cục bộ riêng và có thể cùng dùng registry `1099` vì địa chỉ IP khác nhau.

## 4. Triển khai Server 2

Chép `server-2.jar` sang Máy thật 2 và tạo `server2.properties`:

```properties
db.url=jdbc:postgresql://127.0.0.1:5432/rmi_student_server2
db.user=rmi_user
db.password=MAT_KHAU_THAT
rmi.host=192.168.1.20
rmi.port=1099
rmi.servicePort=1100
server1.host=192.168.1.10
server1.port=1099
```

Chạy:

```powershell
java -jar server-2.jar server2.properties
```

Lệnh trên vừa khởi động dịch vụ RMI, worker đồng bộ và dashboard JavaFX của Server 2. Dashboard quản lý trực tiếp Database 2, hiển thị số bản ghi đang chờ và 100 lần đồng bộ gần nhất.

Trước khi dashboard mở, Server 2 cho nhập IP/hostname và hai cổng của chính nó. Thông tin kết nối đến Server 1 được giữ trong `server2.properties` để phục vụ đồng bộ và không cần nhập lại trên giao diện. Giá trị cấu hình mạng Server 2 được lưu vào `server2.properties` ở thư mục chạy.

Khi chạy trong VS Code, mở hai terminal riêng và chạy lần lượt `Server1Main.java`, `Server2Main.java`. Nếu báo `Port already in use`, hãy dừng phiên server cũ trước khi nhấn Run lại.

## 5. Chạy Client trên máy ảo

Chép `client.jar` sang máy ảo:

```powershell
java -jar client.jar
```

Trên giao diện, nhập:

```text
Server 1: 192.168.1.10 : 1099
Server 2: 192.168.1.20 : 1099
```

Nhấn **Lưu & kết nối**. Client lưu cấu hình vào `client.properties` cạnh JAR.

## Chức năng

- Thêm hoặc sửa sinh viên theo mã.
- Xóa mềm sinh viên.
- Client có thể lưu/xóa trực tiếp trên Server 1.
- Client có thể lưu/xóa trên Server 2 và đồng bộ sang Server 1.
- Tìm theo mã, họ tên hoặc lớp.
- Hiển thị danh sách sinh viên trên Database 2.
- Dashboard quản trị riêng cho Server 1 và Server 2.
- Xem 100 sự kiện đồng bộ gần nhất trên từng server.
- Hiển thị số sự kiện đang chờ đồng bộ trên Server 2.
- So sánh cùng một sinh viên giữa hai database.
- Đối soát toàn bộ sinh viên và liệt kê các mã không đồng nhất.
- Hiển thị trạng thái RMI và PostgreSQL của từng server.
- Hiển thị thời điểm cập nhật, lỗi kết nối và trạng thái đồng bộ.
- Đồng bộ ngay khi ghi và tự gửi lại khi Server 1 hoạt động trở lại.
- Gửi lại theo lô và chống xử lý sự kiện trùng.
- Lưu lịch sử thành công/thất bại trong `sync_log`.

## Kiểm tra hàng đợi

Trên Database 2:

```sql
SELECT event_id, student_id, delivered, retry_count, last_error
FROM sync_outbox
ORDER BY created_at DESC;
```

Trên Database 1:

```sql
SELECT event_id, received_at
FROM sync_inbox
ORDER BY received_at DESC;
```

Kiểm tra nhật ký trên mỗi server:

```sql
SELECT * FROM sync_log ORDER BY created_at DESC;
```

## Thứ tự chạy

1. Khởi động PostgreSQL trên hai máy.
2. Khởi động `server-1.jar`.
3. Khởi động `server-2.jar`.
4. Khởi động `client.jar` trên máy ảo.
5. Nhập IP, lưu kết nối và kiểm tra trạng thái Online.
6. Thêm sinh viên rồi dùng **So sánh hai DB**.
