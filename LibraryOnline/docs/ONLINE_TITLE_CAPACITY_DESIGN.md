# Duyệt mượn Online theo suất đầu sách — chỉ chọn BookCopy khi giao

## Quyết định nghiệp vụ

Người mượn đăng ký **đầu sách** `BookTitles/{bookTitleId}`, chưa đăng ký một cuốn cụ thể. Admin duyệt **một suất** của đầu sách, không chọn `copyId`; đến lúc độc giả xuất trình thẻ tại quầy, Admin quét barcode gáy sách thì mới chọn `BookCopies/{copyId}`.

Đây là **giữ suất trong tồn kho mượn Online**, không phải tính năng đặt trước hoặc xếp hàng chờ đã bỏ khỏi dự án. Khi hết suất, Admin **không được duyệt thêm**; đơn còn ở `Pending`, có thể được xét lại sau khi có sách trả/nhập thêm.

## Hai bộ đếm trên BookTitles

- `totalCopies`: tổng cuốn vật lý (bao gồm cuốn mượn/sửa/ẩn).
- `availableCopies`: số **suất còn được duyệt/mượn**. Chỉ số này không bao gồm yêu cầu đã duyệt chưa giao.
- `reservedOnlineCopies` (mới; không có field được đọc là 0): số đơn Online `Approved` **chưa có cuốn cụ thể**.

Đối với các đầu sách có dữ liệu nhất quán:

```text
Số cuốn vật lý SanSang, không ẩn/priority = availableCopies + reservedOnlineCopies
```

Ví dụ đầu sách có 3 cuốn `SanSang`, `availableCopies=3`, `reservedOnlineCopies=0`.

| Bước | availableCopies | reservedOnlineCopies | BookCopies |
|---|---:|---:|---|
| Ban đầu | 3 | 0 | 3 × SanSang |
| Duyệt A | 2 | 1 | Vẫn 3 × SanSang |
| Duyệt B | 1 | 2 | Vẫn 3 × SanSang |
| Duyệt C | 0 | 3 | Vẫn 3 × SanSang |
| Duyệt D | **Từ chối** | 3 | Không vượt 3 suất |
| Giao A, quét cuốn bất kỳ đúng đầu sách | 0 | 2 | 2 × SanSang, 1 × DaMuon |
| B hết hạn 24 giờ chưa lấy | 1 | 1 | Không thay đổi trạng thái cuốn |
| A trả sách | 2 | 1 | 3 × SanSang |

**Lưu ý:** Các bước của nhiều Admin duyệt cùng lúc đều thực hiện bằng Firestore transaction cùng đọc/ghi `BookTitles/{bookTitleId}`. Firestore tự retry hoặc từ chối khi document đã thay đổi. Quy tắc bảo mật cũng bắt buộc `availableCopies > 0`, giảm 1 và tăng `reservedOnlineCopies` 1 trong cùng batch/transaction cùng với đơn được duyệt và thông báo.

## Quy tắc cập nhật giao dịch

1. **Duyệt** `Pending → Approved`: chỉ cập nhật request (`copyId` **không có**), `BookTitles.availableCopies -= 1`, `BookTitles.reservedOnlineCopies += 1`, thông báo Reader. BookCopies hoàn toàn không đổi.
2. **Giao sách** `Approved → Collected`: Admin quét **mã thẻ Reader và barcode một cuốn vật lý**. Transaction kiểm tra `copyStatus == SanSang`, `isHidden == false`, `priorityUserId == null`, `copy.bookTitleId == request.bookTitleId`. Chỉ bây giờ ghi `request.copyId`, cập nhật `copyStatus=DaMuon`, `reservedOnlineCopies -= 1`, tạo `OnlineLoanReceipts`. Không trừ `availableCopies` lần thứ hai.
3. **Hết hạn** `Approved → Expired`: `availableCopies += 1`, `reservedOnlineCopies -= 1`; không thay đổi BookCopies, vì chưa có cuốn nào bị giữ riêng.
4. **Trả sách** `Collected → Returned`: đối chiếu đúng `copyId` đã ghi lúc giao, cập nhật cuốn `SanSang` và `availableCopies += 1`; `reservedOnlineCopies` giữ nguyên.
5. **Từ chối** `Pending → Rejected`: không động vào hai bộ đếm. **Reader hủy Pending** cũng không đổi tồn kho.

## Tương thích đơn Approved đã có trước thay đổi

Các đơn `Approved` cũ đã có `copyId` và cuốn `ChoLay` vẫn được nhận hoặc hết hạn theo nhánh cũ, **không gán lại cuốn khác**. Chúng không thuộc `reservedOnlineCopies` mới. Khi đã được giao hoặc hết hạn, dữ liệu sẽ tự hội tụ về cơ chế mới cho lượt tiếp theo. Không xóa `copyId` khỏi đơn cũ trong quá trình nâng cấp.

## An toàn và giới hạn vận hành

- `InventoryPolicy.audit()` đã được đổi để kiểm tra `ready = available + reserved`, tránh coi sự khác biệt hợp lệ là mất dữ liệu kho. Nhập sách, hiện lại cuốn sửa chữa và thanh lý vẫn thao tác trên `availableCopies` cùng document và phải đi qua các kiểm tra tồn kho.
- `reservedOnlineCopies` phải là số nguyên không âm, và `availableCopies + reservedOnlineCopies <= totalCopies`.
- `BookTitles.availableCopies` chỉ an toàn khi **mọi luồng cấp phát cuốn vật lý** dùng chung bộ đếm Firestore. Luồng mượn/trả trực tiếp SQLite trên thiết bị khác chưa đồng bộ cùng nguồn; tuyệt đối không coi nó là cam kết đồng bộ kho toàn hệ thống.
- Job trả suất sau 24 giờ vẫn được kích hoạt khi Admin đang chạy app, chưa phải Cloud Function chạy khi không có máy nào mở.
- Thao tác reset thủ công `availableCopies`, `reservedOnlineCopies` hoặc thay `copyStatus` trực tiếp trong Firestore Console có thể phá vỡ bất biến. Cần đối soát dữ liệu và hạn chế chỉnh tay khi triển khai thực tế.

## Kiểm thử & triển khai

Sau khi kéo code mới, chạy kiểm thử Android (bao gồm `OnlineBorrowCopyMatchTest` và `InventoryPolicyTest`):

```powershell
cd LibraryOnline
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

Firestore Rules tests qua **emulator**, sau đó mới triển khai `firestore.rules` theo đúng Firebase project. **Cần cập nhật cả ứng dụng lẫn Rules** vì schema có thêm `reservedOnlineCopies`; nếu chưa deploy Rules mới thì quá trình duyệt theo cơ chế mới sẽ bị từ chối.
