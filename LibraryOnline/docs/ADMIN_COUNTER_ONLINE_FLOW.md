# Admin tại quầy — nhận diện yêu cầu mượn Online và đối chiếu barcode

## Nguồn thiết kế và phạm vi

Theo phần **PHÂN HỆ 3 — NGHIỆP VỤ XỬ LÝ TẠI QUẦY**, mục *Xử lý đơn Mượn Online* trong `Dac_ta_chuc_nang_Library_Online_ban_hoan_thien.docx`, Admin quét mã thẻ để hiện các sách Reader đăng ký từ xa, kèm **số ngày mượn**; sau đó quét **barcode gáy sách vật lý** để giao. Thiết kế bảng dữ liệu phân biệt `BookTitles` (đầu sách) và `BookCopies` (cuốn vật lý).

Đã bỏ chức năng *đặt trước* theo yêu cầu dự án hiện tại: màn hình dưới đây chỉ xử lý `OnlineLoanRequests` đã duyệt và đang mượn.

## Luồng trong tab Mượn / Trả tại quầy

1. Admin chọn **Quét mã thẻ**. Camera trả mã thẻ, app tự điền vào `inputCirculationReaderBarcode`.
2. App tra `Users.barcode` trên **Firestore server**; kiểm tra duy nhất một Reader, thẻ `HoatDong`, chưa hết hạn.
3. App tra `OnlineLoanRequests` của chính `userId`; hiển thị các yêu cầu `Approved` (chờ giao) và `Collected` (đang mượn) cùng **tên đầu sách, ID đầu sách, số ngày đã chọn, hạn đến nhận/hạn trả**. Nếu chỉ có một yêu cầu, tự chọn.
4. Admin chọn yêu cầu (nếu có nhiều), rồi quét mã cuốn bằng nút **Quét mã cuốn**. Camera tự điền `inputCirculationCopyBarcode`. Nếu nhiều đơn mới chưa gán cuốn, Admin chọn đúng yêu cầu trước; đối với đơn cũ đã gán `copyId`, quét đúng mã cuốn có thể tự xác định.
5. App tra `BookCopies.barcode` trên server và **bắt buộc**:
   - `BookCopies.bookTitleId == OnlineLoanRequests.bookTitleId` (đúng đầu sách).
   - Đơn mới Approved **chưa có `copyId`**: chỉ chấp nhận cuốn `SanSang`, chưa có `priorityUserId`, và đúng `bookTitleId`; khi giao mới gán `copyId`.
   - Đơn Approved cũ đã có `copyId`: chỉ chấp nhận chính cuốn `ChoLay` đã được giữ cho Reader; không được gán lại.
   - Khi trả sách Collected: phải quét chính `request.copyId` đã gán khi giao, trạng thái `DaMuon`. Không chấp nhận cuốn bị ẩn hoặc đơn đã hết hạn.
   - Nếu không thỏa, nút xác nhận vô hiệu và hiển thị lý do rõ ràng như *SAI ĐẦU SÁCH* hoặc *ĐÚNG ĐẦU SÁCH, SAI CUỐN*.
6. Sau khi hai barcode hợp lệ, Admin bấm **Xác nhận giao sách Online** hoặc **Xác nhận nhận trả sách Online**, xem hộp thoại tóm tắt rồi xác nhận một lần nữa.
7. `OnlineLoanRequestRepository.collect()` / `returnOnline()` kiểm tra lại trong Firestore transaction, cập nhật trạng thái, phiếu `OnlineLoanReceipts`, cuốn, tồn kho (khi trả) và thông báo; chỉ khi server báo thành công mới hiển thị thành công.

Tab Mượn / Trả thường (SQLite) vẫn có nút `Mượn sách`/`Trả sách` cho luồng **mượn trực tiếp**; khi chọn một đơn Online ứng dụng nhắc sử dụng đúng nút xác nhận Online để không nhầm cơ chế lưu.

## Các điểm cần kiểm thử trên Android Studio và Firebase

- Quét thẻ A: chỉ thấy các đơn Approved/Collected của A, không thấy đơn của B.
- Thẻ bị khóa, thẻ hết hạn, barcode không thuộc ai và trùng barcode trên nhiều Users: không được chọn hay xác nhận giao.
- Reader A có một đơn: tự chọn, số ngày mượn giữ nguyên số Reader đã chọn (ví dụ 3/7/14), **không** lấy số ngày nhập trong form mượn trực tiếp.
- Đúng đầu sách và cuốn bất kỳ `SanSang` chưa ưu tiên: có thể giao nếu đơn mới còn hạn và vẫn còn `reservedOnlineCopies` cho suất đó. Quá trình giao ghi `copyId` vào đơn.
- Quét mã cuốn thuộc **đầu sách khác**: thông báo *SAI ĐẦU SÁCH*, cấm xác nhận.
- Đơn mới Approved chưa có `copyId`: quét cuốn khác cùng đầu sách đang `SanSang` được phép giao. Nếu cuốn `DaMuon`, bị ẩn hoặc có ưu tiên thì chặn.
- Đơn cũ đã có `copyId` hoặc đơn `Collected`: quét cuốn khác cùng đầu sách → *ĐÚNG ĐẦU SÁCH, SAI CUỐN*, cấm xác nhận.
- Admin scan thẻ, camera đóng, màn hình xác minh lại phiên: danh sách Approved được tải lại; quét cuốn sau đó vẫn đối chiếu đúng.
- Đơn đã giao: hiện `Collected` và hạn trả; quét chính mã cuốn, bấm **Xác nhận nhận trả** → `Returned`, tồn tăng đúng một lần.
- Thử trùng lượt giao/trả, offline, đơn hết hạn 24 giờ, cuốn bị ẩn: transaction phải từ chối, không thay đổi tồn kho nửa chừng.

### Chạy test

```powershell
cd LibraryOnline
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

`OnlineBorrowCopyMatchTest` kiểm thử trực tiếp đối chiếu đầu sách/cuốn. `XmlPresentationTest` kiểm tra panel và item XML.

**Giới hạn:** Đây là hoàn thiện luồng **giao/trả yêu cầu Online đã duyệt tại quầy**. Luồng mượn trực tiếp nhiều cuốn và tự chọn hàng loạt khi trả, tiền phạt SQLite, job hết hạn 24 giờ khi không máy nào mở chưa được thay đổi. Các Rules mới cho giao/trả Online của PR trước vẫn phải deploy lên Firebase project sau khi kiểm thử bằng Firestore Emulator.
