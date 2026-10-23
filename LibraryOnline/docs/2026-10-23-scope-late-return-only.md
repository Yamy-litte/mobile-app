# Phạm vi chính thức từ 23/10/2026 — Chỉ phạt trả sách quá hạn

Quyết định mới nhất của chủ dự án: **không triển khai chức năng xử lý mất sách**, giữ **xử phạt trả muộn**. Quyết định này thay thế toàn bộ phần “Báo mất sách — state machine được chấp thuận” trong `2026-10-22-revised-scope-no-preorder-lost-book.md`.

## Chức năng bị loại khỏi phạm vi

- Không tạo giao diện **Reader báo mất**, không thu thập lý do hay cam kết bồi thường mất sách.
- Không triển khai **Admin duyệt báo mất / xác nhận bồi thường / hoàn thành đền bù**, không chuyển `LoanDetails.detailStatus` thành `Mat` hay `BookCopies.copyStatus` thành `DaMat` từ bất kỳ luồng mới nào.
- Không khóa/mở thẻ vì *báo mất*, không dùng `lostBookMultiplier` để tính tiền hoặc giới thiệu chức năng mới.
- Không tạo `LostBookReports`, trạng thái `Pending/Processing/Completed` cho mất sách hoặc sinh khoản phạt `LostBook`.

## Chức năng phạt được giữ lại

- Trả sách trực tiếp **đúng hạn**: ghi trả; **không sinh Penalty**.
- Trả **muộn**: dùng `PenaltyCalculator.lateDays(dueDate, returned)` và `SystemConfigs.lateFeePerDay`. Tạo `Penalties.penaltyType='LateReturn'` với số ngày, số tiền, mã phiếu và trạng thái chưa thu.
- **Admin Thu phạt:** chỉ cho phép thu các khoản `LateReturn` hợp lệ, ghi `PenaltyPayments` trong SQLite transaction; thu lại khoản đã thanh toán phải bị từ chối.
- **Lịch sử độc giả / báo cáo phạt:** chỉ tổng hợp `LateReturn` và khoản thanh toán tương ứng.
- Giữ nguyên cơ chế đang có: thẻ có thể bị khóa khi phát sinh khoản phạt trễ; **không tự mở khóa khi thanh toán**, vì hệ thống chưa tách riêng nguyên nhân khóa thủ công và khóa do nợ phạt.

## Tương thích dữ liệu cũ

**Không xóa hay tự migration dữ liệu hiện có.** Các cột/giá trị `LostBook`, `DaMat`, `lostBookMultiplier` có thể vẫn xuất hiện trong SQLite, Firestore hoặc các bản ghi lưu trữ cũ. Chúng chỉ là *legacy*, không phải quyền tạo mới. UI kho có thể tiếp tục hiển thị nhãn trạng thái của sách đã mất trong lịch sử, nhưng không có đường thao tác **báo mất/đền bù**.

Quyền cập nhật collection cũ hoặc quyền quản trị dữ liệu qua công cụ Firebase bên ngoài ứng dụng không được hiểu là đã được xóa chỉ bởi thay đổi UI này.

## Trạng thái và giới hạn

- Mã giao diện, thu phạt và báo cáo đã được giới hạn cho phạt `LateReturn`; tính phạt trả muộn trực tiếp dựa vào **SQLite trên thiết bị Admin**, chưa phải sổ phạt hợp nhất giữa nhiều thiết bị.
- Đối với **mượn Online**, quy trình `returnOnline()` hiện chỉ xác nhận trả và nhắc đối soát nếu quá hạn; **chưa tự động tạo khoản phạt LateReturn Cloud**. Để có phạt tự động liên thiết bị cho Online cần sổ phạt dùng chung và transaction idempotent, không tự tạo Penalty SQLite giả.
- Chức năng Đặt trước vẫn ngoài phạm vi như đã quyết định trước đây.

## Kiểm thử

```powershell
cd LibraryOnline
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

Kiểm tra: trả đúng hạn không phạt; trả muộn sinh `LateReturn` đúng số ngày/số tiền; Admin thu một lần thành công; thanh toán lần hai bị chặn; nhập ID phạt `LostBook` cũ phải bị từ chối **mà không ghi thêm PenaltyPayments**; báo cáo/đếm công nợ loại bản ghi `LostBook` lịch sử.
