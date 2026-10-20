# B09 — Bộ nghiệm thu25ca và cách truy vết kết quả

Phân công ban đầu: TV4 phụ trách kiểm thử. Theo yêu cầu của người dùng,
người dùng tiếp nhận phần tổng hợp/nghiệm thu này; không tự ghi TV4 là
người chạy hoặc tác giả của những việc người dùng đã làm.

## Phạm vi

Theo mục7.4 tài liệu kỹ thuật: chọn9ca nghiệp vụ chính,6ca ngoại lệ/biên,
7ca phân quyền/bảo mật và3ca báo cáo/xuất dữ liệu, tổng25ca.
Định nghĩa đầy đủ từngca nằm trong `verification/b09-acceptance-cases.json`.

24ca dẫn đến24testcaseJUnit khác nhau có sẵn trong bộ kiểm thử; không
viết lại kiểm thử giống hệt chỉ để tăng số lượng. Các request nghiệp vụ
dùng MockMvc, Spring Security và DBkiểmthửriêng; báo cáo được kiểm qua
APIduyệt kết hợp reportservice. Không mô tả đây là25HTTPrequest tới Render.

CaB09-TC25 là kiểm thử Excel thủ công đã thực hiện trong phiên làm việc:
báo cáo nhập5000dòng, đangxemtrang2, xuấtfile cóheader vàdữliệuđếnC5001,
giátrị100; cácdòngngaysautrống trong ảnh. Metadata vàSHA256 haiảnh đầu/cuối
ở `verification/b09-acceptance-manual.json`. Đây là bằng chứng đã quan sát,
không phải kiểm thử Excel tự động vừa chạy và chưa kiểm toán từngôxlsxgốc.

## Chạy tự động khi cần kiểm thử lại

Từ thư mục chứa pom.xml, Java21/Maven:

```powershell
powershell.exe -NoProfile -File .\scripts\B09-tong-hop-nghiem-thu.ps1
```

Lệnh chạy `mvn clean test`, sau đó sinh CSV, báo cáoHTML, manifest và
bản sao XML nguồn của các ca được dẫn chiếu. Không sửa code ứng dụng,
không truy cập hoặc tạo dữ liệu trong app8081/Render. Các DBJUnit tách
riêng khỏi DBfile local. Bộ190test lớn hơn bộ25ca nghiệmthu; hai số này
không phải hai lượt cùng loại để cộng với nhau.

## Tổng hợp từ lượt kiểm thử đã chạy

Nếu đã có bộJUnit190testPASS và nguồn chưa thay đổi, dùng:

```powershell
powershell.exe -NoProfile -File .\scripts\B09-tong-hop-nghiem-thu.ps1 -UseExistingResults
```

Chế độ này không chạy lại test. Nó đọc trạng thái testcase thực tế từ XML,
giữ timestamp nguồn, copy XML và hash bằng chứng. HEAD lúc tổng hợp được
ghi riêng, không giả định là commit lịch sử của lượt test cũ.
XML thiếu, testcase không duy nhất, skipped/failure/error hoặc ảnh sai hash
đều không được gán PASS. Không dùng lời mô tả mongđợi làm bằng chứng đã chạy.

Kết quả ở `scripts/B09-test-results/ACCEPTANCE_<thời gian>/`:

- `results.csv`: mã, chức năng, tiền đề, bước, mongđợi, thựctế, kếtluận,
  phương pháp, bằng chứng và timestamp nguồn cho từngca.
- `report.html`: bảng đọc được, có thể mở bằng trình duyệt.
- `manifest.json`: chế độ chạy, phân bố9/6/7/3, sốca đạt, hash và snapshot
  nguồn lúc tổng hợp; tách24mappingtựđộng và1caảnhthủcông.
- `junit/`: XML gốc của các testcase được truy vết.

## Các việc vẫn phải ghi đúng giới hạn

Đây là nghiệm thu local, không tự xác nhận triển khai/hiệu năng online.
Kết quả Lighthouse/keyboard/mobile và cache/SQL ở các tài liệuB09đã có
được giữ riêng. Chưa được gọi Accessibility100 là hoàn tấtWCAGAA;
còn cần bảng đối chiếu tiêu chí và kiểm tra thủ công/screenreader.
Không thay tài khoản/mật khẩu thực hoặc dữ liệu ứng dụng để chạy bộ này.
