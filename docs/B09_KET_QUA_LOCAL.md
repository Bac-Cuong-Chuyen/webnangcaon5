# B09 — Kết quả kiểm chứng local

Ngày thực hiện: 19/10/2026. Nhánh: `feature/b09-performance`.
Commit mã nguồn: `644808a`. Môi trường: Java 21, Spring Boot 3.5.5,
H2 file, ứng dụng `http://localhost:8081`, profile `b09`.

## Dữ liệu và phép đo

- 5.000 sản phẩm B09; báo cáo 01–15/10/2026: nhập 500.000/5.000 dòng,
  xuất 100.000/5.000 dòng, chuyển 50.000/1 dòng tổng hợp.
- Mỗi lượt đo: 5 endpoint, 5 lần làm nóng và 50 mẫu/endpoint;
  tổng 250 mẫu hợp lệ HTTP 200. Đo tuần tự bằng Invoke-WebRequest/Stopwatch;
  kiểm tra JSON/hash ngoài khoảng đo. Median là trung bình mẫu thứ 25 và 26.
- BEFORE: `BEFORE_20261019_164156_117`;
  AFTER: `AFTER_20261019_171402_361` trong `scripts/B09-test-results`.

| Endpoint | Median trước (ms) | Median sau (ms) | Cải thiện (%) |
|---|---:|---:|---:|
| Product search | 18.193 | 15.586 | 14.33 |
| Import report | 123.246 | 141.349 | -14.69 |
| Export report | 133.939 | 125.727 | 6.13 |
| Transfer report | 11.562 | 13.182 | -14.01 |
| Product detail | 12.067 | 14.746 | -22.20 |

Hai trong năm endpoint nhanh hơn; ba endpoint chậm hơn. Chưa có bằng chứng
tăng tốc tổng thể. Loạt AFTER có làm nóng cache, không đo độ trễ cache miss.
Phép đo thực hiện trước thay đổi N+1 và phân trang; không dùng bảng này để
khẳng định hiệu quả độ trễ của hai thay đổi sau đó. GitHead trong manifest
là commit nền c133bb2 vì mã nguồn tại thời điểm đo chưa commit;
GitStatus và các hash được giữ nguyên làm dấu vết.
Các lượt BEFORE ban đầu lỗi đăng nhập không tạo đủ mẫu, vẫn giữ local,
không dùng làm baseline và không trộn vào 250 mẫu hợp lệ.

## Index, cache, N+1 và phân trang

- PLAN_BEFORE_20261019_164500_687 và PLAN_AFTER_20261019_165013_815:
  cùng SQL/ngày; sau khi áp dụng 6 index có sử dụng index mới.
  Imports logical reads 1114 -> 862; không suy diễn đây là độ trễ HTTP.
- INDEX_20261019_164851_427: count/sum dữ liệu trước và sau giữ nguyên.
- CACHE_20261019_171211_953: 7/7 PASS, gồm miss/hit, key theo ngày,
  ADMIN không được đọc báo cáo, MANAGER không được đọc stats,
  đổi SKU làm mất hiệu lực cache, khôi phục dữ liệu sau kiểm thử.
  TTL 60 giây là cấu hình, chưa kiểm thử chờ hết TTL.
- CACHE_STATS_20261019_171618_673: exportReports có 56 hit, 6 miss,
  6 load thành công, 0 load lỗi; dữ liệu chi tiết trong JSON gốc.
- NPLUSONE_BEFORE_20261019_172006_189 và NPLUSONE_AFTER_20261019_172417_177:
  danh sách chi tiết nhập/xuất 10/50 dòng từ 11/51 SQL còn 1 SQL;
  kiểm thử STAFF chỉ đọc phiếu mình đạt.
- PAGINATION_20261019_173718_003: sản phẩm/tồn kho dùng 2 SQL,
  phiếu/chi tiết nhập/xuất dùng 1 SQL khi trả cả 20 và 100 dòng.
  Kiểm thử mặc định20, tối đa100, tham số sai400, thứ tựID ổn định,
  lọc quyền trước LIMIT và không trùng trang đều đạt.
- Hồi quy thực tế: `mvn clean test` 180 tests, failures0/errors0/skipped0,
  BUILD SUCCESS lúc 17:37:30. Ảnh trình duyệt xác nhận page0 ID1–20,
  page1 ID21–40 trên dữ liệu local. Các phép đếm SQL dùng DB kiểm thử riêng.

## Chạy lại

Từ thư mục gốc repo, Java21; không nhập mật khẩu vào mã nguồn.
Tài khoản manager phải có sẵn, mật khẩu nhập ẩn khi script yêu cầu.

```powershell
mvn clean test
mvn spring-boot:run '-Dspring-boot.run.profiles=b09'
# Terminal khác, ứng dụng vẫn chạy:
powershell.exe -NoProfile -File .\scripts\B09-do-hieu-nang.ps1 -Phase after -BaselineDirectory .\scripts\B09-test-results\BEFORE_20261019_164156_117
powershell.exe -NoProfile -File .\scripts\B09-kiem-chung-cache.ps1
powershell.exe -NoProfile -File .\scripts\B09-xem-thong-ke-cache.ps1
```

Lệnh chạy lại tạo thư mục thời gian mới, không thay kết quả gốc đã ghi.
Script kiểm cache tạm đổi SKU B09 rồi khôi phục; chỉ dùng trên môi trường
local thử nghiệm. Script xem kế hoạch/index yêu cầu dừng ứng dụng để truy
cập H2 file; đọc hướng dẫn trong script trước khi thực hiện.
Không tạo baseline mới trên DB đã tối ưu rồi gọi đó là BEFORE.

## Phạm vi còn lại

Đây là minh chứng local, chưa xác nhận hiệu năng online hoặc hoàn tất toàn bộ
Buổi09. Còn phần giao diện báo cáo 5.000 dòng, accessibility/mobile/keyboard,
Lighthouse, metadata/sitemap/robots theo phạm vi trang công khai,
compression/browser cache, bộ ca kiểm thử nghiệp vụ/API và triển khai online.
180 kiểm thử tự động không tự thay thế bộ 25 ca nghiệm thu được phân công.
Database, bản backup và mật khẩu nằm ngoài Git.
