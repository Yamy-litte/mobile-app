# Mượn sách Online — luồng Reader / Admin / Firestore

## Phạm vi và chuẩn giao diện

Không dùng đặt trước, danh sách chờ hoặc trạng thái `DaDatTruoc`.
Figma chỉ tham khảo bố cục; tất cả giao diện mới nằm trong `res/layout/`, màu kế thừa Login trong `colors.xml`.
Firestore là nguồn chung giữa thiết bị Reader/Admin; **không tự tạo phiếu SQLite giả** khi duyệt/giao.

## Vòng đời

| Hành động | Firestore request | Cuốn sách | Tồn sẵn sàng | Thông báo |
|---|---|---|---|---|
| Reader gửi hợp lệ | Pending | Chưa giữ | Không đổi | Admin nhận tin trong app |
| Reader hủy khi chưa duyệt | Cancelled | Chưa giữ | Không đổi | Đơn biến mất khỏi hàng chờ |
| Admin từ chối | Rejected | Chưa giữ | Không đổi | Reader có lý do |
| Admin duyệt | Approved | SanSang → ChoLay | Trừ 1 | Reader nhận hạn đến lấy 24h |
| Admin giao sách (xác minh hai barcode) | Collected | ChoLay → DaMuon | Không đổi | Reader nhận hạn trả |
| Admin nhận trả (xác minh hai barcode) | Returned | DaMuon → SanSang | Cộng 1 | Reader nhận xác nhận |
| Hết hạn giữ sách | Expired | ChoLay → SanSang | Cộng 1 | Reader nhận tin hết hạn |

- Admin phải đối chiếu **mã thẻ Reader và mã cuốn vật lý** trước khi bấm giao/trả. Có thể nhập tay hoặc dùng đầu đọc barcode dạng bàn phím; chưa tích hợp quét camera trong màn hình giao Online.
- `OnlineLoanReceipts/{requestId}_v{attempt}` được tạo duy nhất khi giao sách thực tế; trạng thái Open → Returned. Bản ghi giữ nguyên khi Reader gửi lượt mượn khác sau khi trả.
- Hạn trả tính từ thời điểm Admin giao sách, không phải khi Reader gửi đơn hoặc Admin duyệt.
- Transaction kiểm tra lại trạng thái của đơn, cuốn và người mượn trước khi cập nhật; việc xử lý trùng hoặc lỗi đối soát sẽ thất bại, không tự ý tăng tồn kho.
- Khi đầu sách hoặc bản sao đang ẩn, thao tác nhận trả Online yêu cầu thủ thư đối soát thủ công, không tự động hoàn nguyên bản ẩn.
- Trường `BookTitles.availableCopies` chỉ giảm lúc duyệt, tăng lúc hết hạn/trả. Giao sách không giảm tồn lần thứ hai.

## Cấu hình, giới hạn chưa triển khai

1. **Firestore Rules phải được triển khai** để app có quyền tạo/đóng `OnlineLoanReceipts` và đổi `Collected/Returned/Cancelled`. Chỉ merge APK không đủ.
2. Chốt hạn 24 giờ hiện được tự kiểm tra khi giao diện Admin hoạt động, **chưa có bộ lập lịch máy chủ** cho trường hợp tất cả ứng dụng đều đóng. Firebase Functions đang ở chế độ không hoạt động trong dự án; muốn bảo đảm giải phóng đúng giờ cần triển khai job backend có kiểm soát và cấu hình billing phù hợp.
3. Chưa đồng bộ hai chiều bản ghi `OnlineLoanReceipts` sang bảng `LoanReceipts/LoanDetails` của từng thiết bị SQLite. Reader xem riêng phần lịch sử Online, Admin sử dụng giao/trả Online qua Firestore; **không dùng luồng mượn/trả SQLite cũ cho cuốn đang thuộc giao dịch Cloud**.
4. Hạn mức tổng giữa các phiếu SQLite không đồng bộ và các đơn online chưa được bảo đảm toàn hệ thống trong cùng một transaction; kiểm tra hạn mức trước khi giao cần một nguồn dữ liệu dùng chung và quy tắc tổng hợp. Sách trả trễ chưa được tự động tạo khoản phạt liên thiết bị; UI nhắc thủ thư đối soát.
5. Dữ liệu thanh lý, hỏng, ẩn và đặt trước cũ không được biến đổi/migration trong phạm vi này.

