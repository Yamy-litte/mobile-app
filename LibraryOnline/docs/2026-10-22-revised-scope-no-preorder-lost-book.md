> **TÀI LIỆU LỊCH SỬ — ĐÃ ĐƯỢC THAY THẾ VỀ PHẦN MẤT SÁCH (23/10/2026).** Quyết định mới nhất: bỏ toàn bộ xử lý mất sách, chỉ giữ phạt trả muộn. Xem [2026-10-23-scope-late-return-only.md](2026-10-23-scope-late-return-only.md). Không triển khai state machine báo mất dưới đây. Dữ liệu legacy vẫn được giữ nguyên.

# Phạm vi cập nhật — không Đặt trước, quy trình Báo mất sách

Ngày thống nhất: 2026-10-22. Căn cứ: yêu cầu trực tiếp của người dùng thay thế các phần Đặt trước và Báo mất trong đặc tả cũ. Đây là tài liệu **định nghĩa hành vi cần triển khai**, không phải bằng chứng tính năng đã hoạt động.

## 1. Ngoài phạm vi mới: Đặt trước

- **Không phát triển** thao tác Đặt trước, hàng đợi ưu tiên, quyền nhận sách 48 giờ và thông báo sách đặt trước cho Reader hay Admin.
- **Giữ Mượn Online** khi có sách sẵn sàng, cùng thời hạn đến nhận 24 giờ và nghiệp vụ trả/mượn trực tiếp.
- Không tạo bản ghi `reservationType=DatTruoc` hoặc `copyStatus=DaDatTruoc` mới.
- **Không xóa, không sửa hàng loạt** bản ghi `DatTruoc`/`DaDatTruoc` cũ. Dữ liệu lịch sử và các trạng thái legacy vẫn phải đọc được; mọi phương án giải quyết hàng chờ tồn đọng cần đối soát riêng.
- Các trường `reservationHoldHours`, `lostBookMultiplier` đang là thành phần schema/config legacy; **không đổi tên, xóa hay tự migration** chúng. Ẩn chúng khỏi UI cấu hình để không quảng bá chức năng ngoài phạm vi.

## 2. Báo mất sách — state machine được chấp thuận

Trạng thái ở đây thuộc về **yêu cầu báo mất**, không phải giá trị `BookCopies.copyStatus` hiện hữu.

| Giai đoạn | Tác nhân / thao tác | Trạng thái yêu cầu | Hiệu ứng |
|---|---|---|---|
| 1 | Reader chọn cuốn trong Sách đang mượn, nhấn **Báo mất sách**, nhập lý do, tích **cam kết bồi thường**, rồi Gửi | `Pending` — **Chờ xử lý mất** | Thông báo yêu cầu cho Admin. Chưa ghi sách là `DaMat`; chưa khóa quyền mượn tại bước gửi. |
| 2 | Admin mở yêu cầu, đối chiếu đúng người/phiếu mượn/cuốn và nhấn **Xác nhận xử lý** | `Processing` — **Đang xử lý** | Chỉ tại thời điểm xác nhận này mới **khóa mượn tạm thời** cho độc giả. Lưu Admin và thời gian xác nhận. |
| 3 | Admin nhận sách thay thế hoặc tiền bồi thường **thực tế**, chọn hình thức và nhấn **Hoàn thành đền bù** | `Completed` — **Hoàn thành** | Kết thúc khoản mượn bị mất; bản sách trở thành `DaMat`, không còn khả dụng, không được xóa dữ liệu lịch sử. Gỡ riêng khóa mượn phát sinh từ yêu cầu này; chỉ mở lại quyền mượn khi không còn lý do khóa khác. |

### Dữ liệu tối thiểu của yêu cầu

`reportId`, `userId`, `loanDetailId`, `loanReceiptId`, `copyId`, `bookTitleId`, `reason`, `commitmentAccepted`, `status`, `createdAt`, `processedBy`, `processedAt`, `completedBy`, `completedAt`, `compensationMethod`, `amountReceived`.

- Một chi tiết mượn chỉ được có **một yêu cầu chưa hoàn thành**. Yêu cầu gửi lại do mạng không được tạo nhiều bản hay khóa nhiều lần.
- Chỉ chủ tài khoản sở hữu khoản mượn thực tế mới được gửi yêu cầu. Lý do không được trống; cam kết bắt buộc.
- Chỉ Admin đang hoạt động mới có quyền xác nhận, ghi bồi thường và chuyển trạng thái. Chặn nhảy trạng thái, chỉnh trạng thái về trước hay tự xác nhận của Reader.
- Hình thức bồi thường là **tiền** hoặc **sách thay thế**. Số tiền đã thu phải là số thực nhận và được Admin xác nhận; yêu cầu mới **không quy định nhân giá bìa với 2**. Nếu nhận sách thay thế, phải kiểm kê/cấp mã cuốn mới riêng, không tái sử dụng mã cuốn đã mất.
- Không dùng việc sửa `Users.cardStatus` đơn độc làm khóa tạm vì hiện `BiKhoa` có thể bao gồm khóa thủ công và khóa do nợ phạt. Cần nguyên nhân khóa tách biệt để không vô tình mở thẻ vi phạm khác.

