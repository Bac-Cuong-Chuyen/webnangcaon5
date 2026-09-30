# Bảng test case Buổi 5 (B05-06)

Điều kiện: đã đăng nhập bằng tài khoản Nhân viên kho (trừ TC08). Test tự tạo 1 Product và 1 Warehouse trước mỗi ca rồi rollback, nên không phụ thuộc dữ liệu mẫu.

| Mã | API | Mô tả | Input | Kết quả mong đợi | HTTP | Kết quả thực tế | Trạng thái |
|---|---|---|---|---|---|---|---|
| TC01 | POST /api/imports | Nhập kho hợp lệ | quantity=10 | Tạo phiếu nhập + chi tiết, Inventory = 10 | 200 | | |
| TC02 | POST /api/exports | Xuất kho hợp lệ | Nhập 10, xuất 5 | Tạo phiếu xuất, Inventory = 5 | 200 | | |
| TC03 | POST /api/imports | Product không tồn tại | productId=999999 | code=RESOURCE_NOT_FOUND, không tạo phiếu | 404 | | |
| TC04 | POST /api/imports | Warehouse không tồn tại | warehouseId=999999 | code=RESOURCE_NOT_FOUND, không tạo phiếu | 404 | | |
| TC05 | POST /api/imports | Quantity = 0 | quantity=0 | code=VALIDATION_ERROR | 400 | | |
| TC06 | POST /api/imports | Quantity âm | quantity=-5 | code=VALIDATION_ERROR | 400 | | |
| TC07 | POST /api/exports | Xuất vượt tồn kho | Nhập 10, xuất 1000000 | code=BUSINESS_CONFLICT, Inventory vẫn = 10 | 409 | | |
| TC08 | POST /api/imports | Chưa đăng nhập gọi API | Không đăng nhập | Bị từ chối | 401 | | |
| TC09 | POST /api/exports | Thiếu productId | productId=null | code=VALIDATION_ERROR | 400 | | |
| TC10 | POST /api/imports | Quantity sai kiểu | quantity="abc" | code=INVALID_REQUEST_BODY | 400 | | |

Chạy: `mvn test -Dtest=StockApiValidationTest`, chụp ảnh BUILD SUCCESS làm minh chứng.
