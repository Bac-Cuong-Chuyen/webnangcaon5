# B8 — Hệ thống Quản trị Chuỗi cung ứng và Kho hàng

Dự án Nhóm 5, học phần CSE702051.

Hệ thống hướng tới quản lý sản phẩm, kho, nhà cung cấp, khách hàng,
nhập kho, xuất kho, tồn kho, điều chuyển và báo cáo nhập–xuất–tồn.

## 1. Thành viên và phân công tại Buổi 01

| Vai trò | Thành viên | MSSV | Phụ trách |
|---|---|---|---|
| V1 | Nguyễn Văn Bắc | 23016960 | Nhóm trưởng, kiến trúc, điều phối và tích hợp |
| V2 | Phạm Minh Việt Cường | Chờ bổ sung | Cơ sở dữ liệu và kiểm chứng kết nối |
| V3 | Nguyễn Văn Chuyền | 24104120 | Nghiệp vụ và backend |
| V4 | Hoàng Việt Long | 24107591 | Bảo mật và kiểm thử |
| V5 | Nguyễn Quốc Phong | 24107818 | Triển khai và tài liệu |

Danh sách trên ghi nhận cơ cấu đủ năm thành viên tại Buổi 01.

## 2. Ngăn xếp công nghệ

- Backend: Java 21, Spring Boot 3.5.5.
- Quản lý phụ thuộc: Maven 3.9.x.
- Truy cập dữ liệu: Spring Data JPA.
- Xác thực và phân quyền: Spring Security.
- Giao diện: HTML, CSS, JavaScript.
- Mã nguồn: Git và GitHub.
- Đóng gói môi trường: Docker, Docker Compose.
- CSDL của cấu hình local hiện tại: H2 trong bộ nhớ.
- CSDL online và URL trang chào: cập nhật sau khi kiểm chứng triển khai.

H2 trong bộ nhớ phục vụ chạy local, không thay thế yêu cầu
tạo và kiểm chứng cơ sở dữ liệu trực tuyến của Buổi 01.

## 3. Bảng phiên bản đã kiểm chứng

Kết quả trên máy Nguyễn Văn Bắc ngày 17/10/2026:

| Thành phần | Phiên bản |
|---|---|
| Git | 2.55.0.windows.5 |
| Java | 21.0.8 LTS |
| Maven | 3.9.12 |
| Spring Boot | 3.5.5, khai báo trong pom.xml |
| Docker Desktop | 4.95.0 |
| Docker Engine | 29.8.2 |
| Docker Compose | 5.6.0 |

Các thành viên đối chiếu phiên bản và gửi kết quả kiểm chứng từ máy mình.
Bảng này chưa xác nhận môi trường của cả nhóm đã đồng nhất.

## 4. Chạy ứng dụng local

Yêu cầu chạy ứng dụng: JDK 21 và Maven 3.9.x.

Mở PowerShell tại thư mục chứa pom.xml:

```powershell
java -version
mvn -v
mvn spring-boot:run
```

Có thể giới hạn bộ nhớ JVM khi chạy:

```powershell
mvn '-Dspring-boot.run.jvmArguments=-Xms64m -Xmx256m' spring-boot:run
```

Chờ log thông báo ứng dụng đã khởi động.

- Địa chỉ local: http://localhost:8081
- Cấu hình cổng: src/main/resources/application.properties
- Dừng ứng dụng: Ctrl+C tại terminal đang chạy server.

Lệnh chạy Maven trực tiếp không yêu cầu Docker.
Docker được kiểm chứng riêng để chuẩn bị môi trường đóng gói.

## 5. Cơ sở dữ liệu local hiện tại

H2 Console: http://localhost:8081/h2-console

Thông số của cấu hình phát triển local:

- Driver Class: org.h2.Driver
- JDBC URL: jdbc:h2:mem:supplychain
- User Name: sa
- Password: để trống theo cấu hình local hiện tại.

CSDL nằm trong bộ nhớ; không dùng để lưu dữ liệu bền vững.
Khi khởi động lại ứng dụng, dữ liệu có thể được khởi tạo lại.