## Kiểm thử an toàn

Trước khi deploy:

```bash
cd LibraryOnline
python tools/check_ui_boundaries.py
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

Firestore Rules chạy **trên emulator cục bộ**, không dùng dữ liệu production:

```bash
cd LibraryOnline
npm --prefix tools/firestore-tests ci
npx --yes --package=firebase-tools@14.22.0 firebase emulators:exec --config firebase.emulators.json --project demo-library-online --only firestore 'npm --prefix tools/firestore-tests test'
```

Lệnh cuối yêu cầu Firebase CLI và Java được cấu hình trong môi trường phát triển; xác minh `tools/firestore-tests/README.md` trước khi chạy để dùng đúng script hiện có.

Thao tác deploy Rules phải được thực hiện riêng sau khi kiểm thử emulator, với project Firebase chính xác:

```bash
firebase deploy --only firestore:rules
```

Không cấp quyền thêm cho Reader được ghi BookCopies/BookTitles, không deploy hoặc ghi Firebase production trong bước sửa code này.

## Khắc phục khi bấm Gửi yêu cầu mà không thấy phản hồi (23/10/2026)

Đã sửa luồng và thông báo ở mã nguồn:

1. Trước đây Reader dùng `tx.get(OnlineLoanRequests/{uid}_{bookId})` ngay khi gửi yêu cầu lần đầu. Document lúc này **chưa tồn tại**, Rules cũ chỉ kiểm tra `resource.data.userId` nên có thể từ chối GET.
2. Android mới dùng **truy vấn owner** `whereEqualTo("userId", uid)` để tải danh sách và xác định đơn đã có hay chưa. Khi lần đầu tạo, transaction đọc User/BookTitle/Config rồi **set document mới mà không GET một document chưa tồn tại**. Khi gửi lại đơn cũ, transaction vẫn GET và kiểm tra trạng thái/attempt. Firestore Rules vẫn chặn ghi trùng. Cách này giúp **gửi Pending lần đầu hoạt động với cả Rules cũ**, miễn là query owner, Reader, sách và config đều hợp lệ.
3. Rules mới cũng tách `allow get` và `allow list` để cho phép GET của chính Reader đối với document chưa tồn tại, **không mở quyền đọc đơn hiện có của người khác**.
4. Nút `onlineBorrowConfirm` cũ bị vô hiệu hóa âm thầm nếu không có Internet/quyền Firestore hoặc chưa đọc cấu hình, quota. Giao diện mới có `onlineBorrowValidation`, `onlineBorrowRetry` và báo lý do cụ thể bằng Toast. Chỉ ghi sau khi tất cả kiểm tra hợp lệ; chỉ chuyển đến tab Mượn sách sau khi transaction Firestore báo thành công.

Nếu vẫn gặp `PERMISSION_DENIED`: cần chạy Firebase Emulator Rules tests và **deploy `firestore.rules` mới** vào đúng Firebase project. `git pull` chỉ cập nhật mã trên máy, không cập nhật Rules trên Firebase. Lỗi này không khắc phục được chỉ bằng Build lại APK.

Chu trình kiểm tra:
- Reader đăng nhập với thẻ `HoatDong`, mở chi tiết sách còn `availableCopies > 0`, vào Mượn Online.
- Dùng `Kiểm tra lại kết nối và điều kiện` nếu trang báo chưa xác minh.
- Nhấn Xác nhận: trước khi Firestore xác nhận hiển thị “Đang gửi…”. Thành công tự mở tab Mượn sách, danh sách Online có `Pending`; Admin mở mục Yêu cầu Online sẽ thấy đơn.
- Nếu không thành công, ghi lại chính xác mã lỗi trên màn hình. Không đánh dấu đơn là thành công khi Firestore báo lỗi.
