# CafeBookingOnlineApp

Ứng dụng Android viết bằng Java, dùng SQLite cho dữ liệu thực đơn/đơn hàng và Firebase Authentication cho tài khoản.

## Chức năng khách hàng

- Đăng ký bằng email/mật khẩu, gửi email xác minh, đăng nhập và đặt lại mật khẩu.
- Xem thực đơn, tìm kiếm món, xem chi tiết và số lượng tồn kho.
- Thêm món vào giỏ, cập nhật số lượng, xóa món và xem tổng tiền.
- Checkout tạo hóa đơn trạng thái `awaiting confirmation`; giá và tồn kho được xác minh lại trong transaction SQLite.
- Lịch sử đơn hàng, xem chi tiết và hủy đơn khi còn chờ xác nhận.
- Đánh giá món ăn theo thang 1–5 sao; khách hàng có thể sửa đánh giá của mình.
- Đánh giá đơn hàng; mỗi đơn có tối đa một đánh giá của chủ đơn và có thể sửa lại.
- Đăng xuất và xóa giỏ hàng khỏi phiên hiện tại.

## Cấu trúc dữ liệu SQLite

- `Users`: email duy nhất và vai trò; mật khẩu không lưu trong SQLite.
- `Categories`: danh mục món.
- `MenuItems`: món ăn, danh mục, giá và tồn kho.
- `Bills`: hóa đơn, chủ đơn, ngày, trạng thái và tổng tiền.
- `BillDetails`: món, số lượng và đơn giá tại thời điểm đặt.
- `Reviews`: đánh giá theo đơn hàng.
- `DishReviews`: đánh giá theo món, mỗi khách hàng chỉ có một đánh giá cho mỗi món.

Cơ sở dữ liệu có dữ liệu mẫu để chạy demo. Khi nâng cấp từ phiên bản DB cũ, migration tạo bảng đánh giá và điều chỉnh tồn kho demo cũ mà không xóa đơn hàng người dùng.

## Build trong Android Studio

1. Mở thư mục `235057450_NguyenHieuNghia_ CafeBookingOnlineApp`.
2. Dùng JDK 21 nếu môi trường Gradle/Android Gradle Plugin yêu cầu.
3. Đồng bộ Gradle và cài Android SDK Platform 36.
4. Kiểm tra `app/google-services.json` thuộc đúng Firebase project.
5. Chạy cấu hình app trên emulator hoặc thiết bị Android.

## Kiểm thử tồn kho

GitHub Actions workflow `.github/workflows/android-build.yml` build APK và chạy kiểm thử tích hợp trên emulator.

Bài kiểm thử tồn kho dùng database riêng của instrumentation test:
- Tồn kho Americano ban đầu của dataset test: 17.
- Checkout 2 phần: tồn kho phải giảm còn 15.
- Hủy đơn mới tạo: tồn kho phải trở lại 17.
- Checkout số lượng vượt tồn kho: phải thất bại và giữ nguyên tồn kho.

## Luồng kiểm thử thủ công

1. Đăng ký tài khoản mới, mở email xác minh rồi đăng nhập.
2. Tìm một món và thêm vào giỏ; xác nhận tồn kho chưa giảm chỉ vì thêm giỏ.
3. Checkout; kiểm tra hóa đơn mới ở trạng thái `awaiting confirmation` và tồn kho giảm đúng số lượng.
4. Mở lịch sử, xem chi tiết đơn, hủy đơn đang chờ xác nhận; tồn kho phải được hoàn lại.
5. Thử hủy lại hoặc hủy đơn đã xác nhận; thao tác phải bị từ chối.
6. Gửi đánh giá món và đánh giá đơn; mở lại màn hình để kiểm tra dữ liệu đã lưu.
7. Đăng xuất, đăng nhập lại và xác nhận lịch sử chỉ hiện đơn thuộc tài khoản đó.

## Lưu ý

- Giỏ hàng hiện lưu trong bộ nhớ của tiến trình; nếu hệ điều hành đóng hẳn app, giỏ có thể bị xóa.
- Tồn kho và hóa đơn được lưu bền vững trong SQLite trên thiết bị.
- Firebase email verification/reset-password cần được cấu hình đúng trong Firebase Console và thiết bị cần kết nối Internet.
- Trạng thái `confirmed`/hoàn tất trong demo là dữ liệu mẫu; màn hình quản trị/nhân viên không thuộc phạm vi hiện tại.