H2 Console và tài khoản local này không phải cấu hình CSDL production.
Không đưa mật khẩu CSDL online hoặc connection string chứa bí mật
vào README hay repository.

## 6. Kiểm chứng Docker

Mở Docker Desktop và đợi Engine sẵn sàng, sau đó chạy:

```powershell
docker version
docker compose version
docker run --rm hello-world
```

Điều kiện đạt:

- docker version có cả Client và Server.
- docker compose version trả phiên bản.
- Container thử hiển thị Hello from Docker!

Kết quả trên máy Bắc đã đạt.
Bộ 09 lệnh nghiệm thu toàn nhóm vẫn đối chiếu theo Sổ tay lệnh mục 2.

## 7. Kiểm thử tự động

Tại thư mục chứa pom.xml:

```powershell
mvn '-DforkCount=0' test
```

Nếu gặp lỗi file biên dịch cũ không hợp lệ, lưu toàn bộ mã nguồn,
dừng ứng dụng đang chạy và biên dịch sạch:

```powershell
mvn '-DforkCount=0' clean test
```

Kết quả cần có BUILD SUCCESS; kiểm số test, failures, errors và skipped.
Test tự động không thay thế kiểm chứng môi trường hoặc kiểm thử online.

## 8. Cấu trúc dự án

Các thư mục trong src/main/java/com/example/supplychain:

- controller: tiếp nhận request và trả response.
- service: xử lý nghiệp vụ.
- repository: truy cập dữ liệu.
- entity: mô hình dữ liệu JPA.
- dto: cấu trúc dữ liệu đầu vào và đầu ra.
- security: thành phần bảo mật.
- config: cấu hình ứng dụng.
- exception: xử lý và chuẩn hóa lỗi.

Các thư mục liên quan:

- src/main/resources: cấu hình, tài nguyên và dữ liệu khởi tạo.
- src/test/java: kiểm thử tự động.
- database: script và ghi chú dữ liệu.
- docs: tài liệu dự án.

## 9. Quy ước Git

Repository: https://github.com/Bac-Cuong-Chuyen/webnangcaon5

- master: bản chính đã tích hợp.
- develop: nhánh phát triển chung.
- Nhánh nhiệm vụ: tạo riêng theo nội dung công việc.
- Commit mô tả rõ thay đổi, không gộp file ngoài phạm vi.
- Tạo PR, mô tả kết quả kiểm thử và rà soát trước khi merge.
- Không force-push nhánh chung khi chưa thống nhất.
- Không commit trực tiếp các giá trị bí mật.

Trước khi commit:

```powershell
git status --short --branch
git diff --check
git diff --stat
```

Chỉ git add những file thuộc nhiệm vụ đã kiểm tra.

## 10. Quản lý cấu hình và bí mật

.gitignore loại trừ:

- .env và .env.*; giữ ngoại lệ .env.example.
- target, file .class và log.
- Cấu hình IDE cá nhân và file hệ điều hành.

.env.example chỉ chứa tên biến và giá trị mẫu không bí mật.

.gitignore không loại bỏ file đã được theo dõi hoặc bí mật từng commit.
V4 kiểm cả working tree, file tracked và lịch sử Git trước khi
kết luận repository sạch.

## 11. Hồ sơ khởi động Buổi 01

| Mã | Sản phẩm cần hoàn thiện |
|---|---|
| B01-01 | Nhóm và phân công đủ V1–V5 |
| B01-02 | Đăng ký đề tài B8 và ngăn xếp công nghệ |
| B01-03 | Nhánh chính/phát triển, .gitignore và quyền giảng viên |
| B01-04 | Cả nhóm chạy đủ 09 lệnh kiểm chứng môi trường |
| B01-05 | Trang chào HTTPS truy cập được từ mạng ngoài |
| B01-06 | CSDL trống online đã tạo và kết nối được |

Ảnh chính theo danh mục: 01, 05, 06, 11, 13.

V5 cập nhật URL trang chào và thông tin triển khai sau khi xác nhận.
V2 bàn giao kết quả kết nối CSDL local/online.
V1 xác nhận quyền truy cập repository của giảng viên.