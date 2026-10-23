# Hoàn thiện Home Admin & tab Sách của tôi (Reader)

## Căn cứ

- `Dac_ta_chuc_nang_Library_Online_ban_hoan_thien.docx`: Phân hệ 2 mô tả tra cứu, yêu cầu mượn Online và theo dõi sách đang mượn; Phân hệ 5 mô tả bảng điều khiển Admin và công việc quản trị.
- `Thiết kế giao diện-Figma (1).pdf`: trang *Sách của tôi* tổ chức bằng các tab trạng thái và thẻ sách dễ đọc; Home có thông tin trọng tâm trước các hành động.
- Yêu cầu cập nhật của dự án **loại bỏ tính năng đặt trước**; bởi vậy thay tab *Đặt trước* của Figma bằng **Chờ duyệt** và **Lịch sử**, không thêm hàng đợi trước. Đơn được duyệt chỉ giữ *suất đầu sách*, BookCopy được gán khi Admin giao, như PR #15.
- Giữ palette Login: nền `#F5F7FC`, thẻ `#FFFFFF`, viền `#DCE3EF`, chữ `#182230`/`#68758A`, xanh `#315EFB`.

## Home Admin

Khối **Ưu tiên xử lý tại quầy** nằm ngay dưới tiêu đề Dashboard:

| Chỉ số | Nguồn | Điều hướng |
| --- | --- | --- |
| Chờ duyệt | `OnlineLoanRequests.status=Pending` | Duyệt yêu cầu → màn hình quản lý yêu cầu Online |
| Chờ giao | `status=Approved` | Giao/Trả sách → màn hình barcode tại quầy |
| Đang mượn | `status=Collected` | Giao/Trả sách → màn hình tại quầy |

Dữ liệu đến từ **chính snapshot của `AdminOnlineLoanController` đang dùng để duyệt**; không tạo listener/aggregate Firestore trùng lặp. Khi còn cache hoặc pending writes phải hiện thông báo *đang chờ đồng bộ*. Khi listener gặp lỗi hiển thị dấu `—`, không giả lập số liệu bằng 0. Không thay thế các thống kê sách/độc giả/biểu đồ/báo cáo và nút hiện có.

## Reader → tab Mượn sách → Sách của tôi

Bốn trạng thái có tabs XML cuộn ngang để sử dụng được trên màn hình hẹp:

- **Đang mượn:** `OnlineLoanRequests.status=Collected`, hiện hạn trả/mã cuốn thực tế; tiếp theo là **Sách mượn tại quầy (dữ liệu trên thiết bị)**. Số đếm trên nút chỉ tính đơn Online, không cộng nhầm phiếu SQLite.
- **Chờ lấy:** `Approved`, hiện số ngày yêu cầu, hạn nhận, bộ đếm giờ, thông điệp *đã giữ suất đầu sách – mã cuốn xác định khi nhận tại quầy*. Không dùng chức năng đặt trước.
- **Chờ duyệt:** `Pending`, hiện sách đã gửi, ngày gửi; nút hủy chỉ bật khi snapshot Firestore hợp lệ (không phải cache/ghi chưa xác nhận); transaction hủy giữ nguyên.
- **Lịch sử:** `Returned`, `Rejected`, `Expired`, `Cancelled` và các `OnlineLoanReceipts.status=Returned` đã hoàn tất từ các lượt giao trả trước đó. `OnlineLoanReceipts.status=Open` được xem ở Đang mượn, không lặp lại trong lịch sử.

Các danh sách dùng snapshot/listener sẵn có và lọc trạng thái khi nhấn tab; không tạo request server riêng khi đổi tab. Nhãn số lượng/empty state được cập nhật từ snapshot, tab đang xem được lưu lại khi Activity bị tạo lại. Khu vực thông báo riêng không thay đổi.

## Giao diện & kiểm thử

- XML: `view_reader_loans.xml`, `item_reader_online_request.xml`, `item_reader_online_receipt.xml`, `item_reader_loan_record.xml`, `activity_admin_main.xml`.
- Style: `reader_loans_styles.xml`, `bg_reader_loan_tab.xml`, `reader_loan_tab_text.xml`.
- Controller: `ReaderOnlineLoanController` xử lý filter/đếm; `ReaderMainActivity` lưu lại tab; `AdminMainActivity` chỉ nhận thống kê để hiển thị; `AdminOnlineLoanController` dùng snapshot sẵn có.
- `XmlPresentationTest` có thêm kiểm tra các ID layout và ColorStateList.
- Không thêm camera trên Reader, không thay Firestore Rules hoặc các giao dịch mượn/trả.

### Kịch bản kiểm thử thủ công

1. Mở Home Admin bằng tài khoản quản trị. Tạo `Pending` từ Reader → KPI chờ duyệt tăng; Admin duyệt → chờ duyệt giảm và chờ giao tăng; xác nhận giao → chờ giao giảm và đang mượn tăng; trả → đang mượn giảm. Mất mạng phải hiện *dữ liệu đã lưu*, không khẳng định realtime.
2. Bấm **Duyệt yêu cầu** → tới vùng danh sách duyệt Online; bấm **Giao / Trả sách** → tới giao diện quét mã thẻ/cuốn. Kiểm tra khi đăng xuất/đăng nhập lại.
3. Reader mở tab Mượn sách: thử lần lượt Đang mượn, Chờ lấy, Chờ duyệt và Lịch sử với một tài khoản có dữ liệu. Sau khi gửi yêu cầu, chỉ Chờ duyệt tăng; sau duyệt Chờ lấy có bộ đếm nhận; sau giao Đang mượn có hạn trả; sau trả Lịch sử hiển thị phiếu đã hoàn tất.
4. Chờ duyệt có nút hủy; các trạng thái khác không có nút hủy. Hủy không làm thay đổi số cuốn tồn kho.
5. Kiểm tra Reader nếu chỉ có phiếu mượn SQLite cục bộ: thông tin hiển thị ở Đang mượn nhưng có ghi chú nguồn dữ liệu và **không được cộng** vào số yêu cầu Online trên tab.
6. Xoay màn hình hoặc để Android tạo lại Activity: màn hình Mượn sách phải giữ đúng trạng thái đã chọn. Thử trên emulator 320dp và điện thoại thật.

### Chạy build/tests

```powershell
cd LibraryOnline
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
python tools/check_ui_boundaries.py
```

Lưu ý: Chưa triển khai thêm chức năng *gia hạn sách* nêu trong đặc tả; việc xếp hàng đặt trước vẫn bị loại bỏ theo quyết định mới. Đây là thay đổi Home Admin và giao diện Sách của tôi, không phải cập nhật tất cả nghiệp vụ được liệt kê trong đặc tả.
