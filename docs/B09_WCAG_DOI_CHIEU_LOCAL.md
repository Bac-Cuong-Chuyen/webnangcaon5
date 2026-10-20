# B09 – Đối chiếu accessibility và kiểm chứng local

Ngày kiểm tra: 20/10/2026. Phạm vi sửa: trang báo cáo kho và nhật ký hệ thống; đối chiếu thêm minh chứng trang chủ đã lưu. Kết quả là bảng kiểm theo Phụ lục 09, chưa phải chứng nhận toàn bộ WCAG 2.1 AA.

## Thay đổi

Sáu tệp static reports.html/css/js và audit-logs.html/css/js: tăng tương phản viền ô nhập sáng/tối; liên kết lỗi với ô bằng aria-describedby; đánh dấu aria-invalid và đưa focus về ô ngày sai; bỏ lỗi cũ khi sửa/xóa bộ lọc; liên kết lỗi đăng nhập; bổ sung caption, th scope và vùng bảng nhật ký nhận focus. Bảng nhật ký cuộn trong vùng riêng ở màn hình hẹp. Không sửa API, quyền truy cập, dữ liệu hoặc pom.xml.

## Đối chiếu yêu cầu

| Hạng mục | Đối chiếu | Kết quả và giới hạn |
|---|---|---|
| Tiêu đề/mô tả riêng | Phụ lục09; 2.4.2 | Ba trang đã có title/description riêng ở PR27. Lighthouse trang chủ Navigation bốn mục100; báo cáo Snapshot SEO4/4 áp dụng, không phải lần đo mới của bản sửa này. |
| Nhãn và cấu trúc bảng | 1.3.1; 3.3.2 | Nhãn input, main/h1, caption/th scope; vùng bảng có tên và nhận focus. Đã rà source và QA fixture. |
| Biểu đồ có nội dung thay thế | 1.1.1 | Canvas báo cáo có tên, mô tả và bảng dữ liệu thay thế. Không có ảnh thông tin độc lập cần alt trong ba trang đã rà. |
| Tương phản | 1.4.3; 1.4.11 | Đo10 cặp màu chữ/viền báo cáo sáng/tối trong fixture: chữ≥4,5:1; viền≥3:1. Viền sáng so với nền ô tăng1,277→3,646:1. Chưa suy rộng mọi màu/trạng thái toàn hệ thống. |
| Bàn phím/focus | 2.1.1; 2.1.2; 2.4.7 | Người dùng xác nhận vòng đăng xuất/đăng nhập/tab báo cáo/bộ lọc/bảng/phân trang, focus rõ và thoát khỏi bảng. Ghi nhận lời xác nhận, không có bản ghi từng phím. |
| Báo lỗi/hướng dẫn | 3.3.1; 3.3.3 | Ảnh local thật xác nhận lỗi khoảng ngày báo cáo và thời điểm nhật ký, hướng dẫn sửa và focus. Sửa ngày báo cáo tải lại5000 dòng; reset nhật ký xóa hai ô và lỗi cũ. |
| Phóng to | 1.4.4 | Ảnh Edge200% và trang1/2, các điều khiển vẫn hiện; người dùng xác nhận Excel tải được. Giới hạn các trạng thái đã quan sát. |
| Mobile/reflow | 1.4.10 | Ảnh390px trước đây; QA fixture320px hai trang không tràn ngang toàn trang. Bảng dữ liệu có vùng cuộn ngang riêng. |
| Giãn chữ | 1.4.12 | QA fixture dùng line-height1,5, letter-spacing0,12em, word-spacing0,16em và khoảng sau đoạn2em ở1280/320px: không tràn ngang toàn trang, không cắt chữ nút đang hoạt động. Chưa rà mọi thành phần bằng người dùng thật. |
| Nhãn/role/trạng thái với trình đọc màn hình | 4.1.2; 4.1.3 | Người dùng xác nhận Narrator đọc nhãn ngày và lỗi khoảng ngày của báo cáo. Không có bản ghi âm, chưa đánh giá mọi luồng trên mọi trang. |
| Kiểm tự động | Phụ lục09 | Có báo cáo Lighthouse gốc PR26/27; QA bổ sung17 mục fixture PASS. Không đánh đồng số kiểm tra đạt với toàn bộ WCAG AA. |
| Sitemap/robots online | Phụ lục09 | Online còn chờ TV5 xác minh build/commit sau ảnh RESOURCE_NOT_FOUND. Không thuộc kết luận local này. |

## Minh chứng và cách hiểu

Trong docs/b09-accessibility-evidence: bốn ảnh người dùng, manual-results.json ghi8 kịch bản cùng loại chứng cứ, assistant-fixture-qa.json ghi17 mục kiểm riêng và hash sáu tệp static. Các xác nhận tải Excel/bàn phím/Narrator/reset do người dùng báo, không được thay bằng ảnh giả lập hoặc mô tả thành kiểm tự động. Ảnh sửa ngày tải5000 dòng và bộ ảnh đầy đủ được lưu trong bộ B09-minh-chung phục vụ báo cáo Word cuối.

QA fixture sử dụng Edge headless, máy chủ cổng tạm và HTTP/dữ liệu giả lập; không truy cập H2 thật hoặc Render. Đã kiểm tổng5000 dòng, bảng50/chart20, Excel A1:C5001, phiên đăng nhập, quyền403/hết phiên401, lỗi ngày, vùng bảng/focus, sáng/tối và giãn chữ. Thông tin này xác nhận hành vi frontend dưới các phản hồi giả lập, không thay thế kiểm backend.

190 Java tests đã đạt ở mốc public-web trước bản sửa static. Không ghi vừa chạy lại190tests; bản sửa không thêm test Java hoặc thay backend. CI của PR mới cần đối chiếu theo commit thực tế khi có.

## Nguồn đối chiếu

- W3C [Non-text Contrast](https://www.w3.org/WAI/WCAG21/Understanding/non-text-contrast.html): chi tiết giao diện cần để nhận biết có tương phản tối thiểu3:1 với màu kề.
- W3C [Resize Text](https://www.w3.org/WAI/WCAG21/Understanding/resize-text.html): phóng chữ tới200% không mất nội dung/chức năng.
- W3C [Reflow](https://www.w3.org/WAI/WCAG21/Understanding/reflow.html): đối chiếu chiều rộng320CSSpx, có ngoại lệ cho nội dung cần bố cục hai chiều như bảng dữ liệu.
- W3C [Labels or Instructions](https://www.w3.org/WAI/WCAG21/Understanding/labels-or-instructions.html) và [Error Suggestion](https://www.w3.org/WAI/WCAG21/Understanding/error-suggestion.html).
