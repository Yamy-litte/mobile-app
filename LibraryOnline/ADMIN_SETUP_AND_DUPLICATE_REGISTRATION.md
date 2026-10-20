# Cấp quyền Admin cho LibraryOnline

Email: hieunghiaya@gmail.com
UID Firebase Authentication: rqZF26jMQLTjbzbHasW6A2UMbkP2

## Tạo hồ sơ quản trị viên

Trong Firebase Console, chọn dự án `libraryonline-dd3a0` → Build → Firestore Database → Start collection (hoặc mở collection `Admins` nếu đã tồn tại).

Tạo document ID chính xác bằng UID ở trên trong collection `Admins`, với các trường:
- `email` (string): `hieunghiaya@gmail.com`
- `fullName` (string): `Quản trị viên`
- `role` (string): `Admin`
- `adminStatus` (string): `Active`
- `createdAt` (timestamp): thời điểm tạo bằng Console

Đảm bảo email này đã tồn tại trong Authentication và UID hiển thị ở Authentication đúng chính xác với UID bên trên. Không lưu mật khẩu vào Firestore. Sau khi tạo hồ sơ, đăng nhập app bằng mật khẩu của tài khoản Firebase đó.

## Chặn đăng ký trùng Gmail và số điện thoại

Bản Android tạo đồng thời một RegistrationRequests document, một RegistrationEmailIndex document có ID bằng email đã chuẩn hóa, và một RegistrationPhoneIndex document có ID bằng số điện thoại đã chuẩn hóa. Firestore Rules chỉ cho phép tạo index nếu chưa tồn tại và yêu cầu đăng ký phải tham chiếu đúng hai index trong cùng batch. Vì vậy hai lần gửi đồng thời cùng email hoặc số điện thoại không thể cùng thành công.

Các index không tự giải phóng khi hồ sơ bị từ chối. Đây là lựa chọn an toàn mặc định để tránh một hồ sơ bị từ chối có thể gửi lại liên tục; nếu muốn cho phép đăng ký lại sau khi từ chối, cần bổ sung quy trình quản trị giải phóng index có kiểm soát. Các yêu cầu cũ trước khi triển khai index chưa được tự động nhập vào hai collection index.

## Cảnh báo triển khai Rules

Tệp `firestore.rules` hiện khóa mọi collection không được khai báo rõ. Không deploy nguyên tệp lên cơ sở dữ liệu đang dùng app nếu chưa bổ sung rules cho các collection còn lại (sách, mượn trả, phạt, thông báo...). Nếu đã deploy rules cũ, cần deploy bản mới sau khi kiểm tra đầy đủ các collection và quyền truy cập.
