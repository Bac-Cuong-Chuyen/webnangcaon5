# B09 — Trang công khai, sitemap/robots, gzip và cache

Ngày19/10/2026. Nhánh `feature/b09-public-web`, bắt đầu từ masterbf6ff9c
(PR#26). URL triển khai do nhóm cung cấp: https://webnangcaon5.onrender.com.
Kết quả dưới đây là local, chưa xác nhận bản thay đổi này đã triển khai online.

## Thay đổi và cấu hình

- Trang chủ `/` và `/index.html` có title/description/OpenGraph/canonical.
  URL tuyệt đối lấy từ `app.public-base-url`, cấu hình qua biến môi trường
  `PUBLIC_BASE_URL`; mặc định local `http://localhost:8081`.
  Không dùng Host header từ request để sinh URL.
- `/sitemap.xml` sinh XML với duy nhất trang chủ công khai. `/robots.txt`
  khai báo sitemap và loại API, H2 console, trang báo cáo, trang nhật ký
  khỏi phạm vi crawl. Hai tệp được sinh động qua controller.
- Trang báo cáo/nhật ký có noindex,nofollow vì là giao diện dữ liệu theo
  quyền; nhật ký có description riêng. Chính sách robots không thay thế
  xác thực/phân quyền. Không lấy điểm SEO trang nội bộ làm mục tiêu lập chỉ mục.
- Thư viện `/vendor/**` hiện có tên gắn phiên bản, cache public1năm/immutable;
  khi nâng thư viện phải đổi URL. CSS/JS ứng dụng chưa gắn hash dùng
  no-cache/public để trình duyệt tái xác thực; hỗ trợ Last-Modified/304.
- Home public dùng no-cache/public; sitemap/robots dùng publicmaxage3600.
  HTML nội bộ và API giữ no-store. Không sửa SecurityConfig/RBAC/CSRF.
- Bật gzip từ1024byte cho HTML/CSS/JS/plain/XML. Không thêm MIME JSON
  vào danh sách nén. Không thay đổi hợp đồng báo cáo hoặc dữ liệu.

## Kiểm chứng thực tế

| Hạng mục | Kết quả |
|---|---|
| Hồi quy `mvn clean test` | 190tests, failures0/errors0/skipped0, BUILD SUCCESS lúc19:18:56+07,33.235s |
| B09PublicWebTest | 5/5PASS: home/OG/origin, sitemapXML/robots, cache304/vendor, noindex/privateHTML, quyền/cacheAPI |
| B09CompressionTest | 2/2PASS trên HTTP server thật cổng ngẫu nhiên, DBkiểmthửriêng |
| Gzip | 205419byte gốc ->69707byte nén; giải nén giống từngbyte; identity giữ nguyên |
| curl ứng dụng8081 | VendorHTTP200/gzip/maxage31536000/public/immutable; CSSHTTP200/no-cache/public/LastModified |
| Header bảo mật | nosniff, CSP, SAMEORIGIN, Referrer-Policy vẫn hiện trong ảnhcurl |

Kiểm thử HTTP cũng xác nhận robots nhỏ không nén và APIanonymousme401/no-store.
Tính hợp lệ XML, một loc công khai và nguồn URL cấu hình được kiểm bằng
integrationtest. Ảnhcurl local chỉ thấy cuối XML, chưa chứa toàn bộ nội dung
robots/sitemap; chưa coi là minh chứng truy cập trên tên miền online.

## Lighthouse Navigation gốc

[Báo cáo HTML](b09-public-evidence/localhost_8081-20261019T192227.html).

- Lighthouse13.4.1; gatherMode navigation;
  fetchTime2026-10-19T12:22:27.038Z (19:22:27+07).
- Final URL `http://localhost:8081/`, không có runtimeError hoặc runWarnings.
- Cấu hình formFactormobile, screenEmulation.disabled=true; không coi là
  kiểm thử điện thoại vật lý. Không suy diễn độ trễ mạng online từ local.
- Điểm: Performance100, Accessibility100, BestPractices100, SEO100.
- FCP758.59ms, LCP758.59ms, SpeedIndex758.59ms, TBT0ms, CLS0.
- Vẫn có hai gợi ý không làm giảm điểm: network dependency tree và
  render-blocking request cho index.css (transfer794byte, gợi ý154ms).
  Giữ stylesheet ngoài để phù hợp CSP và không sửa chỉ để loại gợi ý này.

Điểm trên thuộc trang giới thiệu công khai nhỏ. Không đại diện cho trang
báo cáo5000dòng, hiệu năngAPI, dữ liệu người dùng hoặc môi trường Render.
Accessibility100 không chứng nhận hoàn tất mọi tiêu chí WCAG2.1AA.

## Triển khai và kiểm tra tiếp

Khi triển khai commit này, đặt biến môi trường Render:

```text
PUBLIC_BASE_URL=https://webnangcaon5.onrender.com
```

Ứng dụng cần khởi động lại sau khi thay biến. Sau deploy, truy cập `/`,
`/sitemap.xml`, `/robots.txt` trên tên miền nhóm; sitemap/canonical/og:url
phải dùng HTTPS và đúng tên miền. Đối chiếu header gzip/cache ở môi trường
online riêng vì reverse proxy có thể thay đổi cách nén và header.
Không dùng localhost làm URL sitemap của bản online.

Phần này chưa thay thế bộ25ca nghiệm thu/API, kiểm tra accessibility thủ
công và các mục online còn lại của Buổi09. Kết quả đo5endpoint của PR#25
được giữ nguyên, không thay bằng điểm Lighthouse trang chủ.