### Quy tắc tồn kho và lịch sử

- Trước bồi thường: giữ `BookCopies.copyStatus` là đang mượn và duy trì khoản mượn chưa kết thúc; chỉ đánh dấu trạng thái yêu cầu `Pending/Processing`.
- Sau bồi thường: ghi `BookCopies.copyStatus=DaMat`, `isHidden=true`, giữ `copyId` và barcode cũ để bảo toàn lịch sử.
- Cuốn đang mượn vốn **đã không nằm trong availableCopies**; không trừ `availableCopies` lần hai.
- `BookTitles.totalCopies` hiện đếm số bản ghi vật lý (bao gồm bản thanh lý/mất). `InventoryPolicy.audit` đòi `totalCopies == copies.size()`; vì vậy **không giảm totalCopies khi vẫn giữ bản ghi**, nếu không thay đổi nhất quán toàn bộ mô hình thống kê. “Trừ khỏi kho” là trừ số bản **còn sử dụng**, không xóa chứng từ lịch sử.
- Chi tiết mượn kết thúc với trạng thái `LoanDetails.detailStatus=Mat`; không ghi `DaTra` hoặc ngày trả giả. Phiếu mượn chỉ đóng khi không còn khoản mượn thực tế khác.
- Chống xử lý lặp: Admin nhấn Hoàn thành lần nữa không tạo khoản thu trùng, không trừ tồn kho trùng, không gỡ thêm một lượt khóa.

## 3. Bảo đảm nhất quán liên thiết bị (điều kiện bắt buộc để đánh dấu hoàn thành)

Hiện trạng tại commit `ab406797`: `LoanDetails`, `LoanReceipts`, `Reservations`, `Penalties`, `PenaltyPayments` hiện chủ yếu nằm trong SQLite riêng của từng thiết bị. Firestore Rules hiện không cấp quyền truy cập các collection nghiệp vụ này. Vì vậy **không thể chỉ ghi một LostBookReports document rồi coi là đã đồng bộ mượn/trả trên mọi thiết bị**.

Trước khi cho phép nút **Hoàn thành đền bù** vận hành production:
1. Xác lập nguồn dữ liệu giao dịch mượn có thể xác minh chung giữa Reader/Admin (hoặc một backend đáng tin cậy), đối chiếu `userId+loanDetailId+copyId`.
2. Có cơ chế giao dịch/idempotency/đồng bộ không để trạng thái yêu cầu `Completed` trong khi sổ mượn ở thiết bị khác còn `DangMuon`.
3. Có cơ chế khóa **quyền mượn mới** có hiệu lực trên luồng mượn tại quầy, kể cả khi hồ sơ bị khóa thủ công do lý do khác. Mở khóa tự động **chỉ gỡ lý do báo mất của yêu cầu đã hoàn thành**, không xóa các khóa khác.
4. Rules phải cho phép Reader tạo yêu cầu của chính mình và Admin thực hiện chỉ các chuyển trạng thái hợp lệ; kiểm tra Rules bằng Firebase Emulator trước khi triển khai.
5. Thông báo Admin có thể dùng Firestore listener; **FCM không tự xảy ra** nếu chưa triển khai thành phần gửi push hợp lệ.
6. Không triển khai Rules production hoặc migration dữ liệu tự động nếu chưa kiểm thử và đối soát khả năng tương thích với dữ liệu cũ.

## 4. Tiêu chí kiểm thử / nghiệm thu

- Reader có khoản mượn thực tế được báo mất đúng cuốn; người khác không thể báo mất thay.
- Lý do trống/không tích cam kết không tạo yêu cầu.
- Gửi xong hiển thị **Chờ xử lý mất**; độc giả chưa bị khóa mượn trước bước Admin xác nhận.
- Admin nhận yêu cầu và xác nhận xử lý; tất cả đường mượn mới đều bị chặn đối với độc giả này.
- Chỉ sau khi Admin xác nhận bồi thường thực tế, trạng thái thành **Hoàn thành**, bản cũ thành `DaMat` và mượn mất được kết thúc trên mọi thiết bị.
- Hai báo mất cùng người, khóa thủ công, nợ phạt, thao tác lặp và nhiều thiết bị đồng thời không gây mở khóa nhầm hoặc trừ kho/thu tiền hai lần.
- Không cho phát sinh yêu cầu Đặt trước mới, không xóa dữ liệu `DatTruoc` lịch sử.

## 5. Tình trạng thực hiện

- **Đã thống nhất phạm vi và điều kiện nghiệm thu:** tài liệu này.
- **Có thể đổi nhãn UI ngay:** bỏ chữ “Đặt trước” khỏi lối tắt Admin, ẩn cài đặt ưu tiên 48 giờ và hệ số bồi thường cũ khỏi UI, giữ cấu hình legacy trong dữ liệu.
- **Chưa thực hiện trong thay đổi tài liệu này:** form báo mất Reader, màn xử lý Admin, trạng thái và kho khóa liên thiết bị, đối soát hoàn tất/thu tiền và Firebase Rules mới. Không được ghi “đã hoàn thành” cho các phần này.
