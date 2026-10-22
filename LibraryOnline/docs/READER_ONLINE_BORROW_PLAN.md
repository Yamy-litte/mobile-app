# Kế hoạch chức năng mượn sách online cho Reader

## Phạm vi nghiệp vụ
- Chỉ cho phép Reader tạo yêu cầu **Mượn sách online** khi đầu sách còn bản sẵn sàng.
- Nếu không còn bản sẵn sàng, giao diện chỉ hiển thị **Hết sách** và không cho gửi yêu cầu. Không có chức năng đặt trước hoặc hàng đợi.
- Khi tạo yêu cầu mượn online thành công, hệ thống giữ một bản sách vật lý ở trạng thái `ChoLay` cho độc giả đến nhận trong thời hạn cấu hình `SystemConfigs.onlineHoldHours` (mặc định 24 giờ).
- Tổng số sách đang mượn cộng với đơn mượn online chưa nhận không được vượt `maxBooksPerUser`; số ngày yêu cầu không được vượt `maxLoanDays`.
- Reader bị khóa, thẻ hết hạn, hoặc có sách quá hạn không được tạo đơn mới.

## Phạm vi triển khai trên nhánh `reader`
1. Màn hình chi tiết sách có nút **Mượn sách online** khi còn bản sẵn sàng; khi hết sách, hiển thị **Hết sách** và vô hiệu hóa nút.
2. Tạo lớp nghiệp vụ/repository cho tạo yêu cầu, kiểm tra điều kiện mượn, xem trạng thái và hủy yêu cầu online.
3. Đồng bộ trạng thái yêu cầu và bản sách với Firestore theo transaction; không coi ghi SQLite cục bộ là mượn online thành công.
4. Cập nhật Firestore Rules hoặc backend đáng tin cậy để chỉ Reader đang đăng nhập thao tác yêu cầu của chính mình, kiểm tra trạng thái thẻ, giới hạn và tồn kho; không tin các trường quyền/hạn giữ do client tự gửi.
5. Kiểm thử: hết sách, hai Reader cùng tranh một bản, giới hạn ngày/số lượng, thẻ khóa/hết hạn, sách quá hạn, tạo trùng yêu cầu, hủy và hết hạn giữ.
6. Chạy unit tests và kiểm tra Android build nếu môi trường cho phép; ghi rõ các kiểm tra Firebase/thiết bị chưa thể xác minh.

## Ràng buộc kỹ thuật
- Catalogue được đọc từ Firestore và cache vào SQLite; `CirculationRepository` hiện chủ yếu xử lý mượn/trả cục bộ. Không hiển thị thành công cho giao dịch online chỉ vì SQLite ghi thành công.
- Firestore Rules hiện chưa có quyền rõ ràng cho giao dịch mượn online. Không mở quyền ghi tổng quát để làm UI hoạt động.
- Không triển khai đặt trước hoặc hàng đợi khi sách hết.
- Không deploy Firestore Rules/Cloud Functions hoặc thay đổi dữ liệu production trong quá trình phát triển nhánh.
