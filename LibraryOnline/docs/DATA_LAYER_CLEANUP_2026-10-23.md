# LibraryOnline — kiểm kê và dọn dẹp data layer (23/10/2026)

## Giới hạn thay đổi

Thay đổi được thực hiện trên nhánh `chore/data-layer-cleanup-2026-10-23`.
Đây là dọn **mã Java không còn caller runtime** và loại bỏ **luồng cấp
phát đặt trước cũ**. KHÔNG xóa hàng dữ liệu thực, KHÔNG xóa collection Firebase,
KHÔNG xóa bảng SQLite, KHÔNG thay đổi database version hoặc tự migration.
SQLite vẫn là `library_online.db` phiên bản **7**.

## Nguồn dữ liệu thực

- Firebase Authentication: xác thực tài khoản.
- Firestore: Admins, Users, RegistrationRequests, RegistrationEmailIndex,
  RegistrationPhoneIndex, PasswordResetRequests, Categories, BookTitles,
  BookCopies, SystemConfigs, OnlineLoanRequests, OnlineLoanReceipts,
  OnlineLoanNotifications.
- SQLite: Users, Admins, RegistrationRequests, Categories, BookTitles,
  BookCopies, LoanReceipts, LoanDetails, Reservations, Penalties,
  PenaltyPayments, SystemConfigs, Notifications.

Mượn/trả Online sử dụng **Firestore OnlineLoanReceipts**; giao dịch trực tiếp
và phạt trả muộn vẫn dùng **SQLite trên từng thiết bị**. Hai nguồn dữ liệu
này không phải là bản sao đồng bộ hai chiều của toàn bộ giao dịch.

## Mã đã loại bỏ

Chín repository không có caller runtime:
`AdminRepository`, `UserRepository`, `BookRepository`,
`BookCopyRepository`, `RegistrationRequestRepository`,
`LoanReceiptRepository`, `ReservationRepository`,
`PenaltyRepository`, `PenaltyPaymentRepository`.

Năm cặp model/DAO chỉ phục vụ repository cũ và fixture test:
`RegistrationRequest`, `Reservation`, `LoanReceipt`, `Penalty`,
`PenaltyPayment` và DAO có tên tương ứng.

Các DAO/repository đang dùng trong đăng nhập, đồng bộ kho, mượn/trả
trực tiếp, đọc chi tiết mượn, cấu hình và thông báo vẫn giữ nguyên.
Bảng dữ liệu của các lớp bị loại bỏ **vẫn còn trong DatabaseHelper**
vì nghiệp vụ thực tế còn dùng SQL trực tiếp hoặc cần giữ bản ghi lịch sử.

## Thay đổi nghiệp vụ đặt trước

- Mượn/trả trực tiếp không gọi hàm hết hạn hàng đợi 48 giờ.
- Trả sách không tự cấp lại cho `DatTruoc` và không tạo
  thông báo `ReservationReady`; sách trở về `SanSang` hoặc
  `DangSuaChua` nếu bị ẩn.
- `Reservations` chỉ được đọc để đối chiếu các khoản giữ
  **Online cũ**; không có thêm bản ghi/chuyển trạng thái đặt trước.
- `DatTruoc` cũ không cản trở lượt mượn mới của cuốn `SanSang`.
- Cuốn `ChoLay` phải được giao qua quy trình Firestore Online;
  `DaDatTruoc` cũ cần Admin đối soát tồn kho, không tự chuyển trạng thái.
- Không thay đổi hành vi giao/nhận Online trên Firestore và
  không kích hoạt tính năng báo mất sách.

## Bước kiểm kê dữ liệu trước migration (chưa thực hiện)

Các truy vấn sau **chỉ đọc**, chạy trên bản sao lưu `library_online.db`
từ mỗi thiết bị đang vận hành:

```sql
SELECT COUNT(*) AS local_registration_requests FROM RegistrationRequests;
SELECT COUNT(*) AS local_reservations FROM Reservations;
SELECT reservationType, reservationStatus, COUNT(*) AS total
FROM Reservations GROUP BY reservationType, reservationStatus;
SELECT COUNT(*) AS active_online_local FROM Reservations
WHERE reservationType='Online'
  AND reservationStatus NOT IN ('Cancelled','Expired','Completed','Received');
SELECT copyStatus, COUNT(*) FROM BookCopies
WHERE copyStatus IN ('ChoLay','DaDatTruoc') GROUP BY copyStatus;
PRAGMA foreign_key_check;
```

Không thể xác nhận số lượng thực của những bảng này bằng cách đọc code.
Bởi vậy **không DROP TABLE / DELETE** hoặc tăng version để chạy migration
phá hủy dữ liệu trong đợt này. Khi có kết quả kiểm kê từ tất cả thiết bị,
cần sao lưu, đối soát khóa ngoại/copy đang giữ và thử nâng cấp SQLite
trên dữ liệu cũ trước khi đề xuất migration riêng.

## Kiểm thử hồi quy cần chạy

- `LoanValidatorTest`: chặn Checkout SQLite đối với `ChoLay` và `DaDatTruoc`.
- `CirculationNoPreorderTest`: trả sách không cấp cho hàng chờ;
  giao trực tiếp không bị `DatTruoc` cũ cản; `ChoLay` không thể giao
  từ SQLite.
- `RemainingDaoTest`: bảng lịch sử có thể đọc/ghi trong thử nghiệm.
- Biên dịch APK, chạy unit/instrumentation tests và kiểm tra mượn/trả
  Online, thu phạt, đồng bộ kho, thống kê trên Android.

```powershell
cd LibraryOnline
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
.\gradlew.bat :app:connectedDebugAndroidTest
```

**Kiểm thử Gradle/Android thực tế chưa chạy trong phiên chỉnh sửa
GitHub này. Không merge hoặc xóa dữ liệu production dựa vào kiểm kê tĩnh.**
