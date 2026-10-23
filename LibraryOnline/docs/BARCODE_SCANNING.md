# Quét barcode bằng camera — Library Online

## Tích hợp

- SDK: ZXing Android Embedded 4.3.0 dùng `ScanContract` / `ScanOptions`, decoder Core 3.5.3 đang dùng để in mã Code 128.
- Android minSdk 23 giữ nguyên; bật core-library desugaring để tương thích các Java API mới hơn của ZXing trên API 23.
- `LibraryBarcodeScanner` ở `ui/scanner` đăng ký Activity Result một lần trong `onCreate` và phát kết quả một lần. `LibraryBarcodeCaptureActivity` dùng `res/layout/activity_library_barcode_scan.xml`, `view_library_barcode_camera.xml`; không dựng layout động bằng Java.
- Hỗ trợ Code 128 (nhãn sách/thẻ được in), Code 39, Code 93, EAN-13, EAN-8 (ISBN thường gặp) và QR chứa **mã thuần**. Không giải mã URL/QR liên kết lạ thành lệnh.
- CAMERA permission chỉ được xin khi mở scanner qua library; không có camera/quyền camera thì vẫn nhập bằng tay. Thiết bị không có camera vẫn có thể cài ứng dụng nhờ `uses-feature required=false`.

## Điểm sử dụng

| Màn hình | Quét | Sau quét |
| --- | --- | --- |
| Reader trang chủ | ISBN hoặc Code128 của cuốn | ISBN → lọc đầu sách tại chỗ; mã cuốn → đối chiếu BookCopies, kiểm tra BookTitles không ẩn rồi mở chi tiết |
| Admin kho sách | ISBN hoặc barcode cuốn | ISBN → danh sách cuốn của đầu sách; mã cuốn → chi tiết cuốn; không tìm thấy có thể nhập mã và làm mới dữ liệu |
| Admin mượn/trả trực tiếp | Thẻ độc giả + mã cuốn | Điền đúng hai ô; **không tự thực hiện borrow/return**. Admin phải kiểm tra và bấm nút xác nhận |
| Admin giao/trả Online | Thẻ độc giả + mã cuốn `ChoLay` / `DaMuon` | Điền đúng hai ô trên request hiện tại; việc lưu lên Firestore chỉ bắt đầu khi Admin bấm nút xác nhận |

Lưu ý: ISBN thuộc **đầu sách**, Code128 dán trên từng **cuốn**, không được xem hai ID đó là giống nhau. Mã vạch thẻ độc giả được so khớp với trường `Users.barcode`, mã cuốn với `BookCopies.barcode`, không dùng UID hay BookTitle ID để giả mạo mã.

## Kiểm thử

1. Sau khi kéo main về, chạy `./gradlew :app:assembleDebug :app:testDebugUnitTest`.
2. Trên điện thoại thật hoặc emulator bật **camera mô phỏng**, vào Reader, quét ISBN mẫu và quét barcode cuốn để mở chi tiết (cần bản ghi BookCopies đã đồng bộ).
3. Ở Admin Kho, quét nhãn Code128 do `BarcodeLabels` tạo, đối chiếu cuốn đúng; thử ISBN mở danh sách cuốn.
4. Ở Admin Mượn/Trả tại quầy, quét mã thẻ rồi mã cuốn, kiểm tra ô đã điền; thử **Hủy quét** và từ chối camera để chắc chắn không có giao dịch tự phát.
5. Ở Online, duyệt yêu cầu, quét hai mã, sau đó bấm xác nhận giao; kiểm tra `Collected`, và lặp lại khi trả để đạt `Returned`. Các Rules và tồn kho Firestore phải đã cấu hình và triển khai.
6. Kiểm tra màn hình quét theo chuẩn Login: nền `#F5F7FC`, mặt nổi `#FFFFFF`, chữ `#182230/#68758A`, nút xanh `#315EFB`.

**Giới hạn:** Chưa tích hợp camera quét liên tục nhiều mã cùng lúc, quét ảnh tĩnh từ gallery, chế độ offline tìm toàn bộ Firestore hoặc tự đồng bộ SQLite/Cloud. Quét đơn lẻ để tránh giao dịch trùng. Không thay thế kiểm tra nghiệp vụ khi giao/trả.
