# Giao diện nghiệp vụ Mượn & Trả Admin — phiên bản sử dụng tại quầy

## Cơ sở thiết kế và phạm vi

Đối chiếu đặc tả `Dac_ta_chuc_nang_Library_Online_ban_hoan_thien.docx`, **Phân hệ 3 – Nghiệp vụ xử lý tại quầy** (mục 1, 2 và 3): thủ thư xác minh thẻ, xem đơn mượn Online đã duyệt, quét cuốn sách vật lý và xác nhận; mượn trực tiếp, trả sách và thu phạt là nghiệp vụ khác nhau. Tham khảo form/card, bố cục mobile của PDF Figma; **không lấy hệ màu từ Figma**.

Bộ màu chuẩn Login giữ nguyên: `#F5F7FC` (nền), `#FFFFFF` (thẻ/ô), `#DCE3EF` (viền), `#182230` (chữ chính), `#68758A` (chữ phụ), `#315EFB` (nút và lựa chọn).

## Cách dùng mới

- **Mượn Online:** Quét/nhập mã thẻ → xem các yêu cầu `Approved` / `Collected` của người đó → chọn đơn (nếu có nhiều) → quét/nhập mã cuốn → chờ hệ thống đối chiếu trên Firestore → nút **Xác nhận giao** *hoặc* **Xác nhận nhận trả** tương ứng mới xuất hiện. Thao tác vẫn yêu cầu xác nhận bằng dialog và transaction.
- **Tại quầy:** Dùng cùng mã thẻ và cuốn vừa quét. Chọn số ngày nếu mượn trực tiếp; có nút **Mượn sách** và **Trả sách** riêng, dữ liệu giao dịch trực tiếp tiếp tục dùng `CirculationRepository` (SQLite) như trước. Không thay đổi chính sách nghiệp vụ trong bản UI này.
- **Thu phạt:** Một khu riêng để nhập mã khoản phạt và xác nhận đã thu. Khi trả sách trực tiếp tạo `relatedId` khoản phạt, ứng dụng điền mã và tự chuyển đến khu thu phạt để thủ thư xử lý.
- **Kết quả:** Một khối thông báo phía cuối trang hiển thị kết quả thành công hoặc lỗi; vẫn giữ Toast để dễ nhận biết.

Các khối màn hình được sắp xếp thành các bước có thứ tự. Trạng thái thẻ hợp lệ và barcode đúng được hiển thị bằng màu thành công qua color selector trong `res/color`, không hardcode màu/điều khiển hình thức trong Java. Thẻ yêu cầu đang chọn có viền xanh.

## Các file giao diện

- `res/layout/view_admin_circulation.xml`: toàn bộ bố cục chính, chế độ, quét thẻ/cuốn, Online, trực tiếp, thu phạt và kết quả
- `res/layout/item_admin_counter_online_loan.xml`: thẻ đơn Online
- `res/values/circulation_styles.xml`: style thẻ, ô, tiêu đề, nút quét và nút chế độ
- `res/drawable/bg_circulation_mode.xml`, `bg_circulation_selected_card.xml`: selector lựa chọn
- `res/color/circulation_mode_text.xml`, `circulation_verification_text.xml`: màu trạng thái

Java chỉ điều khiển sự kiện, dữ liệu, `View.VISIBLE/GONE` và `setSelected`; không dựng widget mới bằng `new`.

## Kiểm thử thủ công

1. Vào Admin → Mượn trả, chọn **Mượn Online**. Khi chưa quét, khối danh sách và xác minh hiện thông điệp hướng dẫn, không hiển thị hai nút xác nhận vô hiệu.
2. Quét thẻ Reader có 2 đơn: kiểm tra tên, hạn nhận/hạn trả, ngày mượn, nhãn `CHỜ GIAO`/`ĐANG MƯỢN`. Chọn một thẻ, viền xanh chuyển đúng; quét cuốn sai thì giao/nhận trả không được kích hoạt.
3. Quét cuốn đúng sau khi Admin duyệt giữ suất đầu sách; nút **Xác nhận giao sách Online** xuất hiện và chỉ bật sau khi Firestore đã xác minh. Đối với đơn `Collected` là nút nhận trả, không phải giao.
4. Mở camera khi đang ở **Tại quầy**, quét rồi quay lại: ứng dụng giữ đúng tab, mã tự điền, có nút mượn/trả thường. Kiểm tra số ngày và kết quả thông báo như trước.
5. Quay lại camera ở mode **Thu phạt** hoặc chuyển tab, không tự xác nhận giao dịch khi quét. Sau trả trực tiếp phát sinh khoản phạt, mã được điền ở tab Thu phạt.
6. Trên emulator màn hình hẹp 320dp và thiết bị thật, kiểm tra chữ không chồng lấn, các nút quét lớn đủ bấm, cuộn và bàn phím không che thao tác.

## Điều chưa thay đổi

Chỉ refactor **giao diện**, không đổi Firestore Rules, tồn kho, hạn mượn hoặc transactions `collect/returnOnline`. Các yêu cầu đặc tả về quét liên tục nhiều cuốn, trả hàng loạt tự tích chọn và đồng bộ trực tiếp/offline của SQLite với Firestore vẫn là nhiệm vụ nghiệp vụ riêng; không được coi là hoàn tất vì UI này trông đầy đủ hơn.

Chạy `cd LibraryOnline && .\gradlew.bat :app:testDebugUnitTest :app:assembleDebug` và `python tools/check_ui_boundaries.py` trước khi đưa APK tới quầy. Các test layout/selector được bổ sung vào `XmlPresentationTest`.
