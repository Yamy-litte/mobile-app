# Kế hoạch chức năng đặt sách online cho Reader

## Căn cứ nghiệp vụ
Theo đặc tả Library Online:
- Khi đầu sách còn bản sẵn sàng, Reader chọn **Mượn Online** và chọn số ngày mượn trong giới hạn Admin cấu hình.
- Khi xác nhận mượn online thành công, hệ thống giữ một bản sách vật lý ở trạng thái `ChoLay`, gán người nhận và tạo thời hạn đến quầy 24 giờ (có thể điều chỉnh theo `SystemConfigs.onlineHoldHours`).
- Khi hết sách sẵn sàng, Reader có thể chọn **Đặt trước** để vào hàng đợi theo đầu sách.
- Khi bản sách được trả, người đầu hàng được ưu tiên giữ sách trong `SystemConfigs.reservationHoldHours` (mặc định 48 giờ), sau đó chuyển cho người tiếp theo nếu hết hạn.
- Tổng số sách đang mượn cộng với đơn mượn online chưa nhận không được vượt `maxBooksPerUser`; số ngày yêu cầu không được vượt `maxLoanDays`. Reader bị khóa, thẻ hết hạn, hoặc có sách quá hạn không được tạo đơn mới.

## Phạm vi triển khai trên nhánh `reader`
1. Bổ sung hành động trong màn hình chi tiết sách: chọn ngày mượn, xác nhận mượn online khi còn sách; đăng ký hàng đợi khi hết sách; hiển thị trạng thái và lỗi rõ ràng.
2. Thêm lớp nghiệp vụ/repository cho tạo và hủy đơn, truy vấn đơn của Reader, tính hạn giữ chỗ và vị trí hàng đợi.
3. Đồng bộ trạng thái giao dịch và bản sách với Firestore theo transaction; không coi ghi SQLite cục bộ là đặt online thành công.
4. Cập nhật Firestore Rules để chỉ Reader đang đăng nhập thao tác đơn của chính mình, kiểm tra schema/trạng thái và ngăn Reader tự sửa quyền, hạn giữ hoặc bản sách. Nếu quy tắc cần nhiều kiểm tra liên tài liệu, ưu tiên Cloud Functions đáng tin cậy thay vì tin dữ liệu do client gửi.
5. Thêm kiểm thử quy tắc nghiệp vụ: hết sách, giữ tranh chấp cùng một bản, giới hạn ngày/số lượng, thẻ khóa/hết hạn, sách quá hạn, đăng ký trùng hàng đợi, hủy và hết hạn giữ.
6. Chạy unit tests và kiểm tra Android build nếu môi trường cho phép; ghi rõ những kiểm tra Firebase/thiết bị chưa thể xác minh.

## Ràng buộc kỹ thuật
- Dữ liệu catalogue hiện được đọc từ Firestore và cache vào SQLite; `CirculationRepository` hiện chủ yếu xử lý mượn/trả cục bộ. Không được hiển thị thông báo thành công cho một giao dịch online chỉ vì SQLite ghi thành công.
- Firestore Rules hiện chưa có quyền rõ ràng cho collection `Reservations`; không mở quyền ghi tổng quát để làm UI hoạt động.
- Không deploy Firestore Rules/Cloud Functions hoặc thay đổi dữ liệu production trong quá trình phát triển nhánh.
